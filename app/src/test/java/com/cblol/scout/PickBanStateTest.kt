package com.cblol.scout

import com.cblol.scout.data.Champion
import com.cblol.scout.data.PickBanPhase
import com.cblol.scout.data.PickBanState
import org.junit.Assert.*
import org.junit.Test

class PickBanStateTest {

    private fun champ(id: String, role: String = "MID") =
        Champion(id, id, id.take(8), listOf(role), role)

    private fun emptyState(playerIsBlue: Boolean = true) = PickBanState(
        currentTurnIndex = 0,
        blueBans         = mutableListOf(),
        redBans          = mutableListOf(),
        bluePicks        = mutableListOf(),
        redPicks         = mutableListOf(),
        playerIsBlue     = playerIsBlue,
        usedChampions    = mutableSetOf()
    )

    @Test
    fun initialState_allListsAndSetsEmpty() {
        val s = emptyState()
        assertEquals(0, s.currentTurnIndex)
        assertTrue(s.blueBans.isEmpty())
        assertTrue(s.redBans.isEmpty())
        assertTrue(s.bluePicks.isEmpty())
        assertTrue(s.redPicks.isEmpty())
        assertTrue(s.usedChampions.isEmpty())
    }

    @Test
    fun addBlueBan_updatesListAndUsedSet() {
        val s = emptyState()
        val c = champ("Ahri")
        s.blueBans.add(c)
        s.usedChampions.add(c.id)
        assertEquals(1, s.blueBans.size)
        assertTrue("Ahri" in s.usedChampions)
    }

    @Test
    fun addRedBan_isIndependentOfBlueBan() {
        val s = emptyState()
        s.blueBans.add(champ("Ahri"))
        s.redBans.add(champ("Zed"))
        assertEquals(1, s.blueBans.size)
        assertEquals(1, s.redBans.size)
    }

    @Test
    fun addBluePick_andRedPick_areIndependent() {
        val s = emptyState()
        s.bluePicks.add(champ("Jinx", "ADC"))
        s.redPicks.add(champ("Thresh", "SUP"))
        assertEquals(1, s.bluePicks.size)
        assertEquals(1, s.redPicks.size)
    }

    @Test
    fun usedChampions_preventsReuse() {
        val s = emptyState()
        s.usedChampions.add("Ahri")
        assertTrue("Ahri" in s.usedChampions)
        assertFalse("Jinx" in s.usedChampions)
    }

    @Test
    fun currentTurnIndex_canIncrement() {
        val s = emptyState()
        s.currentTurnIndex++
        assertEquals(1, s.currentTurnIndex)
    }

    @Test
    fun playerIsBlue_trueAndFalse_preserved() {
        assertTrue(emptyState(true).playerIsBlue)
        assertFalse(emptyState(false).playerIsBlue)
    }
}
