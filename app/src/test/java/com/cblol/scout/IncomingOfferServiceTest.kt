package com.cblol.scout

import com.cblol.scout.data.OfferStatus
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
 *
 * **Política de histórico**: propostas NUNCA são removidas. O ciclo de vida
 * usa [OfferStatus] — PENDING → uma das resoluções (ACCEPTED/REJECTED/EXPIRED).
 * Os testes refletem isso: aceitar/recusar/expirar não diminui o tamanho da
 * lista, apenas muta o status.
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

    // Geracao: pre-condicoes ----------------------------------------------

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
    fun generate_neverExceedsMaxPendingOffers() {
        // Avança a data a cada rodada para escapar do intervalo e acumular ofertas.
        // O teto é por PENDINGs (resolvidas não contam); como nenhum tick aqui
        // resolve nada, pendingOffers deve respeitar o limite.
        val roster = makeRoster5("T1", 85)
        val gs = makeGameState()
        flagAllRequested(gs, roster)

        var day = java.time.LocalDate.parse(gs.currentDate)
        repeat(10) {
            day = day.plusDays(IncomingOfferService.OFFER_INTERVAL_DAYS.toLong())
            gs.currentDate = day.toString()
            if (TransferWindowService.isMarketOpen(gs)) {
                IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
            }
        }
        assertTrue("Não pode exceder 5 ofertas pendentes",
            IncomingOfferService.pendingOffers(gs).size <= 5)
    }

    // Expiracao -----------------------------------------------------------

    @Test
    fun expire_marksOffersAsExpiredWhenMarketCloses() {
        // Propostas não saem da lista — ficam com status EXPIRED para histórico.
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        val countBefore = (gs.incomingOffers ?: emptyList()).size

        // Move para fora da janela → todas as PENDING devem virar EXPIRED.
        gs.currentDate = "2026-05-20"
        assertFalse(TransferWindowService.isMarketOpen(gs))
        val expired = IncomingOfferService.expireOffers(gs)
        if (countBefore > 0) {
            assertEquals(countBefore, expired.size)
        }
        // Total de ofertas (incluindo resolvidas) deve permanecer o mesmo.
        assertEquals(countBefore, (gs.incomingOffers ?: emptyList()).size)
        // Nenhuma PENDING sobrou.
        assertTrue(IncomingOfferService.pendingOffers(gs).isEmpty())
        // Todas marcadas como EXPIRED com resolvedOn setado.
        (gs.incomingOffers ?: emptyList()).forEach {
            assertEquals(OfferStatus.EXPIRED, it.status)
            assertEquals(gs.currentDate, it.resolvedOn)
        }
    }

    @Test
    fun expire_marksOffersPastValidityDateAsExpired() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        val pendingBefore = IncomingOfferService.pendingOffers(gs).size

        // Avança além da validade, mas ainda dentro da janela de pré-temporada.
        val day = java.time.LocalDate.parse(gs.currentDate).plusDays(6)
        gs.currentDate = day.toString()
        if (TransferWindowService.isMarketOpen(gs) && pendingBefore > 0) {
            IncomingOfferService.expireOffers(gs)
            // Ofertas devem ter mudado de status (não saíram da lista).
            assertTrue(IncomingOfferService.pendingOffers(gs).size < pendingBefore)
        }
    }

    @Test
    fun expire_isIdempotent() {
        // Chamar expireOffers várias vezes não muda nada além da primeira
        // — propostas já EXPIRED ficam intactas.
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        gs.currentDate = "2026-05-20"  // mercado fechado
        IncomingOfferService.expireOffers(gs)
        val resolvedDates = (gs.incomingOffers ?: emptyList()).map { it.resolvedOn }
        // Avança o tempo e expira de novo — nada deve mudar.
        gs.currentDate = "2026-06-15"
        val secondPass = IncomingOfferService.expireOffers(gs)
        assertTrue("Segunda chamada não deve expirar nada", secondPass.isEmpty())
        assertEquals(resolvedDates, (gs.incomingOffers ?: emptyList()).map { it.resolvedOn })
    }

    // Resposta: recusar ---------------------------------------------------

    @Test
    fun rejectOffer_marksAsRejected_keepsInList() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 85)
        flagAllRequested(gs, roster)
        IncomingOfferService.generateOffersIfDue(gs, makeSnapshot(), roster, ::marketPrice)
        val offer = IncomingOfferService.pendingOffers(gs).firstOrNull() ?: return
        val countBefore = (gs.incomingOffers ?: emptyList()).size

        IncomingOfferService.rejectOffer(gs, offer)

        // Oferta CONTINUA na lista — só mudou de status.
        val sameOffer = IncomingOfferService.offerById(gs, offer.id)
        assertNotNull("Oferta deve permanecer na lista após recusa", sameOffer)
        assertEquals(OfferStatus.REJECTED, sameOffer?.status)
        assertEquals(gs.currentDate, sameOffer?.resolvedOn)
        assertEquals(countBefore, (gs.incomingOffers ?: emptyList()).size)
        assertFalse(sameOffer?.isPending ?: true)
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

    // Resposta: aceitar (via markAccepted) --------------------------------

    @Test
    fun markAccepted_marksAsAccepted_keepsInList() {
        // Espelha o que TransferMarket.acceptIncomingOffer faz com o serviço.
        val gs = makeGameState()
        val offer = com.cblol.scout.data.IncomingTransferOffer(
            "o1", "p1", "P1", "MID", "T2", "T2",
            1_000_000, gs.currentDate, "2026-03-20"
        )
        gs.incomingOffers = mutableListOf(offer)

        IncomingOfferService.markAccepted(gs, "o1")

        val same = IncomingOfferService.offerById(gs, "o1")
        assertNotNull(same)
        assertEquals(OfferStatus.ACCEPTED, same?.status)
        assertEquals(gs.currentDate, same?.resolvedOn)
    }

    // activeOffers ordenacao ----------------------------------------------

    @Test
    fun activeOffers_pendingFirst_thenResolvedByDate() {
        // Mistura PENDINGs e resolvidas — PENDINGs devem vir primeiro,
        // resolvidas em seguida ordenadas por data desc.
        val gs = makeGameState()
        gs.incomingOffers = mutableListOf(
            // resolvida antiga
            com.cblol.scout.data.IncomingTransferOffer(
                id = "r1", playerId = "p1", playerName = "P1", playerRole = "MID",
                fromTeamId = "T2", fromTeamName = "T2",
                amountBrl = 500_000, offeredOn = "2026-01-10", expiresOn = "2026-01-15",
                status = OfferStatus.REJECTED,
                resolvedOn = "2026-01-15"
            ),
            // pendente com valor médio
            com.cblol.scout.data.IncomingTransferOffer(
                id = "p1", playerId = "p2", playerName = "P2", playerRole = "TOP",
                fromTeamId = "T3", fromTeamName = "T3",
                amountBrl = 1_000_000, offeredOn = gs.currentDate, expiresOn = "2026-03-20"
            ),
            // resolvida recente
            com.cblol.scout.data.IncomingTransferOffer(
                id = "r2", playerId = "p3", playerName = "P3", playerRole = "ADC",
                fromTeamId = "T4", fromTeamName = "T4",
                amountBrl = 2_000_000, offeredOn = "2026-02-01", expiresOn = "2026-02-05",
                status = OfferStatus.ACCEPTED,
                resolvedOn = "2026-02-05"
            )
        )
        val sorted = IncomingOfferService.activeOffers(gs)
        // 1º: pendente. 2º/3º: resolvidas por data desc (r2 antes de r1).
        assertEquals(listOf("p1", "r2", "r1"), sorted.map { it.id })
    }

    @Test
    fun activeOffers_allPending_sortedByAmountDescending() {
        // Quando todas são PENDING e têm mesma data, a ordem cai no valor.
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
