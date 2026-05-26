package com.cblol.scout

import com.cblol.scout.data.BondTier
import com.cblol.scout.data.PlayerBond
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.domain.usecase.PlayerBondService
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Testes do [PlayerBondService] — sistema de laços (química) entre jogadores.
 *
 * O serviço é JVM-puro: recebe [com.cblol.scout.data.GameState] e roster, sem
 * Android. A maior parte é determinística; o drift diário depende do humor
 * médio (lido do MoraleService, que inicializa o mood em faixa aleatória se
 * ausente), então os testes que verificam direção do drift fixam o mood
 * explicitamente via [PlayerOverride] antes de rodar.
 *
 * Para controlar quantos dias um tick avança, manipulamos `currentDate` e
 * `lastBondTickDate` diretamente — o serviço calcula `days` a partir deles.
 */
class PlayerBondServiceTest {

    // ── keyFor simétrico ────────────────────────────────────────────────

    @Test
    fun keyFor_isSymmetric() {
        assertEquals(PlayerBond.keyFor("a", "b"), PlayerBond.keyFor("b", "a"))
        assertEquals("a|b", PlayerBond.keyFor("a", "b"))
        assertEquals("a|b", PlayerBond.keyFor("b", "a"))
    }

    // ── BondTier.from ───────────────────────────────────────────────────

    @Test
    fun bondTier_fromLevel_mapsToCorrectBand() {
        assertEquals(BondTier.TOXIC,    BondTier.from(-100))
        assertEquals(BondTier.TOXIC,    BondTier.from(-60))
        assertEquals(BondTier.TENSE,    BondTier.from(-59))
        assertEquals(BondTier.TENSE,    BondTier.from(-20))
        assertEquals(BondTier.NEUTRAL,  BondTier.from(-19))
        assertEquals(BondTier.NEUTRAL,  BondTier.from(0))
        assertEquals(BondTier.NEUTRAL,  BondTier.from(19))
        assertEquals(BondTier.FRIENDLY, BondTier.from(20))
        assertEquals(BondTier.FRIENDLY, BondTier.from(59))
        assertEquals(BondTier.BONDED,   BondTier.from(60))
        assertEquals(BondTier.BONDED,   BondTier.from(100))
    }

    // ── ensureBondsFor ──────────────────────────────────────────────────

    @Test
    fun ensureBondsFor_createsNeutralPairForEveryCombination() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)  // 5 jogadores → C(5,2) = 10 pares
        PlayerBondService.ensureBondsFor(gs, roster)
        assertEquals(10, gs.playerBonds?.size)
        gs.playerBonds?.values?.forEach { assertEquals(0, it.level) }
    }

    @Test
    fun ensureBondsFor_isIdempotent_doesNotDuplicate() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        PlayerBondService.ensureBondsFor(gs, roster)
        assertEquals(10, gs.playerBonds?.size)
    }

    @Test
    fun ensureBondsFor_preservesExistingBondLevels() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        // Mexe num laço e garante que ensure não reseta.
        PlayerBondService.recordCombo(gs, roster[0].id, roster[1].id)
        val before = PlayerBondService.levelBetween(gs, roster[0].id, roster[1].id)
        PlayerBondService.ensureBondsFor(gs, roster)
        assertEquals(before, PlayerBondService.levelBetween(gs, roster[0].id, roster[1].id))
    }

    // ── recordCombo / recordFight ───────────────────────────────────────

    @Test
    fun recordCombo_increasesBondAndLogsHistory() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.recordCombo(gs, roster[0].id, roster[1].id)
        val bond = PlayerBondService.bondBetween(gs, roster[0].id, roster[1].id)!!
        assertEquals(12, bond.level)  // DELTA_COMBO = +12
        assertEquals(1, bond.history.size)
        assertEquals(12, bond.history.first().delta)
    }

    @Test
    fun recordFight_decreasesBondAndLogsHistory() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.recordFight(gs, roster[0].id, roster[1].id)
        val bond = PlayerBondService.bondBetween(gs, roster[0].id, roster[1].id)!!
        assertEquals(-16, bond.level)  // DELTA_FIGHT = -16
        assertEquals(1, bond.history.size)
        assertEquals(-16, bond.history.first().delta)
    }

    @Test
    fun recordCombo_isSymmetric_orderDoesNotMatter() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.recordCombo(gs, roster[1].id, roster[0].id)
        // consultando na ordem inversa deve ver o mesmo laço
        assertEquals(12, PlayerBondService.levelBetween(gs, roster[0].id, roster[1].id))
    }

    @Test
    fun applyDelta_clampsToMaxAndMin() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        // Muitos combos seguidos não devem ultrapassar +100.
        repeat(20) { PlayerBondService.recordCombo(gs, roster[0].id, roster[1].id) }
        assertEquals(100, PlayerBondService.levelBetween(gs, roster[0].id, roster[1].id))
        // Muitas brigas não devem passar de -100.
        repeat(20) { PlayerBondService.recordFight(gs, roster[2].id, roster[3].id) }
        assertEquals(-100, PlayerBondService.levelBetween(gs, roster[2].id, roster[3].id))
    }

    // ── recordSeriesResult ──────────────────────────────────────────────

    @Test
    fun recordSeriesResult_win_raisesAllPairs() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        PlayerBondService.recordSeriesResult(gs, roster, won = true)
        // Todos os 10 pares sobem +3 (DELTA_SERIES_WIN).
        gs.playerBonds?.values?.forEach { assertEquals(3, it.level) }
    }

    @Test
    fun recordSeriesResult_loss_lowersAllPairs() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        PlayerBondService.recordSeriesResult(gs, roster, won = false)
        gs.playerBonds?.values?.forEach { assertEquals(-2, it.level) }  // DELTA_SERIES_LOSS
    }

    // ── tickDaily: drift modulado por humor ─────────────────────────────

    /** Fixa o mood de todos os jogadores num valor (para controlar o drift). */
    private fun setMoodAll(gs: com.cblol.scout.data.GameState, roster: List<com.cblol.scout.data.Player>, mood: Int) {
        roster.forEach { p ->
            gs.playerOverrides[p.id] = (gs.playerOverrides[p.id] ?: PlayerOverride(p.id)).copy(mood = mood)
        }
    }

    @Test
    fun tickDaily_highMood_raisesBondOverTime() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 80)  // humor alto → esquenta
        PlayerBondService.ensureBondsFor(gs, roster)

        // Avança 30 dias (em saltos), chamando o tick a cada salto.
        var date = LocalDate.parse(gs.currentDate)
        repeat(10) {
            date = date.plusDays(3)
            gs.currentDate = date.toString()
            PlayerBondService.tickDaily(gs, roster)
        }
        // Com humor alto e 30 dias, o laço médio deve ter subido bem acima de 0.
        assertTrue(PlayerBondService.averageTeamBond(gs, roster) > 10)
    }

    @Test
    fun tickDaily_lowMood_loweresBondOverTime() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 20)  // humor baixo → azeda
        PlayerBondService.ensureBondsFor(gs, roster)

        var date = LocalDate.parse(gs.currentDate)
        repeat(10) {
            date = date.plusDays(3)
            gs.currentDate = date.toString()
            PlayerBondService.tickDaily(gs, roster)
        }
        assertTrue(PlayerBondService.averageTeamBond(gs, roster) < 0)
    }

    @Test
    fun tickDaily_isIdempotentForSameDay() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 80)
        PlayerBondService.ensureBondsFor(gs, roster)

        // Avança 3 dias num único salto e chama tick.
        gs.currentDate = "2026-04-04"
        PlayerBondService.tickDaily(gs, roster)
        val afterFirst = PlayerBondService.averageTeamBond(gs, roster)

        // Chamar de novo no MESMO dia não deve mudar nada (days == 0).
        PlayerBondService.tickDaily(gs, roster)
        assertEquals(afterFirst, PlayerBondService.averageTeamBond(gs, roster))
    }

    @Test
    fun tickDaily_transferRequest_penalizesBond() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 50)  // humor neutro: drift suave
        PlayerBondService.ensureBondsFor(gs, roster)

        // Marca um jogador como tendo pedido transferência.
        gs.playerOverrides[roster[0].id] =
            gs.playerOverrides[roster[0].id]!!.copy(transferRequestedOn = gs.currentDate)

        // Avança 1 dia.
        gs.currentDate = "2026-04-02"
        PlayerBondService.tickDaily(gs, roster)

        // Laços que ENVOLVEM o jogador insatisfeito devem ter penalidade (<= 0),
        // enquanto pares sem ele não levam a penalidade.
        val withRequester = PlayerBondService.levelBetween(gs, roster[0].id, roster[1].id)
        assertTrue("Par com quem pediu transferência deve ser penalizado", withRequester < 0)
    }

    // ── teamStrengthBonus / averageTeamBond ─────────────────────────────

    @Test
    fun averageTeamBond_returnsZeroForLessThanTwoPlayers() {
        val gs = makeGameState()
        assertEquals(0, PlayerBondService.averageTeamBond(gs, emptyList()))
        assertEquals(0, PlayerBondService.averageTeamBond(gs, listOf(makePlayer("p1", "MID"))))
    }

    @Test
    fun teamStrengthBonus_isAverageTimesFactorRounded() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        // Coloca todos os pares em +50 → bônus = round(50 * 0.08) = 4.
        gs.playerBonds!!.keys.toList().forEach { key ->
            val b = gs.playerBonds!![key]!!
            gs.playerBonds!![key] = b.copy(level = 50)
        }
        assertEquals(50, PlayerBondService.averageTeamBond(gs, roster))
        assertEquals(4, PlayerBondService.teamStrengthBonus(gs, roster))
    }

    @Test
    fun teamStrengthBonus_negativeForToxicTeam() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        PlayerBondService.ensureBondsFor(gs, roster)
        gs.playerBonds!!.keys.toList().forEach { key ->
            val b = gs.playerBonds!![key]!!
            gs.playerBonds!![key] = b.copy(level = -100)
        }
        assertEquals(-8, PlayerBondService.teamStrengthBonus(gs, roster))  // round(-100 * 0.08)
    }

    // ── pickComboPair / pickFightPair ───────────────────────────────────

    @Test
    fun pickComboPair_favorsHighestChemistry() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 50)
        PlayerBondService.ensureBondsFor(gs, roster)
        // Cria um par claramente entrosado.
        repeat(5) { PlayerBondService.recordCombo(gs, roster[0].id, roster[1].id) }  // +60

        // Roda várias vezes (há ruído) e confirma que o par forte é o escolhido na maioria.
        var hits = 0
        repeat(30) {
            val pair = PlayerBondService.pickComboPair(gs, roster)!!
            val ids = setOf(pair.first.id, pair.second.id)
            if (ids == setOf(roster[0].id, roster[1].id)) hits++
        }
        assertTrue("Par mais entrosado deve ser favorecido", hits > 15)
    }

    @Test
    fun pickFightPair_favorsWorstChemistry() {
        val gs = makeGameState()
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 50)
        PlayerBondService.ensureBondsFor(gs, roster)
        // Cria um par claramente em atrito.
        repeat(5) { PlayerBondService.recordFight(gs, roster[2].id, roster[3].id) }  // -80

        var hits = 0
        repeat(30) {
            val pair = PlayerBondService.pickFightPair(gs, roster)!!
            val ids = setOf(pair.first.id, pair.second.id)
            if (ids == setOf(roster[2].id, roster[3].id)) hits++
        }
        assertTrue("Par com pior química deve ser favorecido para briga", hits > 15)
    }

    @Test
    fun pickComboPair_nullForTooFewPlayers() {
        val gs = makeGameState()
        assertNull(PlayerBondService.pickComboPair(gs, listOf(makePlayer("p1", "MID"))))
        assertNull(PlayerBondService.pickFightPair(gs, emptyList()))
    }

    // ── tickDaily milestones ────────────────────────────────────────────

    @Test
    fun tickDaily_reportsBondedMilestoneWhenPairCrossesThreshold() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val roster = makeRoster5("T1", 70)
        setMoodAll(gs, roster, 90)  // humor altíssimo: drift forte
        PlayerBondService.ensureBondsFor(gs, roster)
        // Empurra um par para perto do limiar de BONDED (60), com daysTogether
        // já em múltiplo de DRIFT_INTERVAL_DAYS (3) para que o próximo salto
        // complete exatamente 1 passo de drift.
        gs.playerBonds!![PlayerBond.keyFor(roster[0].id, roster[1].id)] =
            PlayerBond(roster[0].id, roster[1].id, level = 58, daysTogether = 3)
        // Fixa o relógio de tick para que o salto seguinte conte 3 dias.
        gs.lastBondTickDate = "2026-04-01"

        // Avança 3 dias → daysTogether 3→6, cruza um boundary de drift (+3 com
        // humor alto), levando o nível de 58 para 61 (BONDED).
        gs.currentDate = "2026-04-04"
        val milestones = PlayerBondService.tickDaily(gs, roster)
        assertTrue(
            "Deveria reportar marco BONDED",
            milestones.any { it.tier == BondTier.BONDED }
        )
    }
}
