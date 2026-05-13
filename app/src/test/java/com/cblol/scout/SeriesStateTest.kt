package com.cblol.scout

import com.cblol.scout.data.SeriesState
import org.junit.Assert.*
import org.junit.Test

class SeriesStateTest {

    @Test
    fun initial_zeroWins_notFinished() {
        val s = SeriesState()
        assertEquals(0, s.playerWins)
        assertEquals(0, s.opponentWins)
        assertFalse(s.isFinished)
    }

    @Test
    fun recordMap_playerWin_incrementsPlayerWins() {
        assertEquals(1, SeriesState().recordMap(true).playerWins)
    }

    @Test
    fun recordMap_opponentWin_incrementsOpponentWins() {
        assertEquals(1, SeriesState().recordMap(false).opponentWins)
    }

    @Test
    fun isFinished_false_at1_0() {
        assertFalse(SeriesState(1, 0).isFinished)
    }

    @Test
    fun isFinished_false_at1_1() {
        assertFalse(SeriesState(1, 1).isFinished)
    }

    @Test
    fun isFinished_true_playerReaches2_0() {
        assertTrue(SeriesState(2, 0).isFinished)
    }

    @Test
    fun isFinished_true_playerReaches2_1() {
        assertTrue(SeriesState(2, 1).isFinished)
    }

    @Test
    fun isFinished_true_opponentReaches0_2() {
        assertTrue(SeriesState(0, 2).isFinished)
    }

    @Test
    fun isFinished_true_opponentReaches1_2() {
        assertTrue(SeriesState(1, 2).isFinished)
    }

    @Test
    fun chained_playerWins2_1() {
        val s = SeriesState().recordMap(true).recordMap(false).recordMap(true)
        assertEquals(2, s.playerWins)
        assertEquals(1, s.opponentWins)
        assertTrue(s.isFinished)
    }

    @Test
    fun chained_opponentWins0_2() {
        val s = SeriesState().recordMap(false).recordMap(false)
        assertEquals(0, s.playerWins)
        assertEquals(2, s.opponentWins)
        assertTrue(s.isFinished)
    }

    @Test
    fun recordMap_isImmutable_originalUnchanged() {
        val original = SeriesState(1, 0)
        val updated = original.recordMap(true)
        assertEquals(1, original.playerWins) // original não muda
        assertEquals(2, updated.playerWins)
    }
}
