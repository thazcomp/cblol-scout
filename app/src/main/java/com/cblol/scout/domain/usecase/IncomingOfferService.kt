package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.IncomingTransferOffer
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
 * **Fluxo:**
 *  1. [generateOffersIfDue] roda nos ticks diários (só com mercado aberto).
 *     A cada [OFFER_INTERVAL_DAYS] dias sorteia 0-N novas ofertas.
 *  2. As ofertas ficam em [GameState.incomingOffers] até o gerente responder.
 *  3. [acceptOffer] vende o jogador para o time proponente (recebe o valor);
 *     [rejectOffer] descarta e, se o jogador queria sair, afeta a moral.
 *  4. [expireOffers] remove ofertas vencidas (por data ou janela fechada).
 *
 * **Por que separado do [com.cblol.scout.game.TransferMarket]?** O TransferMarket
 * cuida de transações iniciadas pelo GERENTE (comprar/vender). Aqui o fluxo é
 * inverso — a iniciativa é da IA. Manter separado respeita SRP e deixa a regra
 * de geração/expiração isolada e testável.
 *
 * **SOLID:**
 *  - **SRP**: só gera/avalia/expira ofertas recebidas. A venda em si reaproveita
 *    a mesma mutação de override que o TransferMarket faz (via [applyAcceptedOffer]).
 *  - **OCP**: novos gatilhos de oferta (ex: jogador em destaque) entram em
 *    [shouldTargetPlayer]/[offerAmountFor] sem mexer no resto.
 *  - **DIP**: JVM-puro; recebe [GameState] e [SnapshotData], não toca Android.
 */
object IncomingOfferService {

    /** Intervalo (dias) entre rodadas de geração de ofertas, com janela aberta. */
    const val OFFER_INTERVAL_DAYS = 3

    /** Validade (dias) de cada oferta antes de expirar sozinha. */
    private const val OFFER_VALIDITY_DAYS = 5L

    /** Máximo de ofertas ativas simultâneas (evita poluir a UI). */
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
     * @param rivalTeams times que podem oferecer propostas. Em carreira de 1ª
     *   divisão, vem do snapshot oficial; em 2ª divisão, vem dos times
     *   procedurais do CD (sem isso, times da elite ofereceriam valores
     *   desproporcionais ao orçamento da 2ª div). Se preferir manter o
     *   comportamento antigo, passe `snapshot.times`.
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

        // Não estoura o teto de ofertas ativas.
        val freeSlots = (MAX_ACTIVE_OFFERS - offers.size).coerceAtLeast(0)
        if (freeSlots == 0) return GenerationResult(emptyList())

        val otherTeams = rivalTeams.filter { it.id != state.managerTeamId }
        if (otherTeams.isEmpty()) return GenerationResult(emptyList())

        // Candidatos: jogadores do elenco que ainda não têm oferta ativa.
        val alreadyTargeted = offers.map { it.playerId }.toSet()
        val candidates = roster
            .filter { it.id !in alreadyTargeted }
            .filter { shouldTargetPlayer(state, it) }
            .shuffled()
            .take(minOf(freeSlots, MAX_OFFERS_PER_ROUND))

        val created = candidates.map { player ->
            val requested = MoraleService.hasRequestedTransfer(state, player.id)
            val team = otherTeams.random()
            val amount = offerAmountFor(player, requested, marketPriceOf)
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
                motivatedByRequest = requested
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

    // ── Expiração ───────────────────────────────────────────────────────

    /**
     * Remove ofertas que expiraram por data OU porque o mercado fechou.
     * Retorna a lista de ofertas removidas (para log, se desejado).
     */
    fun expireOffers(state: GameState): List<IncomingTransferOffer> {
        val offers = state.incomingOffers ?: return emptyList()
        if (offers.isEmpty()) return emptyList()

        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return emptyList()
        val marketOpen = TransferWindowService.isMarketOpen(state)

        val expired = offers.filter { offer ->
            if (!marketOpen) return@filter true
            val exp = runCatching { LocalDate.parse(offer.expiresOn) }.getOrNull()
            exp == null || today.isAfter(exp)
        }
        if (expired.isNotEmpty()) offers.removeAll(expired)
        return expired
    }

    // ── Resposta do gerente ─────────────────────────────────────────────

    /** Oferta ativa pelo id, ou null. */
    fun offerById(state: GameState, offerId: String): IncomingTransferOffer? =
        state.incomingOffers?.find { it.id == offerId }

    /** Lista (não-nula) de ofertas ativas, ordenadas por valor desc. */
    fun activeOffers(state: GameState): List<IncomingTransferOffer> =
        (state.incomingOffers ?: emptyList()).sortedByDescending { it.amountBrl }

    /** Remove uma oferta da lista (após aceitar/recusar). */
    fun removeOffer(state: GameState, offerId: String) {
        state.incomingOffers?.removeAll { it.id == offerId }
    }

    /**
     * Marca a recusa de uma oferta: remove da lista e aplica o efeito moral
     * (mais pesado se o jogador havia pedido para sair).
     */
    fun rejectOffer(state: GameState, offer: IncomingTransferOffer) {
        MoraleService.recordTransferOfferRejected(
            state, offer.playerId, hadRequested = offer.motivatedByRequest
        )
        removeOffer(state, offer.id)
    }
}
