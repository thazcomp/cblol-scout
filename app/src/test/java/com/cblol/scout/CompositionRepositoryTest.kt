package com.cblol.scout

import com.cblol.scout.util.CompositionRepository
import org.junit.Assert.*
import org.junit.Test

class CompositionRepositoryTest {

    @Test
    fun all_notEmpty() {
        assertTrue(CompositionRepository.all.isNotEmpty())
    }

    @Test
    fun all_allHaveUniqueIds() {
        val ids = CompositionRepository.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun all_allHaveAtLeast2RequiredPicks() {
        CompositionRepository.all.forEach {
            assertTrue("${it.id} tem menos de 2 required", it.requiredPicks.size >= 2)
        }
    }

    @Test
    fun all_allHaveAtLeast1KeyChampion() {
        CompositionRepository.all.forEach {
            assertTrue("${it.id} sem keyChampions", it.keyChampions.isNotEmpty())
        }
    }

    @Test
    fun all_bonusStrengthBetween1And15() {
        CompositionRepository.all.forEach {
            assertTrue("${it.id} bonus fora do range", it.bonusStrength in 1..15)
        }
    }

    @Test
    fun all_tierIsValid() {
        CompositionRepository.all.forEach {
            assertTrue("${it.id} tier inválido", it.tier in listOf("S", "A", "B"))
        }
    }

    @Test
    fun tierS_comps_exist() {
        assertTrue(CompositionRepository.all.any { it.tier == "S" })
    }

    // ── analyze ─────────────────────────────────────────────────────────

    @Test
    fun analyze_emptyPicks_returnsNoDetection() {
        val result = CompositionRepository.analyze(emptyList())
        assertNull(result.detected)
        assertEquals(0, result.bonusStrength)
    }

    @Test
    fun analyze_womboCombo_fullComp_returnsMaxBonus() {
        // Wombo Combo requer Malphite + Orianna + Amumu (minRequired = 3)
        val picks = listOf("Malphite", "MissFortune", "Orianna", "Amumu", "Jarvaniv")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals("wombo_combo", result.detected!!.id)
        assertEquals(14, result.bonusStrength) // comp completa = bonusStrength total
    }

    @Test
    fun analyze_womboCombo_partialComp_returnsHalfBonus() {
        // Só 3 de 5 → minRequired atingido → metade do bônus
        val picks = listOf("Malphite", "Orianna", "Amumu")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals(7, result.bonusStrength) // 14 / 2
    }

    @Test
    fun analyze_keyChampionBanned_returnsNoBonus() {
        val picks = listOf("Malphite", "MissFortune", "Orianna", "Amumu", "Jarvaniv")
        val bans  = listOf("Malphite") // ban do oponente quebra a comp
        val result = CompositionRepository.analyze(picks, bans)
        assertNull("Comp não deve ser detectada com key champion banado", result.detected)
        assertEquals(0, result.bonusStrength)
    }

    @Test
    fun analyze_protectTheCarry_detected() {
        val picks = listOf("Jinx", "Lulu", "Karma")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals("protect_the_carry", result.detected!!.id)
    }

    @Test
    fun analyze_pokeAndSiege_detected() {
        val picks = listOf("Jayce", "Ezreal", "Xerath")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals("poke_siege", result.detected!!.id)
    }

    @Test
    fun analyze_xayahRakan_exactPair_detected() {
        val picks = listOf("Xayah", "Rakan", "Malphite")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals("xayah_rakan", result.detected!!.id)
    }

    @Test
    fun analyze_caseInsensitive() {
        val picks = listOf("malphite", "orianna", "amumu")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
    }

    @Test
    fun analyze_yoneYasuo_detected() {
        val picks = listOf("Yasuo", "Yone", "Malphite")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        assertEquals("yone_yasuo", result.detected!!.id)
    }

    @Test
    fun analyze_zeriEnchanters_rakan_not_breaking() {
        // Rakan não é key champion de Zeri+Enchanters
        val picks = listOf("Zeri", "Lulu", "Karma")
        val bans  = listOf("Rakan")
        val result = CompositionRepository.analyze(picks, bans)
        assertNotNull("Rakan ban não deve quebrar Zeri+Enchanters", result.detected)
    }

    @Test
    fun analyze_multipleComps_returnsBestBonus() {
        // Picks que atendem parcialmente duas comps — deve retornar a de maior bônus
        val picks = listOf("Malphite", "Orianna", "Amumu", "Jinx", "Lulu")
        val result = CompositionRepository.analyze(picks)
        assertNotNull(result.detected)
        // Wombo (14) > protect (13/2=6 parcial) — deve retornar wombo
        assertEquals("wombo_combo", result.detected!!.id)
    }

    // ── compsNeutralizedBy ───────────────────────────────────────────────

    @Test
    fun compsNeutralizedBy_malphite_includesWomboCombo() {
        val neutralized = CompositionRepository.compsNeutralizedBy(listOf("Malphite"))
        assertTrue(neutralized.any { it.id == "wombo_combo" })
    }

    @Test
    fun compsNeutralizedBy_emptyBans_returnsEmpty() {
        assertTrue(CompositionRepository.compsNeutralizedBy(emptyList()).isEmpty())
    }

    @Test
    fun compsNeutralizedBy_lulu_includesProtectAndZeri() {
        val neutralized = CompositionRepository.compsNeutralizedBy(listOf("Lulu"))
        assertTrue(neutralized.any { it.id == "protect_the_carry" })
        assertTrue(neutralized.any { it.id == "zeri_enchanters" })
    }

    // ── suggestBans ──────────────────────────────────────────────────────

    @Test
    fun suggestBans_returnsAtLeast5Suggestions() {
        val suggestions = CompositionRepository.suggestBans()
        assertTrue(suggestions.size >= 5)
    }

    @Test
    fun suggestBans_noDuplicateChampions() {
        val champs = CompositionRepository.suggestBans().map { it.first }
        assertEquals(champs.size, champs.distinct().size)
    }

    @Test
    fun suggestBans_firstSuggestionIsTierS() {
        val first = CompositionRepository.suggestBans().first()
        // O primeiro ban sugerido deve ser de uma comp Tier S
        val tierSKeyChampions = CompositionRepository.all
            .filter { it.tier == "S" }
            .flatMap { it.keyChampions }
        assertTrue(
            "Primeira sugestão deveria ser de Tier S",
            first.first in tierSKeyChampions
        )
    }
}
