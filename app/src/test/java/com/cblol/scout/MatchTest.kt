package com.cblol.scout

import com.google.gson.Gson
import com.cblol.scout.data.Match
import org.junit.Assert.*
import org.junit.Test

class MatchTest {

    @Test
    fun winnerTeamId_notPlayed_returnsNull() {
        assertNull(makeMatch().winnerTeamId())
    }

    @Test
    fun winnerTeamId_homeWins20_returnsHome() {
        assertEquals("T1", makeMatch(played = true, homeScore = 2, awayScore = 0).winnerTeamId())
    }

    @Test
    fun winnerTeamId_awayWins20_returnsAway() {
        assertEquals("T2", makeMatch(played = true, homeScore = 0, awayScore = 2).winnerTeamId())
    }

    @Test
    fun winnerTeamId_awayWins21_returnsAway() {
        assertEquals("T2", makeMatch(played = true, homeScore = 1, awayScore = 2).winnerTeamId())
    }

    @Test
    fun winnerTeamId_homeWins21_returnsHome() {
        assertEquals("T1", makeMatch(played = true, homeScore = 2, awayScore = 1).winnerTeamId())
    }

    @Test
    fun defaultValues_notPlayedZeroScore() {
        val m = makeMatch()
        assertFalse(m.played)
        assertEquals(0, m.homeScore)
        assertEquals(0, m.awayScore)
        assertNull(m.pickBanPlan)
    }

    @Test
    fun gson_roundTrip_preservesFields() {
        val gson = Gson()
        val m = makeMatch("id1", played = true, homeScore = 2, awayScore = 1)
        val back = gson.fromJson(gson.toJson(m), Match::class.java)
        assertEquals(m.id, back.id)
        assertEquals(m.homeScore, back.homeScore)
        assertEquals(m.awayScore, back.awayScore)
        assertTrue(back.played)
    }

    @Test
    fun homeTeamId_awayTeamId_arePreserved() {
        val m = makeMatch(homeId = "LOUD", awayId = "paiN")
        assertEquals("LOUD", m.homeTeamId)
        assertEquals("paiN", m.awayTeamId)
    }
}
