package com.cblol.scout

import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Side
import org.junit.Assert.*
import org.junit.Test

class MatchEventTest {

    @Test
    fun ban_holdsCorrectFields() {
        val e = MatchEvent.Ban(1, Side.HOME, "Ahri")
        assertEquals(1, e.gameNumber)
        assertEquals(Side.HOME, e.side)
        assertEquals("Ahri", e.champion)
    }

    @Test
    fun pick_holdsCorrectFields() {
        val e = MatchEvent.Pick(2, Side.AWAY, "Faker", "MID", "Orianna")
        assertEquals(2, e.gameNumber)
        assertEquals(Side.AWAY, e.side)
        assertEquals("Faker", e.playerName)
        assertEquals("MID", e.role)
        assertEquals("Orianna", e.champion)
    }

    @Test
    fun kill_holdsCorrectFields() {
        val e = MatchEvent.Kill("12:34", Side.HOME, "Gumayusi", "Jinx", "Ruler", "Kai'Sa")
        assertEquals("12:34", e.time)
        assertEquals(Side.HOME, e.killerSide)
        assertEquals("Gumayusi", e.killerName)
        assertEquals("Jinx", e.killerChamp)
        assertEquals("Ruler", e.victimName)
        assertEquals("Kai'Sa", e.victimChamp)
    }

    @Test
    fun towerDown_holdsLocation() {
        val e = MatchEvent.TowerDown("08:00", Side.HOME, "top")
        assertEquals("top", e.location)
        assertEquals(Side.HOME, e.side)
    }

    @Test
    fun inhibitor_holdsLocation() {
        val e = MatchEvent.Inhibitor("28:00", Side.AWAY, "mid")
        assertEquals("mid", e.location)
        assertEquals(Side.AWAY, e.side)
    }

    @Test
    fun dragon_holdsType() {
        val e = MatchEvent.Dragon("12:00", Side.AWAY, "Infernal")
        assertEquals("Infernal", e.type)
        assertEquals(Side.AWAY, e.side)
    }

    @Test
    fun baron_holdsSide() {
        val e = MatchEvent.Baron("22:00", Side.HOME)
        assertEquals(Side.HOME, e.side)
        assertEquals("22:00", e.time)
    }

    @Test
    fun herald_holdsSide() {
        val e = MatchEvent.Herald("08:30", Side.AWAY)
        assertEquals(Side.AWAY, e.side)
    }

    @Test
    fun buff_holdsPlayerAndType() {
        val e = MatchEvent.Buff("04:00", Side.HOME, "Canyon", "Blue")
        assertEquals("Canyon", e.playerName)
        assertEquals("Blue", e.type)
        assertEquals(Side.HOME, e.side)
    }

    @Test
    fun gameEnd_holdsAllFields() {
        val e = MatchEvent.GameEnd(1, Side.HOME, 32, 15 to 8)
        assertEquals(1, e.gameNumber)
        assertEquals(Side.HOME, e.winnerSide)
        assertEquals(32, e.durationMinutes)
        assertEquals(15 to 8, e.finalKills)
    }

    @Test
    fun seriesEnd_holdsWinnerAndScore() {
        val e = MatchEvent.SeriesEnd(Side.AWAY, 1 to 2)
        assertEquals(Side.AWAY, e.winnerSide)
        assertEquals(1 to 2, e.mapScore)
    }

    @Test
    fun gameTick_holdsMinuteAndSecond() {
        val e = MatchEvent.GameTick(15, 30)
        assertEquals(15, e.minute)
        assertEquals(30, e.second)
    }

    @Test
    fun gameStart_holdsGameNumber() {
        val e = MatchEvent.GameStart(3)
        assertEquals(3, e.gameNumber)
    }

    @Test
    fun phaseAnnouncement_holdsText() {
        val e = MatchEvent.PhaseAnnouncement("Pick & Ban — Mapa 2")
        assertEquals("Pick & Ban — Mapa 2", e.text)
    }
}
