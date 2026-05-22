package com.cblol.scout

import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.domain.usecase.MoraleService
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Testes dos gatilhos de **pedido de transferência** e do efeito moral de
 * **recusar uma oferta**, adicionados ao [MoraleService].
 *
 * O pedido em si é sorteado (15%/dia) quando há um motivo válido. Para evitar
 * flake, os testes que verificam "pede" rodam vários dias (a chance de NUNCA
 * pedir em N dias com motivo válido cai exponencialmente), e os que verificam
 * "não pede" garantem que o motivo nunca existe (prob efetiva = 0).
 */
class MoraleTransferRequestTest {

    /** Roda o decay diário por [days] dias, avançando a data, e diz se pediu. */
    private fun runDaysAndCheckRequested(
        gs: com.cblol.scout.data.GameState,
        player: com.cblol.scout.data.Player,
        days: Int
    ): Boolean {
        var date = LocalDate.parse(gs.currentDate)
        repeat(days) {
            date = date.plusDays(1)
            gs.currentDate = date.toString()
            MoraleService.applyDailyDecay(gs, listOf(player))
            if (MoraleService.hasRequestedTransfer(gs, player.id)) return true
        }
        return MoraleService.hasRequestedTransfer(gs, player.id)
    }

    // ── Gatilho 1: moral no fundo ───────────────────────────────────────

    @Test
    fun veryLowMorale_eventuallyRequestsTransfer() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val player = makePlayer("p1", "MID", 80, teamId = "T1")
        // Moral no fundo (<= 10) → motivo "profundamente insatisfeito".
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 5,
            lastPlayedDate = "2026-04-01"  // jogou hoje, então decay não mexe
        )
        // 60 dias com prob 0.15 → chance de nunca pedir ≈ 0.85^60 ≈ 0.006%.
        assertTrue(runDaysAndCheckRequested(gs, player, 60))
    }

    // ── Gatilho 2: reserva frustrado ────────────────────────────────────

    @Test
    fun dissatisfiedReserve_longOnBench_eventuallyRequests() {
        val gs = makeGameState(currentDate = "2026-04-01")
        // Reserva (titular=false), insatisfeito (<=30), parado há muito tempo.
        val player = makePlayer("p2", "MID", 75, titular = false, teamId = "T1")
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 25,
            lastPlayedDate = "2026-02-01"  // > 21 dias no banco
        )
        assertTrue(runDaysAndCheckRequested(gs, player, 60))
    }

    @Test
    fun dissatisfiedStarter_doesNotRequestForBenchReason() {
        val gs = makeGameState(currentDate = "2026-04-01")
        // Titular insatisfeito (mood 25), mas como é TITULAR não há gatilho de
        // "reserva frustrado", e mood 25 > 10 (não dispara o gatilho de fundo).
        val player = makePlayer("p3", "MID", 75, titular = true, teamId = "T1")
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 25,
            lastPlayedDate = "2026-04-01"
        )
        assertFalse(runDaysAndCheckRequested(gs, player, 60))
    }

    @Test
    fun happyReserve_doesNotRequest() {
        val gs = makeGameState(currentDate = "2026-04-01")
        // Reserva, mas FELIZ (mood 70 > 30) → sem gatilho.
        val player = makePlayer("p4", "MID", 75, titular = false, teamId = "T1")
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 70,
            lastPlayedDate = "2026-02-01"
        )
        assertFalse(runDaysAndCheckRequested(gs, player, 60))
    }

    @Test
    fun recentReserve_dissatisfied_doesNotRequestYet() {
        val gs = makeGameState(currentDate = "2026-04-01")
        // Reserva insatisfeito, mas há pouco tempo no banco (< 21 dias).
        val player = makePlayer("p5", "MID", 75, titular = false, teamId = "T1")
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 25,
            lastPlayedDate = "2026-03-29"  // ~3 dias
        )
        // Rodamos só 5 dias para não cruzar o limiar de 21 dias de banco.
        assertFalse(runDaysAndCheckRequested(gs, player, 5))
    }

    // ── clearTransferRequest ────────────────────────────────────────────

    @Test
    fun clearTransferRequest_removesFlag() {
        val gs = makeGameState()
        val player = makePlayer("p6", "MID", 80, teamId = "T1")
        gs.playerOverrides[player.id] = PlayerOverride(
            playerId = player.id, mood = 5, transferRequestedOn = gs.currentDate
        )
        assertTrue(MoraleService.hasRequestedTransfer(gs, player.id))
        MoraleService.clearTransferRequest(gs, player.id)
        assertFalse(MoraleService.hasRequestedTransfer(gs, player.id))
    }

    // ── Efeito de recusar oferta ────────────────────────────────────────

    @Test
    fun recordOfferRejected_requested_dropsMoraleMoreThanNonRequested() {
        val gs = makeGameState()
        val a = makePlayer("a", "MID", 80, teamId = "T1")
        val b = makePlayer("b", "TOP", 80, teamId = "T1")
        gs.playerOverrides[a.id] = PlayerOverride(a.id, mood = 60)
        gs.playerOverrides[b.id] = PlayerOverride(b.id, mood = 60)

        MoraleService.recordTransferOfferRejected(gs, a.id, hadRequested = true)
        MoraleService.recordTransferOfferRejected(gs, b.id, hadRequested = false)

        val dropA = 60 - MoraleService.moodOf(gs, a.id)
        val dropB = 60 - MoraleService.moodOf(gs, b.id)
        assertTrue("Recusar quem pediu deve doer mais", dropA > dropB)
        assertTrue(dropB > 0)
    }

    @Test
    fun recordOfferRejected_addsHistoryEntry() {
        val gs = makeGameState()
        val p = makePlayer("p", "MID", 80, teamId = "T1")
        gs.playerOverrides[p.id] = PlayerOverride(p.id, mood = 60)
        val before = MoraleService.historyOf(gs, p.id).size
        MoraleService.recordTransferOfferRejected(gs, p.id, hadRequested = true)
        assertTrue(MoraleService.historyOf(gs, p.id).size > before)
    }
}
