package com.cblol.scout

import com.cblol.scout.game.AdvanceReport
import org.junit.Assert.*
import org.junit.Test

class AdvanceReportTest {

    @Test
    fun defaultValues_allZeroAndFalse() {
        val r = AdvanceReport()
        assertEquals(0, r.matchesPlayed)
        assertFalse(r.myWin)
        assertFalse(r.myLoss)
        assertEquals(0L, r.income)
        assertEquals(0L, r.expense)
    }

    @Test
    fun income_canAccumulate() {
        val r = AdvanceReport()
        r.income += 100_000L
        r.income += 50_000L
        assertEquals(150_000L, r.income)
    }

    @Test
    fun expense_canAccumulate() {
        val r = AdvanceReport()
        r.expense += 80_000L
        r.expense += 20_000L
        assertEquals(100_000L, r.expense)
    }

    @Test
    fun matchesPlayed_canIncrement() {
        val r = AdvanceReport()
        r.matchesPlayed += 3
        assertEquals(3, r.matchesPlayed)
    }

    @Test
    fun myWin_canBeSetTrue() {
        val r = AdvanceReport()
        r.myWin = true
        assertTrue(r.myWin)
    }

    @Test
    fun myLoss_canBeSetTrue() {
        val r = AdvanceReport()
        r.myLoss = true
        assertTrue(r.myLoss)
    }

    @Test
    fun incomeAndExpense_areIndependent() {
        val r = AdvanceReport()
        r.income  += 200_000L
        r.expense += 50_000L
        assertEquals(200_000L, r.income)
        assertEquals(50_000L, r.expense)
    }

    @Test
    fun transferRequests_startsEmpty_andAccumulates() {
        val r = AdvanceReport()
        assertTrue(r.transferRequests.isEmpty())
        r.transferRequests += "Tinowns"
        r.transferRequests += "Robo"
        assertEquals(listOf("Tinowns", "Robo"), r.transferRequests)
    }

    @Test
    fun incomingOffers_startsEmpty_andAccumulates() {
        val r = AdvanceReport()
        assertTrue(r.incomingOffers.isEmpty())
        r.incomingOffers += "Titan"
        assertEquals(listOf("Titan"), r.incomingOffers)
    }

    @Test
    fun transferRequests_and_incomingOffers_areIndependent() {
        val r = AdvanceReport()
        r.transferRequests += "A"
        r.incomingOffers += "B"
        assertEquals(listOf("A"), r.transferRequests)
        assertEquals(listOf("B"), r.incomingOffers)
    }
}
