package com.cblol.scout

import com.cblol.scout.data.PickBanMap
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.PickRecord
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PickBanSerializationTest {
    private val gson = Gson()

    @Test
    fun serializeAndDeserializePlan() {
        val map1 = PickBanMap(1, homeBans = listOf("Aatrox", "Ahri"), homePicks = listOf(PickRecord("p1","Player1","Ahri")))
        val plan = PickBanPlan(matchId = "m1", maps = listOf(map1))
        val json = gson.toJson(plan)
        val parsed = gson.fromJson(json, PickBanPlan::class.java)
        assertNotNull(parsed)
        assertEquals("m1", parsed.matchId)
        assertEquals(1, parsed.maps.size)
        assertEquals(2, parsed.maps[0].homeBans.size)
        assertEquals("Ahri", parsed.maps[0].homePicks[0].champion)
    }
}
