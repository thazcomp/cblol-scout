package com.cblol.scout

import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.Side
import com.cblol.scout.game.Champions
import com.cblol.scout.game.LiveMatchEngine
import org.junit.Assert.*
import org.junit.Test

class LiveMatchEngineTest {

    private val home = makeRoster5("HOME", 75)
    private val away = makeRoster5("AWAY", 75)

    @Test
    fun generatePickBan_produces10BansTotal() {
        val events = LiveMatchEngine.generatePickBan(1, home, away)
        assertEquals(10, events.filterIsInstance<MatchEvent.Ban>().size)
    }

    @Test
    fun generatePickBan_produces10PicksTotal() {
        val events = LiveMatchEngine.generatePickBan(1, home, away)
        assertEquals(10, events.filterIsInstance<MatchEvent.Pick>().size)
    }

    @Test
    fun generatePickBan_5BansPerSide() {
        val bans = LiveMatchEngine.generatePickBan(1, home, away)
            .filterIsInstance<MatchEvent.Ban>()
        assertEquals(5, bans.count { it.side == Side.HOME })
        assertEquals(5, bans.count { it.side == Side.AWAY })
    }

    @Test
    fun generatePickBan_5PicksPerSide() {
        val picks = LiveMatchEngine.generatePickBan(1, home, away)
            .filterIsInstance<MatchEvent.Pick>()
        assertEquals(5, picks.count { it.side == Side.HOME })
        assertEquals(5, picks.count { it.side == Side.AWAY })
    }

    @Test
    fun generatePickBan_noChampionUsedTwice() {
        val events = LiveMatchEngine.generatePickBan(1, home, away)
        val all = events.filterIsInstance<MatchEvent.Ban>().map { it.champion } +
                  events.filterIsInstance<MatchEvent.Pick>().map { it.champion }
        assertEquals("Campeão repetido", all.size, all.distinct().size)
    }

    @Test
    fun generatePickBan_noBannedChampionPicked() {
        val events = LiveMatchEngine.generatePickBan(1, home, away)
        val banned = events.filterIsInstance<MatchEvent.Ban>().map { it.champion }.toSet()
        val picked = events.filterIsInstance<MatchEvent.Pick>().map { it.champion }.toSet()
        assertTrue("Campeão banido foi picado", banned.intersect(picked).isEmpty())
    }

    @Test
    fun generatePickBan_bansAlternateSides() {
        val bans = LiveMatchEngine.generatePickBan(1, home, away)
            .filterIsInstance<MatchEvent.Ban>()
        for (i in bans.indices step 2) {
            assertNotEquals("Bans devem alternar HOME/AWAY",
                bans[i].side, bans.getOrNull(i + 1)?.side)
        }
    }

    @Test
    fun generatePickBan_withPlan_usesBlueBan() {
        val planBan = Champions.TOP.first()
        val plan = PickBanPlan(1, emptyList(), emptyList(), listOf(planBan), emptyList())
        val bans = LiveMatchEngine.generatePickBan(1, home, away, plan)
            .filterIsInstance<MatchEvent.Ban>().map { it.champion }
        assertTrue("Blue ban do plano ausente", planBan in bans)
    }

    @Test
    fun generatePickBan_withPlan_usesRedBan() {
        val planBan = Champions.ADC.first()
        val plan = PickBanPlan(1, emptyList(), emptyList(), emptyList(), listOf(planBan))
        val bans = LiveMatchEngine.generatePickBan(1, home, away, plan)
            .filterIsInstance<MatchEvent.Ban>().map { it.champion }
        assertTrue("Red ban do plano ausente", planBan in bans)
    }

    @Test
    fun generatePickBan_withPlan_usesBluePick() {
        val planPick = Champions.MID.first()
        val plan = PickBanPlan(1, listOf(planPick), emptyList(), emptyList(), emptyList())
        val picks = LiveMatchEngine.generatePickBan(1, home, away, plan)
            .filterIsInstance<MatchEvent.Pick>().map { it.champion }
        assertTrue("Blue pick do plano ausente", planPick in picks)
    }

    @Test
    fun generatePickBan_withPlan_usesRedPick() {
        val planPick = Champions.SUP.first()
        val plan = PickBanPlan(1, emptyList(), listOf(planPick), emptyList(), emptyList())
        val picks = LiveMatchEngine.generatePickBan(1, home, away, plan)
            .filterIsInstance<MatchEvent.Pick>().map { it.champion }
        assertTrue("Red pick do plano ausente", planPick in picks)
    }

    @Test
    fun generatePickBan_withFullPlan_honorsAllEntries() {
        val blueBans  = Champions.TOP.take(5)
        val redBans   = Champions.JNG.take(5)
        val bluePicks = Champions.MID.take(5)
        val redPicks  = Champions.ADC.take(5)
        val plan = PickBanPlan(1, bluePicks, redPicks, blueBans, redBans)
        val events = LiveMatchEngine.generatePickBan(1, home, away, plan)
        val bans  = events.filterIsInstance<MatchEvent.Ban>().map { it.champion }
        val picks = events.filterIsInstance<MatchEvent.Pick>().map { it.champion }
        blueBans.forEach  { assertTrue("blue ban $it ausente",  it in bans)  }
        redBans.forEach   { assertTrue("red ban $it ausente",   it in bans)  }
        bluePicks.forEach { assertTrue("blue pick $it ausente", it in picks) }
        redPicks.forEach  { assertTrue("red pick $it ausente",  it in picks) }
    }

    @Test
    fun generatePickBan_alwaysProduces20Events() {
        repeat(5) {
            assertEquals(20, LiveMatchEngine.generatePickBan(1, home, away).size)
        }
    }
}
