package com.cblol.scout

import com.cblol.scout.game.MatchSimulator
import org.junit.Assert.*
import org.junit.Test

class MatchSimulatorTest {

    @Test
    fun teamStrength_uniformOverall_returnsValue() {
        assertEquals(80, MatchSimulator.teamStrength(makeRoster5("T", 80)))
    }

    @Test
    fun teamStrength_emptyRoster_returns50() {
        assertEquals(50, MatchSimulator.teamStrength(emptyList()))
    }

    @Test
    fun teamStrength_mixedTitularReserve_computesCorrectAverage() {
        val starters = listOf(
            makePlayer("p1", "TOP", 80, true),
            makePlayer("p2", "JNG", 80, true)
        )
        val reserves = listOf(
            makePlayer("p3", "MID", 60, false),
            makePlayer("p4", "ADC", 60, false),
            makePlayer("p5", "SUP", 60, false)
        )
        // (80+80+60+60+60)/5 = 68
        assertEquals(68, MatchSimulator.teamStrength(starters + reserves))
    }

    @Test
    fun teamStrength_onlyReserves_usesBestFive() {
        // Reservas com overalls 80,70,60,50,40,30,20,10 → top 5 = (80+70+60+50+40)/5 = 60
        val roster = listOf(
            makePlayer("p1", "TOP", 80, false),
            makePlayer("p2", "JNG", 70, false),
            makePlayer("p3", "MID", 60, false),
            makePlayer("p4", "ADC", 50, false),
            makePlayer("p5", "SUP", 40, false),
            makePlayer("p6", "TOP", 30, false),
            makePlayer("p7", "JNG", 20, false),
            makePlayer("p8", "MID", 10, false)
        )
        assertEquals(60, MatchSimulator.teamStrength(roster))
    }

    @Test
    fun strongerTeam_winsStatisticallyOver80Percent() {
        val strong = makeRoster5("S", 95)
        val weak   = makeRoster5("W", 50)
        var strongWins = 0
        repeat(200) {
            var sw = 0; var ww = 0
            while (sw < 2 && ww < 2) {
                val noise = (-14..14).random()
                val diff = (MatchSimulator.teamStrength(strong) + 4) - MatchSimulator.teamStrength(weak)
                if (diff + noise >= 0) sw++ else ww++
            }
            if (sw > ww) strongWins++
        }
        assertTrue("Time forte deveria vencer >80% (venceu $strongWins/200)", strongWins > 160)
    }

    @Test
    fun equalTeams_homeWinRateBetween50And80Percent() {
        val t1 = makeRoster5("T1", 75)
        val t2 = makeRoster5("T2", 75)
        var t1Wins = 0
        repeat(200) {
            var w1 = 0; var w2 = 0
            while (w1 < 2 && w2 < 2) {
                val noise = (-14..14).random()
                val diff = (MatchSimulator.teamStrength(t1) + 4) - MatchSimulator.teamStrength(t2)
                if (diff + noise >= 0) w1++ else w2++
            }
            if (w1 > w2) t1Wins++
        }
        // Home bonus de +4 faz T1 ganhar um pouco mais; esperado 50-80%
        assertTrue("Win rate esperada 50-80% (foi $t1Wins/200)", t1Wins in 100..160)
    }
}
