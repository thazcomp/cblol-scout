package com.cblol.scout

import com.cblol.scout.data.PickBanPlan
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PickBanPlanTest {

    private val gson = Gson()

    @Test
    fun gson_roundTrip_preservesAllFields() {
        val plan = PickBanPlan(
            mapNumber = 1,
            bluePicks = listOf("Ahri", "Jinx"),
            redPicks  = listOf("Thresh", "Azir"),
            blueBans  = listOf("Aatrox", "Camille"),
            redBans   = listOf("Zed", "Yasuo")
        )
        val back = gson.fromJson(gson.toJson(plan), PickBanPlan::class.java)
        assertEquals(1,                           back.mapNumber)
        assertEquals(listOf("Ahri", "Jinx"),      back.bluePicks)
        assertEquals(listOf("Thresh", "Azir"),    back.redPicks)
        assertEquals(listOf("Aatrox", "Camille"), back.blueBans)
        assertEquals(listOf("Zed", "Yasuo"),      back.redBans)
    }

    @Test
    fun emptyLists_roundTrip() {
        val plan = PickBanPlan(2, emptyList(), emptyList(), emptyList(), emptyList())
        val back = gson.fromJson(gson.toJson(plan), PickBanPlan::class.java)
        assertEquals(2, back.mapNumber)
        assertTrue(back.bluePicks.isEmpty())
        assertTrue(back.redPicks.isEmpty())
        assertTrue(back.blueBans.isEmpty())
        assertTrue(back.redBans.isEmpty())
    }

    @Test
    fun mapNumber3_isPreserved() {
        val plan = PickBanPlan(3, listOf("Viktor"), listOf("Orianna"),
            listOf("Zed"), listOf("Yasuo"))
        val back = gson.fromJson(gson.toJson(plan), PickBanPlan::class.java)
        assertEquals(3, back.mapNumber)
    }

    @Test
    fun allLists_areIndependent() {
        val plan = PickBanPlan(1,
            bluePicks = listOf("A"),
            redPicks  = listOf("B"),
            blueBans  = listOf("C"),
            redBans   = listOf("D")
        )
        assertNotEquals(plan.bluePicks, plan.redPicks)
        assertNotEquals(plan.blueBans,  plan.redBans)
    }
}
