package com.cblol.scout

import com.cblol.scout.game.TransferMarket
import org.junit.Assert.*
import org.junit.Test

class TransferMarketTest {

    @Test
    fun marketPriceOf_overall90_uses24xMultiplier() {
        val p = makePlayer("p", "MID", 90, salary = 100_000L)
        assertEquals((100_000L * 12 * 2.4).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall85_uses24xMultiplier() {
        val p = makePlayer("p", "MID", 85, salary = 80_000L)
        assertEquals((80_000L * 12 * 2.4).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall84_uses18xMultiplier() {
        val p = makePlayer("p", "MID", 84, salary = 60_000L)
        assertEquals((60_000L * 12 * 1.8).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall75_uses18xMultiplier() {
        val p = makePlayer("p", "MID", 75, salary = 50_000L)
        assertEquals((50_000L * 12 * 1.8).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall74_uses12xMultiplier() {
        val p = makePlayer("p", "MID", 74, salary = 40_000L)
        assertEquals((40_000L * 12 * 1.2).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall65_uses12xMultiplier() {
        val p = makePlayer("p", "MID", 65, salary = 30_000L)
        assertEquals((30_000L * 12 * 1.2).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall55_uses085xMultiplier() {
        val p = makePlayer("p", "MID", 55, salary = 20_000L)
        assertEquals((20_000L * 12 * 0.85).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall54_uses06xMultiplier() {
        val p = makePlayer("p", "MID", 54, salary = 10_000L)
        assertEquals((10_000L * 12 * 0.6).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_overall40_uses06xMultiplier() {
        val p = makePlayer("p", "MID", 40, salary = 10_000L)
        assertEquals((10_000L * 12 * 0.6).toLong(), TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_zeroSalary_returns0() {
        val p = makePlayer("p", "MID", 80, salary = 0L)
        assertEquals(0L, TransferMarket.marketPriceOf(p))
    }

    @Test
    fun marketPriceOf_higherOverall_hasHigherPrice() {
        val cheap = makePlayer("p1", "MID", 50, salary = 10_000L)
        val pricey = makePlayer("p2", "MID", 90, salary = 10_000L)
        assertTrue(TransferMarket.marketPriceOf(pricey) > TransferMarket.marketPriceOf(cheap))
    }
}
