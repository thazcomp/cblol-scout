package com.cblol.scout

import com.cblol.scout.data.Division
import com.cblol.scout.domain.GameConstants
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes do enum [Division] e das constantes econômicas relacionadas ao modo
 * "começar na 2ª divisão". Não envolve Android — puro Kotlin.
 */
class DivisionTest {

    @Test
    fun division_hasTwoValues_firstAndSecond() {
        assertEquals(2, Division.values().size)
        assertEquals(Division.FIRST, Division.values()[0])
        assertEquals(Division.SECOND, Division.values()[1])
    }

    @Test
    fun division_labelsAreInPortuguese() {
        assertEquals("CBLOL", Division.FIRST.label)
        assertEquals("Circuito Desafiante", Division.SECOND.label)
        assertEquals("1ª", Division.FIRST.shortLabel)
        assertEquals("2ª", Division.SECOND.shortLabel)
    }

    @Test
    fun secondDivisionBudget_isLowerThanTierBFirstDiv() {
        // O modo "começar de baixo" precisa que o orçamento da 2ª div seja
        // estritamente menor que o tier mais baixo da 1ª — senão não faria
        // sentido como "desafio".
        assertTrue(
            "Orçamento da 2ª divisão deve ser menor que tier B da 1ª",
            GameConstants.Economy.STARTING_BUDGET_SECOND_DIV <
                GameConstants.Economy.STARTING_BUDGET_TIER_B
        )
    }

    @Test
    fun secondDivisionSponsorship_isLowerThanTierBFirstDiv() {
        assertTrue(
            "Patrocínio da 2ª divisão deve ser menor que tier B da 1ª",
            GameConstants.Economy.WEEKLY_SPONSOR_SECOND_DIV <
                GameConstants.Economy.WEEKLY_SPONSOR_TIER_B
        )
    }

    @Test
    fun secondDivisionEconomy_isPositive() {
        assertTrue(GameConstants.Economy.STARTING_BUDGET_SECOND_DIV > 0)
        assertTrue(GameConstants.Economy.WEEKLY_SPONSOR_SECOND_DIV > 0)
    }
}
