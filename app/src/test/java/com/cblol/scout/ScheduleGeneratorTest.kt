package com.cblol.scout

import com.cblol.scout.game.ScheduleGenerator
import org.junit.Assert.*
import org.junit.Test

class ScheduleGeneratorTest {

    @Test
    fun generate_returns56Matches() {
        assertEquals(56, ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28").size)
    }

    @Test
    fun generate_allMatchesHaveUniqueIds() {
        val matches = ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28")
        val ids = matches.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun generate_roundsAre1To14() {
        val rounds = ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28")
            .map { it.round }.distinct().sorted()
        assertEquals((1..14).toList(), rounds)
    }

    @Test
    fun generate_eachTeamPlays7HomeAnd7Away() {
        val matches = ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28")
        EIGHT_TEAMS.forEach { team ->
            assertEquals("home de $team", 7, matches.count { it.homeTeamId == team })
            assertEquals("away de $team", 7, matches.count { it.awayTeamId == team })
        }
    }

    @Test
    fun generate_noTeamFacesItself() {
        ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28").forEach {
            assertNotEquals(it.homeTeamId, it.awayTeamId)
        }
    }

    @Test
    fun generate_eachPairPlaysExactlyTwice() {
        val matches = ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28")
        EIGHT_TEAMS.forEach { a ->
            EIGHT_TEAMS.filter { it != a }.forEach { b ->
                val count = matches.count {
                    (it.homeTeamId == a && it.awayTeamId == b) ||
                    (it.homeTeamId == b && it.awayTeamId == a)
                }
                assertEquals("$a vs $b deveria ser 2x", 2, count)
            }
        }
    }

    @Test
    fun generate_datesAscendByRound() {
        val matches = ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28")
        val dateByRound = matches.groupBy { it.round }.mapValues { it.value.first().date }
        val dates = dateByRound.keys.sorted().map { dateByRound[it]!! }
        assertEquals(dates, dates.sorted())
    }

    @Test
    fun generate_allMatchesStartNotPlayed() {
        ScheduleGenerator.generate(EIGHT_TEAMS, "2026-03-28").forEach {
            assertFalse(it.played)
            assertEquals(0, it.homeScore)
            assertEquals(0, it.awayScore)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_throws_ifFewerThan8Teams() {
        ScheduleGenerator.generate(listOf("T1", "T2"), "2026-03-28")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_throws_ifMoreThan8Teams() {
        ScheduleGenerator.generate(EIGHT_TEAMS + "T9", "2026-03-28")
    }
}
