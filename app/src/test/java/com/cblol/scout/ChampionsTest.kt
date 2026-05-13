package com.cblol.scout

import com.cblol.scout.game.Champions
import org.junit.Assert.*
import org.junit.Test

class ChampionsTest {

    @Test
    fun allFlat_notEmpty() {
        assertTrue(Champions.ALL_FLAT.isNotEmpty())
    }

    @Test
    fun allFlat_noDuplicates() {
        assertEquals(Champions.ALL_FLAT.size, Champions.ALL_FLAT.distinct().size)
    }

    @Test
    fun allFlat_allNonBlank() {
        Champions.ALL_FLAT.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun forRole_TOP_returnsTopList() {
        assertEquals(Champions.TOP, Champions.forRole("TOP"))
    }

    @Test
    fun forRole_JNG_returnsJngList() {
        assertEquals(Champions.JNG, Champions.forRole("JNG"))
    }

    @Test
    fun forRole_MID_returnsMidList() {
        assertEquals(Champions.MID, Champions.forRole("MID"))
    }

    @Test
    fun forRole_ADC_returnsAdcList() {
        assertEquals(Champions.ADC, Champions.forRole("ADC"))
    }

    @Test
    fun forRole_SUP_returnsSupList() {
        assertEquals(Champions.SUP, Champions.forRole("SUP"))
    }

    @Test
    fun forRole_unknown_returnsAllFlat() {
        assertEquals(Champions.ALL_FLAT, Champions.forRole("UNKNOWN"))
    }

    @Test
    fun eachRoleList_notEmpty() {
        listOf("TOP", "JNG", "MID", "ADC", "SUP").forEach { role ->
            assertTrue("$role está vazio", Champions.forRole(role).isNotEmpty())
        }
    }

    @Test
    fun topList_doesNotContainJngChampion() {
        // Lee Sin é JNG, não deve estar em TOP
        assertFalse("Lee Sin" in Champions.TOP)
    }

    @Test
    fun allFlat_containsChampionsFromAllRoles() {
        assertTrue("Aatrox" in Champions.ALL_FLAT) // TOP
        assertTrue("Lee Sin" in Champions.ALL_FLAT) // JNG
        assertTrue("Ahri" in Champions.ALL_FLAT)   // MID
        assertTrue("Jinx" in Champions.ALL_FLAT)   // ADC
        assertTrue("Thresh" in Champions.ALL_FLAT) // SUP
    }
}
