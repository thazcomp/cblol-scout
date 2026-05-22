package com.cblol.scout

import com.cblol.scout.domain.usecase.IncomingOfferService
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.domain.usecase.TransferWindowService
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes do [IncomingOfferService] — geração, expiração e resposta a ofertas de
 * compra recebidas de outros times.
 *
 * O serviço é JVM-puro: recebe [com.cblol.scout.data.GameState],
 * [com.cblol.scout.data.SnapshotData], roster e uma função de preço. Não toca
 * Android, então é totalmente testável sem mocks de framework.
 *
 * A geração é probabilística (usa Random), então os testes usam um roster onde
 * TODOS pediram transferência (prob 0.70 cada) ou validam invariantes que valem
 * independentemente do sorteio (teto de ofertas, expiração, intervalo, etc.).
 */
class IncomingOfferServiceTest {

    private fun marketPrice(player: com.cblol.scout.data.Player): Long =
        (player.contrato.salario_mensal_estimado_brl ?: 0L) * 12

    /** Marca todos os jogadores do roster como tendo pedido transferência. */
    private fun flagAllRequested(gs: com.cblol.scout.data.GameState, roster: List<com.cblol.scout.data.Player>) {
        roster.forEach { p ->
            // inicializa moral e marca pedido (via override direto, simulando o decay)
            gs.playerOverrides[p.id] = com.cblol.scout.data.PlayerOverride(
                playerId = p.id,
                mood = 5,
                transferRequestedOn = gs.currentDate
            )
        }
    }

    // ── Geração: pré-condições ──────────────────────────────────────────

    @Test
    fun generate_marketClosed_producesNothing() {
        // currentDate fora de qualquer janela (meio do split, após inter-temporada)
        val gs = makeGameState(currentDate = "2026-05-20")
        assertFalse(TransferWindowService.isMarketOpen(gs))
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)

        val result = IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        assertTrue(result.newOffers.isEmpty())
        assertTrue(IncomingOfferService.activeOffers(gs).isEmpty())
    }

    @Test
    fun generate_marketOpen_canProduceOffersForRequestedPlayers() {
        val gs = makeGameState()  // pré-temporada: mercado aberto
        assertTrue(TransferWindowService.isMarketOpen(gs))
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)

        // Com 5 jogadores a 0.70 de prob, a chance de zero ofertas é 0.3^5 ≈ 0.24%.
        // Rodamos algumas vezes em estados independentes para evitar flake.
        var totalOffers = 0
        repeat(5) {
            val fresh = makeGameState()
            flagAllRequested(fresh, roster)
            val r = IncomingOfferService.generateOffersIfDue(fresh, makeSnapshot(), roster, ::marketPrice)
            totalOffers += r.newOffers.size
        }
        assertTrue("Esperava ao menos uma oferta em 5 rodadas", totalOffers > 0)
    }

    @Test
    fun generate_respectsInterval_noSecondRoundSameDay() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)

        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        // Segunda chamada no mesmo dia não deve gerar nada (intervalo não passou).
        val second = IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        assertTrue(second.newOffers.isEmpty())
    }

    @Test
    fun generate_setsLastOffersDate() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        assertEquals(gs.currentDate, gs.lastIncomingOffersDate)
    }

    @Test
    fun generate_neverExceedsMaxActiveOffers() {
        // Avança a data a cada rodada para escapar do intervalo e acumular ofertas.
        val roster = makeRoster5("T1", 85)
        val gs = makeGameState()
        flagAllRequested(gs, roster)

        var day = java.time.LocalDate.parse(gs.currentDate)
        repeat(10) {
            day = day.plusDays(IncomingOfferService.OFFER_INTERVAL_DAYS.toLong())
            gs.currentDate = day.toString()
            // só dentro da janela
            if (TransferWindowService.isMarketOpen(gs)) {
                IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
            }
        }
        assertTrue("Não pode exceder 5 ofertas ativas",
            IncomingOfferService.activeOffers(gs).size <= 5)
    }

    // ── Expiração ───────────────────────────────────────────────────────

    @Test
    fun expire_removesOffersWhenMarketCloses() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)

        // Move para fora da janela → todas as ofertas devem expirar.
        gs.currentDate = "2026-05-20"
        assertFalse(TransferWindowService.isMarketOpen(gs))
        IncomingOfferService.expireOffers(gs)
        assertTrue(IncomingOfferService.activeOffers(gs).isEmpty())
    }

    @Test
    fun expire_removesOffersPastValidityDate() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        val before = IncomingOfferService.activeOffers(gs).size

        // Avança além da validade, mas ainda dentro da janela de pré-temporada.
        // Pré-temporada vai de gameStart até a véspera do split (21 dias), e a
        // validade da oferta é 5 dias — então +6 dias garante expiração com
        // mercado ainda aberto.
        val day = java.time.LocalDate.parse(gs.currentDate).plusDays(6)
        gs.currentDate = day.toString()
        if (TransferWindowService.isMarketOpen(gs) && before > 0) {
            IncomingOfferService.expireOffers(gs)
            assertTrue(IncomingOfferService.activeOffers(gs).size < before)
        }
    }

    // ── Resposta: recusar ───────────────────────────────────────────────

    @Test
    fun rejectOffer_removesIt() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        val offer = IncomingOfferService.activeOffers(gs).firstOrNull() ?: return

        IncomingOfferService.rejectOffer(gs, offer)
        assertNull(IncomingOfferService.offerById(gs, offer.id))
    }

    @Test
    fun rejectOffer_requestedPlayer_dropsMoraleMore() {
        val gs = makeGameState()
        val player = makePlayer("p1", "MID", 85, teamId = "T1")
        // Moral inicial conhecida + pediu pra sair
        gs.playerOverrides[player.id] = com.cblol.scout.data.PlayerOverride(
            playerId = player.id, mood = 50, transferRequestedOn = gs.currentDate
        )
        val moodBefore = MoraleService.moodOf(gs, player.id)

        val offer = com.cblol.scout.data.IncomingTransferOffer(
            id = "o1", playerId = player.id, playerName = player.nome_jogo,
            playerRole = player.role, fromTeamId = "T2", fromTeamName = "T2",
            amountBrl = 1_000_000, offeredOn = gs.currentDate, expiresOn = "2026-03-20",
            motivatedByRequest = true
        )
        gs.incomingOffers = mutableListOf(offer)

        IncomingOfferService.rejectOffer(gs, offer)
        val moodAfter = MoraleService.moodOf(gs, player.id)
        assertTrue("Moral deveria cair ao recusar proposta que ele queria",
            moodAfter < moodBefore)
    }

    // ── activeOffers ordenado ───────────────────────────────────────────

    @Test
    fun activeOffers_sortedByAmountDescending() {
        val gs = makeGameState()
        gs.incomingOffers = mutableListOf(
            com.cblol.scout.data.IncomingTransferOffer(
                "a", "p1", "P1", "MID", "T2", "T2", 500_000, gs.currentDate, "2026-03-20"),
            com.cblol.scout.data.IncomingTransferOffer(
                "b", "p2", "P2", "TOP", "T3", "T3", 2_000_000, gs.currentDate, "2026-03-20"),
            com.cblol.scout.data.IncomingTransferOffer(
                "c", "p3", "P3", "ADC", "T4", "T4", 1_000_000, gs.currentDate, "2026-03-20")
        )
        val sorted = IncomingOfferService.activeOffers(gs)
        assertEquals(listOf(2_000_000L, 1_000_000L, 500_000L), sorted.map { it.amountBrl })
    }

    @Test
    fun offerById_returnsCorrectOffer() {
        val gs = makeGameState()
        val offer = com.cblol.scout.data.IncomingTransferOffer(
            "xyz", "p1", "P1", "MID", "T2", "T2", 100_000, gs.currentDate, "2026-03-20")
        gs.incomingOffers = mutableListOf(offer)
        assertEquals("p1", IncomingOfferService.offerById(gs, "xyz")?.playerId)
        assertNull(IncomingOfferService.offerById(gs, "nao_existe"))
    }
}
