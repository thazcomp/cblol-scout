package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.IncomingTransferOffer
import com.cblol.scout.data.OfferStatus
import com.cblol.scout.data.Player
import com.cblol.scout.data.SnapshotData
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Serviço de **ofertas de compra recebidas** — outros times da liga oferecem
 * dinheiro por jogadores do elenco do gerente durante as janelas de
 * transferência.
 *
 * Espelha o comportamento de um Football Manager: rivais observam seu elenco e,
 * quando o mercado está aberto, mandam propostas. Jogadores que **pediram para
 * sair** ([MoraleService.hasRequestedTransfer]) atraem propostas com mais
 * frequência e por valores mais agressivos.
 *
 * **HISTÓRICO PRESERVADO:** propostas NUNCA saem da lista. Cada proposta
 * percorre [OfferStatus.PENDING] → uma das resoluções ([OfferStatus.ACCEPTED],
 * [OfferStatus.REJECTED] ou [OfferStatus.EXPIRED]) e fica para sempre. Isso
 * permite ao gerente revisar decisões e prazos perdidos. Filtros/contagens
 * usam [IncomingTransferOffer.isPending] para distinguir propostas ativas das
 * arquivadas.
 *
 * **Fluxo:**
 *  1. [generateOffersIfDue] roda nos ticks diários (só com mercado aberto).
 *     A cada [OFFER_INTERVAL_DAYS] dias sorteia 0-N novas ofertas.
 *  2. As ofertas ficam em [GameState.incomingOffers] com status PENDING.
 *  3. [markAccepted] / [markRejected] aplicam a resolução do gerente.
 *  4. [expireOffers] muda PENDINGs vencidas para EXPIRED.
 *  5. Nada disso REMOVE entradas — só muta o [IncomingTransferOffer.status].
 *
 * **SOLID:**
 *  - **SRP**: gera e marca o ciclo de vida; o efeito de venda em si vive em
 *    [com.cblol.scout.game.TransferMarket.acceptIncomingOffer].
 *  - **OCP**: novos gatilhos de oferta entram em [shouldTargetPlayer]/[offerAmountFor];
 *    novos estados (ex: COUNTER_OFFERED) entram no enum [OfferStatus].
 *  - **DIP**: JVM-puro; recebe [GameState] e [SnapshotData], não toca Android.
 */
object IncomingOfferService {

    /** Intervalo (dias) entre rodadas de geração de ofertas, com janela aberta. */
    const val OFFER_INTERVAL_DAYS = 3

    /** Validade (dias) de cada oferta antes de expirar sozinha. */
    private const val OFFER_VALIDITY_DAYS = 5L

    /**
     * Máximo de ofertas **pendentes** simultâneas (evita poluir a UI). Ofertas
     * resolvidas (aceitas/recusadas/expiradas) NÃO contam para este limite —
     * elas ficam apenas no histórico.
     */
    private const val MAX_ACTIVE_OFFERS = 5

    /** Máximo de novas ofertas geradas por rodada. */
    private const val MAX_OFFERS_PER_ROUND = 2

    // Probabilidades de um jogador específico virar alvo numa rodada.
    private const val PROB_TARGET_REQUESTED = 0.70   // pediu pra sair: muito visado
    private const val PROB_TARGET_STAR      = 0.25   // craque (overall alto)
    private const val PROB_TARGET_REGULAR   = 0.06   // qualquer outro

    private const val STAR_OVERALL = 80

    // Faixa do multiplicador sobre o valor de mercado (ofertas variam).
    private const val MIN_OFFER_MULT = 0.85
    private const val MAX_OFFER_MULT = 1.45

    /** Bônus no multiplicador quando o jogador pediu pra sair (rivais farejam). */
    private const val REQUESTED_BONUS_MULT = 0.15

    // ── Geração ─────────────────────────────────────────────────────────

    /**
     * Resultado de uma rodada de geração, com os jogadores que receberam
     * proposta (para o motor logar / notificar).
     */
    data class GenerationResult(val newOffers: List<IncomingTransferOffer>)

    /**
     * Gera novas ofertas se (a) o mercado está aberto e (b) já passou o
     * intervalo desde a última geração. Idempotente dentro do mesmo intervalo.
     *
     * Só ofertas PENDING contam para o teto de [MAX_ACTIVE_OFFERS] e para
     * "jogador já tem oferta" — propostas arquivadas (resolvidas) não bloqueiam
     * novas tentativas em cima do mesmo jogador.
     *
     * @param rivalTeams times que podem oferecer propostas. Em carreira de 1ª
     *   divisão, vem do snapshot oficial; em 2ª divisão, vem dos times
     *   procedurais do CD (sem isso, times da elite ofereceriam valores
     *   desproporcionais ao orçamento da 2ª div).
     * @param marketPriceOf função que calcula o valor de mercado de um jogador
     *   (injetada para reaproveitar a regra do TransferMarket sem acoplar a
     *   camada de jogo ao domínio).
     */
    fun generateOffersIfDue(
        state: GameState,
        snapshot: SnapshotData,
        roster: List<Player>,
        marketPriceOf: (Player) -> Long,
        rivalTeams: List<com.cblol.scout.data.Team> = snapshot.times
    ): GenerationResult {
        if (!TransferWindowService.isMarketOpen(state)) return GenerationResult(emptyList())

        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return GenerationResult(emptyList())

        // Respeita o intervalo entre rodadas.
        val last = state.lastIncomingOffersDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        if (last != null && ChronoUnit.DAYS.between(last, today) < OFFER_INTERVAL_DAYS) {
            return GenerationResult(emptyList())
        }
        state.lastIncomingOffersDate = state.currentDate

        val offers = state.incomingOffers ?: mutableListOf<IncomingTransferOffer>().also {
            state.incomingOffers = it
        }

        // Conta só PENDINGs para o teto — propostas arquivadas não ocupam slot.
        val pendingCount = offers.count { it.isPending }
        val freeSlots = (MAX_ACTIVE_OFFERS - pendingCount).coerceAtLeast(0)
        if (freeSlots == 0) return GenerationResult(emptyList())

        val otherTeams = rivalTeams.filter { it.id != state.managerTeamId }
        if (otherTeams.isEmpty()) return GenerationResult(emptyList())

        // Evita disparar nova oferta para um jogador que JÁ tem proposta pendente.
        // Jogadores com propostas só arquivadas voltam a ser alvos elegíveis.
        val alreadyPending = offers.filter { it.isPending }.map { it.playerId }.toSet()
        val candidates = roster
            .filter { it.id !in alreadyPending }
            .filter { shouldTargetPlayer(state, it) }
            .shuffled()
            .take(minOf(freeSlots, MAX_OFFERS_PER_ROUND))

        val created = candidates.map { player ->
            val requested = MoraleService.hasRequestedTransfer(state, player.id)
            val team = otherTeams.random()
            val baseAmount = offerAmountFor(player, requested, marketPriceOf)
            val amount = applyCoachOfferBonus(state, baseAmount)
            IncomingTransferOffer(
                id = "offer_${player.id}_${state.currentDate}_${Random.nextInt(1000, 9999)}",
                playerId = player.id,
                playerName = player.nome_jogo,
                playerRole = player.role,
                fromTeamId = team.id,
                fromTeamName = team.nome,
                amountBrl = amount,
                offeredOn = state.currentDate,
                expiresOn = today.plusDays(OFFER_VALIDITY_DAYS).toString(),
                motivatedByRequest = requested,
                status = OfferStatus.PENDING
            )
        }

        offers.addAll(created)
        return GenerationResult(created)
    }

    /** Decide probabilisticamente se um jogador vira alvo de oferta nesta rodada. */
    private fun shouldTargetPlayer(state: GameState, player: Player): Boolean {
        val prob = when {
            MoraleService.hasRequestedTransfer(state, player.id) -> PROB_TARGET_REQUESTED
            player.overallRating() >= STAR_OVERALL               -> PROB_TARGET_STAR
            else                                                  -> PROB_TARGET_REGULAR
        }
        return Random.nextDouble() < prob
    }

    /** Calcula o valor oferecido: mercado × multiplicador aleatório (+bônus). */
    private fun offerAmountFor(
        player: Player,
        requested: Boolean,
        marketPriceOf: (Player) -> Long
    ): Long {
        val base = marketPriceOf(player)
        val bonus = if (requested) REQUESTED_BONUS_MULT else 0.0
        val mult = Random.nextDouble(MIN_OFFER_MULT, MAX_OFFER_MULT) + bonus
        return (base * mult).toLong().coerceAtLeast(1)
    }

    /**
     * Aplica o bônus do técnico (badge "Negociador" do lv 20) ao valor da
     * oferta antes de retorná-la ao caller. Mantido separado para que a
     * fórmula básica de [offerAmountFor] continue pura/testável sem precisar
     * de [GameState].
     */
    private fun applyCoachOfferBonus(state: GameState, amount: Long): Long {
        val bonusPercent = state.coachBonuses?.incomingOfferBonusPercent ?: 0
        if (bonusPercent <= 0) return amount
        return (amount * (100 + bonusPercent) / 100).coerceAtLeast(amount)
    }

    // ── Expiração ───────────────────────────────────────────────────────

    /**
     * Marca como [OfferStatus.EXPIRED] as ofertas PENDING que expiraram por
     * data OU porque o mercado fechou. NÃO remove ninguém da lista — apenas
     * muda o status. Idempotente: já-expiradas são ignoradas.
     *
     * @return lista das ofertas que foram expiradas nesta passagem (para o
     *   motor logar/avisar, se desejar).
     */
    fun expireOffers(state: GameState): List<IncomingTransferOffer> {
        val offers = state.incomingOffers ?: return emptyList()
        if (offers.isEmpty()) return emptyList()

        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return emptyList()
        val marketOpen = TransferWindowService.isMarketOpen(state)

        val newlyExpired = offers.filter { offer ->
            if (!offer.isPending) return@filter false
            if (!marketOpen) return@filter true
            val exp = runCatching { LocalDate.parse(offer.expiresOn) }.getOrNull()
            exp == null || today.isAfter(exp)
        }
        newlyExpired.forEach { offer ->
            offer.status = OfferStatus.EXPIRED
            offer.resolvedOn = state.currentDate
        }
        return newlyExpired
    }

    // ── Resposta do gerente ─────────────────────────────────────────────

    /** Oferta da lista pelo id (qualquer status). */
    fun offerById(state: GameState, offerId: String): IncomingTransferOffer? =
        state.incomingOffers?.find { it.id == offerId }

    /**
     * TODAS as ofertas da lista, ordenadas: pendentes primeiro (mais antigas no
     * topo dentro de pendentes), depois resolvidas mais recentes primeiro.
     *
     * Mantém o nome [activeOffers] por compatibilidade com chamadores antigos —
     * apesar do nome sugerir "ativas", a UI quer ver TUDO. Quem precisa apenas
     * das pendentes usa [pendingOffers].
     */
    fun activeOffers(state: GameState): List<IncomingTransferOffer> {
        val all = state.incomingOffers ?: return emptyList()
        return all.sortedWith(
            compareByDescending<IncomingTransferOffer> { it.isPending }
                .thenByDescending { it.resolvedOn ?: it.offeredOn }
                .thenByDescending { it.amountBrl }
        )
    }

    /** Filtra só as propostas que ainda aguardam resposta do gerente. */
    fun pendingOffers(state: GameState): List<IncomingTransferOffer> =
        (state.incomingOffers ?: emptyList()).filter { it.isPending }

    /**
     * Marca uma oferta como aceita. Chamado pelo
     * [com.cblol.scout.game.TransferMarket.acceptIncomingOffer] depois de
     * processar a venda. NÃO remove da lista.
     */
    fun markAccepted(state: GameState, offerId: String) {
        val offer = offerById(state, offerId) ?: return
        if (!offer.isPending) return
        offer.status = OfferStatus.ACCEPTED
        offer.resolvedOn = state.currentDate
    }

    /**
     * Marca a recusa de uma oferta: aplica o efeito moral (mais pesado se o
     * jogador havia pedido para sair) e troca o status para REJECTED. NÃO
     * remove da lista.
     */
    fun rejectOffer(state: GameState, offer: IncomingTransferOffer) {
        if (!offer.isPending) return
        MoraleService.recordTransferOfferRejected(
            state, offer.playerId, hadRequested = offer.motivatedByRequest
        )
        offer.status = OfferStatus.REJECTED
        offer.resolvedOn = state.currentDate
    }
}
