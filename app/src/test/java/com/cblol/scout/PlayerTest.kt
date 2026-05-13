package com.cblol.scout

import com.cblol.scout.data.AtributosDeriv
import com.cblol.scout.data.Player
import org.junit.Assert.*
import org.junit.Test

class PlayerTest {

    @Test
    fun overallRating_uniformAttributes_returnsValue() {
        assertEquals(70, makePlayer("p", "MID", overall = 70).overallRating())
    }

    @Test
    fun overallRating_isIntegerAverageOf5Attributes() {
        val p = makePlayer("p", "MID").copy(
            atributos_derivados = AtributosDeriv(100, 80, 60, 40, 20)
        )
        assertEquals(60, p.overallRating()) // (100+80+60+40+20)/5
    }

    @Test
    fun overallRating_allZero_returnsZero() {
        val p = makePlayer("p", "MID").copy(
            atributos_derivados = AtributosDeriv(0, 0, 0, 0, 0)
        )
        assertEquals(0, p.overallRating())
    }

    @Test
    fun overallRating_allMax_returns100() {
        val p = makePlayer("p", "MID").copy(
            atributos_derivados = AtributosDeriv(100, 100, 100, 100, 100)
        )
        assertEquals(100, p.overallRating())
    }

    @Test
    fun copy_preservesAllFields() {
        val p = makePlayer("p1", "TOP", 80, true, "T1", 50_000L)
        val copy = p.copy(nome_jogo = "p2")
        assertEquals("p2", copy.nome_jogo)
        assertEquals(p.role, copy.role)
        assertEquals(p.overallRating(), copy.overallRating())
    }

    @Test
    fun titular_defaultTrue() {
        val p = makePlayer("p", "MID")
        assertTrue(p.titular)
    }

    @Test
    fun role_isPreserved() {
        assertEquals("ADC", makePlayer("p", "ADC").role)
    }
}
