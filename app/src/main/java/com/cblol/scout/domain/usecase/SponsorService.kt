package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.SponsorContract
import com.cblol.scout.data.SponsorOffer
import com.cblol.scout.data.SponsorTier
import com.cblol.scout.util.SponsorCatalog
import java.time.LocalDate
import kotlin.random.Random

/**
 * Sistema de patrocínios.
 *
 * Gerencia o ciclo completo: gerar ofertas baseado em desempenho do técnico,
 * aceitar contratos (respeitando limite), pagar semanalmente, processar bônus
 * por vitória / penalidade por derrota, expirar contratos.
 *
 * **Como funciona:**
 *  - [generateOffersIfDue] sorteia novas ofertas a cada [OFFERS_INTERVAL_DAYS]
 *    dias, baseado na reputação atual do técnico e vitórias no split.
 *  - [acceptOffer] move uma oferta para os contratos ativos, descontando da
 *    lista de ofertas e impondo o limite de [MAX_ACTIVE_SPONSORS].
 *  - [cancelContract] encerra antecipadamente um contrato ativo (paga uma
 *    [CANCELLATION_FEE_WEEKS]x o valor semanal como multa).
 *  - [paySponsorsWeekly] chamado pelo [com.cblol.scout.game.GameEngine] todo
 *    domingo: paga cada contrato ativo + processa expiração.
 *  - [applyMatchPerformance] chamado após cada mapa: aplica bônus/penalidades
 *    dos contratos relevantes.
 *
 * **SOLID:**
 *  - **SRP**: gera/aceita/paga/cancela são funções separadas.
 *  - **OCP**: novos patrocínios são adicionados em [SponsorCatalog] sem mudar
 *    o service.
 *  - **DIP**: depende só de [GameState] e [SponsorCatalog]. Sem Android,
 *    100% testável em JVM.
 */
object SponsorService {

    // ── Constantes ───────────────────────────────────────────────────────

    /** Máximo de patrocínios ativos simultaneamente (limite "espaço de banner"). */
    const val MAX_ACTIVE_SPONSORS = 4

    /** Dias entre rodadas de novas ofertas. */
    const val OFFERS_INTERVAL_DAYS = 14

    /** Quantas ofertas geramos por rodada (alguns podem ser duplicados). */
    const val OFFERS_PER_ROUND = 5

    /** Quantos dias após a oferta o patrocinador ainda mantém a proposta aberta. */
    const val OFFER_VALIDITY_DAYS = 21

    /** Multa em semanas pagas ao cancelar (4 = paga 1 mês adiantado). */
    const val CANCELLATION_FEE_WEEKS = 4

    // ── API pública: ofertas ────────────────────────────────────────────

    /**
     * Gera novas ofertas se passou o intervalo desde a última rodada (ou se
     * nunca rodou). Limpa ofertas expiradas. Retorna a lista atual.
     *
     * Filtra o catálogo por requisitos (reputação do técnico, vitórias no split)
     * antes de sortear — só aparecem ofertas que o jogador realmente pode aceitar.
     */
    fun generateOffersIfDue(state: GameState): List<SponsorOffer> {
        // Ensure lists are not null (backward compatibility with old saves)
        if (state.availableSponsorOffers == null) state.availableSponsorOffers = mutableListOf()
        if (state.activeSponsors == null) state.activeSponsors = mutableListOf()

        val today = parseDate(state.currentDate) ?: return state.availableSponsorOffers!!

        // Remove ofertas expiradas
        state.availableSponsorOffers!!.removeAll { offer ->
            val expires = parseDate(offer.expiresOn)
            expires != null && today.isAfter(expires)
        }

        // Se ainda não passou o intervalo, devolve a lista atual
        val last = state.lastSponsorOffersDate?.let { parseDate(it) }
        val daysSinceLast = if (last != null) {
            java.time.temporal.ChronoUnit.DAYS.between(last, today).toInt()
        } else {
            Int.MAX_VALUE
        }
        if (daysSinceLast < OFFERS_INTERVAL_DAYS) {
            return state.availableSponsorOffers!!
        }

        // Sorteia novas ofertas filtradas pelos requisitos do estado atual
        val reputation = state.coachProfile.reputation
        val winsThisSplit = state.coachProfile.mapsWon

        val eligible = SponsorCatalog.ALL.filter { sponsor ->
            reputation >= sponsor.minReputation &&
            winsThisSplit >= sponsor.minWinsThisSplit &&
            // Não oferece o mesmo patrocinador que já está ativo
            state.activeSponsors!!.none { it.sponsor.id == sponsor.id } &&
            // Nem o que já está como oferta pendente
            state.availableSponsorOffers!!.none { it.sponsor.id == sponsor.id }
        }

        if (eligible.isEmpty()) {
            // Nada novo para oferecer — atualiza o timestamp pra não tentar de novo
            // amanhã, espera o próximo ciclo
            state.lastSponsorOffersDate = state.currentDate
            return state.availableSponsorOffers!!
        }

        val expiresOn = today.plusDays(OFFER_VALIDITY_DAYS.toLong()).toString()
        val newOffers = eligible.shuffled(Random.Default)
            .take(OFFERS_PER_ROUND)
            .map { SponsorOffer(it, state.currentDate, expiresOn) }

        state.availableSponsorOffers!!.addAll(newOffers)
        state.lastSponsorOffersDate = state.currentDate
        return state.availableSponsorOffers!!
    }

    /**
     * Aceita uma oferta, criando um [SponsorContract] ativo.
     * Retorna [AcceptResult] indicando sucesso/erro.
     */
    fun acceptOffer(state: GameState, sponsorId: String): AcceptResult {
        if (state.activeSponsors == null) state.activeSponsors = mutableListOf()
        if (state.availableSponsorOffers == null) state.availableSponsorOffers = mutableListOf()

        if (state.activeSponsors!!.size >= MAX_ACTIVE_SPONSORS) {
            return AcceptResult.LIMIT_REACHED
        }
        val offerIdx = state.availableSponsorOffers!!.indexOfFirst { it.sponsor.id == sponsorId }
        if (offerIdx < 0) return AcceptResult.OFFER_NOT_FOUND

        val offer = state.availableSponsorOffers!![offerIdx]
        val today = parseDate(state.currentDate) ?: return AcceptResult.INVALID_DATE
        val endDate = today.plusWeeks(offer.sponsor.durationWeeks.toLong()).toString()

        val contract = SponsorContract(
            sponsor   = offer.sponsor,
            startDate = state.currentDate,
            endDate   = endDate
        )
        state.activeSponsors!!.add(contract)
        state.availableSponsorOffers!!.removeAt(offerIdx)
        return AcceptResult.OK
    }

    /** Recusa uma oferta (apenas remove da lista). */
    fun rejectOffer(state: GameState, sponsorId: String) {
        if (state.availableSponsorOffers == null) return
        state.availableSponsorOffers!!.removeAll { it.sponsor.id == sponsorId }
    }

    /**
     * Cancela um contrato ativo antes do término natural. Cobra multa equivalente
     * a [CANCELLATION_FEE_WEEKS] semanas do valor — descontada do orçamento.
     * Retorna o valor da multa para a UI mostrar.
     */
    fun cancelContract(state: GameState, sponsorId: String): Long {
        if (state.activeSponsors == null) return 0L
        val idx = state.activeSponsors!!.indexOfFirst { it.sponsor.id == sponsorId }
        if (idx < 0) return 0L
        val contract = state.activeSponsors!![idx]
        val penalty = contract.sponsor.weeklyAmount * CANCELLATION_FEE_WEEKS
        state.budget -= penalty
        state.activeSponsors!!.removeAt(idx)
        return penalty
    }

    // ── Pagamentos / ciclo de vida ──────────────────────────────────────

    /**
     * Resultado do pagamento semanal dos patrocínios.
     */
    data class WeeklyPaymentResult(
        val totalPaid: Long,
        val expiredContracts: List<SponsorContract>
    )

    /**
     * Paga todos os contratos ativos e remove os que expiraram.
     * Chamado pelo [com.cblol.scout.game.GameEngine] todo domingo.
     */
    fun paySponsorsWeekly(state: GameState): WeeklyPaymentResult {
        if (state.activeSponsors == null) state.activeSponsors = mutableListOf()

        val today = parseDate(state.currentDate)
            ?: return WeeklyPaymentResult(0L, emptyList())

        var totalPaid = 0L
        val expired = mutableListOf<SponsorContract>()

        // Itera sobre uma cópia para poder remover durante o loop
        state.activeSponsors!!.toList().forEach { contract ->
            val endDate = parseDate(contract.endDate)
            if (endDate != null && today.isAfter(endDate)) {
                expired.add(contract)
                state.activeSponsors!!.remove(contract)
                return@forEach
            }

            val amount = contract.sponsor.weeklyAmount
            state.budget += amount
            contract.weeksPaid += 1
            contract.totalReceived += amount
            totalPaid += amount
        }

        return WeeklyPaymentResult(totalPaid, expired)
    }

    /**
     * Aplica bônus por vitória OU penalidade por derrota nos contratos ativos.
     * Chamado pelo [com.cblol.scout.ui.MatchResultActivity] após cada mapa.
     *
     * Retorna o saldo líquido (positivo = ganhou, negativo = pagou multa).
     */
    fun applyMatchPerformance(state: GameState, won: Boolean): Long {
        if (state.activeSponsors == null) return 0L

        var delta = 0L
        state.activeSponsors!!.forEach { contract ->
            val s = contract.sponsor
            if (won && s.bonusPerWin > 0) {
                delta += s.bonusPerWin
                contract.totalReceived += s.bonusPerWin
            } else if (!won && s.penaltyPerLoss > 0) {
                delta -= s.penaltyPerLoss
            }
        }
        state.budget += delta
        return delta
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Total semanal somando o patrocínio "base" (sponsorshipPerWeek do tier do time)
     * mais todos os contratos ativos. Útil para mostrar no Hub.
     */
    fun totalWeeklyIncomeFromSponsors(state: GameState): Long =
        (state.activeSponsors ?: emptyList()).sumOf { it.sponsor.weeklyAmount }

    private fun parseDate(s: String): LocalDate? =
        runCatching { LocalDate.parse(s) }.getOrNull()

    // ── Resultado de aceitar oferta ─────────────────────────────────────

    enum class AcceptResult {
        OK,
        LIMIT_REACHED,
        OFFER_NOT_FOUND,
        INVALID_DATE
    }
}
