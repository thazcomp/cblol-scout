package com.cblol.scout

import com.cblol.scout.data.Standing
import org.junit.Assert.*
import org.junit.Test

class StandingTest {

    private fun standing(wins: Int, losses: Int, mw: Int, ml: Int) =
        Standing("T1", "Time 1", wins, losses, mw, ml)

    @Test
    fun mapDiff_positive_whenMoreMapsWon() {
        assertEquals(3, standing(2, 0, 4, 1).mapDiff)
    }

    @Test
    fun mapDiff_negative_whenMoreMapsLost() {
        assertEquals(-2, standing(0, 2, 1, 3).mapDiff)
    }

    @Test
    fun mapDiff_zero_whenEqual() {
        assertEquals(0, standing(1, 1, 2, 2).mapDiff)
    }

    @Test
    fun games_isSumOfWinsAndLosses() {
        assertEquals(5, standing(3, 2, 6, 4).games)
    }

    @Test
    fun games_zero_whenNoGamesPlayed() {
        assertEquals(0, standing(0, 0, 0, 0).games)
    }

    @Test
    fun teamId_andTeamName_arePreserved() {
        val s = Standing("LOUD", "LOUD", 5, 2, 10, 4)
        assertEquals("LOUD", s.teamId)
        assertEquals("LOUD", s.teamName)
    }

    @Test
    fun mapsWon_andMapsLost_arePreserved() {
        val s = standing(3, 1, 6, 2)
        assertEquals(6, s.mapsWon)
        assertEquals(2, s.mapsLost)
    }
}
