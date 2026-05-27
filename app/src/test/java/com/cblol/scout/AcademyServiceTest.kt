package com.cblol.scout

import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AcademyTier
import com.cblol.scout.domain.usecase.AcademyService
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Testes do [AcademyService] — sistema de categoria de base (academia).
 *
 * O serviço é JVM-puro: opera sobre [com.cblol.scout.data.GameState] sem
 * Android. As partes determinísticas (avaliação, promoção, upgrade, custos)
 * são testadas diretamente. As partes probabilísticas (geração de prospects,
 * desenvolvimento diário, que usam `Random.Default`) são testadas por
 * invariantes (faixas válidas) e/ou avançando vários dias, em vez de valores
 * exatos.
 *
 * Para controlar quantos dias um tick avança, manipulamos `currentDate` e
 * `lastAcademyTickDate` diretamente — o serviço calcula `days` a partir deles.
 * NÃO precisa de StaticData (o serviço não toca repositórios).
 */
class AcademyServiceTest {

    // ── academyOf / inicialização ───────────────────────────────────────

    @Test
    fun academyOf_createsAcademyIfNull() {
        val gs = makeGameState()
        gs.academy = null
        val academy = AcademyService.academyOf(gs)
        assertNotNull(academy)
        assertEquals(AcademyTier.BASIC, academy.tier)
    }

    @Test
    fun initializeForNewCareer_createsInitialProspects() {
        val gs = makeGameState()
        AcademyService.initializeForNewCareer(gs)
        // BASIC: capacity 4 → leva inicial = max(4/2, 2) = 2.
        assertEquals(2, AcademyService.prospects(gs).size)
    }

    @Test
    fun initializeForNewCareer_isIdempotent() {
        val gs = makeGameState()
        AcademyService.initializeForNewCareer(gs)
        AcademyService.initializeForNewCareer(gs)
        assertEquals(2, AcademyService.prospects(gs).size)
    }

    // ── generateProspect: invariantes ───────────────────────────────────

    @Test
    fun generateProspect_respectsRanges() {
        val gs = makeGameState()
        repeat(50) {
            val p = AcademyService.generateProspect(gs, AcademyTier.BASIC)
            assertTrue("idade 16-19", p.idade in 16..19)
            assertTrue("overall 45-62", p.currentOverall in 45..62)
            assertTrue("potencial >= overall", p.potential >= p.currentOverall)
            assertTrue("potencial <= maxPotential do tier",
                p.potential <= AcademyTier.BASIC.maxPotential)
            assertTrue("role válida", p.role in listOf("TOP", "JNG", "MID", "ADC", "SUP"))
            assertFalse("começa não avaliado", p.evaluated)
        }
    }

    @Test
    fun generateProspect_eliteHasHigherCeiling() {
        val gs = makeGameState()
        // Em muitas amostras, ELITE deve produzir ao menos um potencial acima do
        // teto do BASIC (78). É probabilístico, mas com 80 amostras a chance de
        // falhar é ínfima.
        val maxElite = (1..80).maxOf {
            AcademyService.generateProspect(gs, AcademyTier.ELITE).potential
        }
        assertTrue("ELITE deve poder gerar potencial > 78 (foi $maxElite)", maxElite > 78)
    }

    @Test
    fun generateProspect_idsAreUnique() {
        val gs = makeGameState()
        val ids = (1..30).map { AcademyService.generateProspect(gs, AcademyTier.BASIC).id }
        assertEquals(ids.size, ids.distinct().size)
    }

    // ── hasFreeSlot / capacidade ────────────────────────────────────────

    @Test
    fun hasFreeSlot_falseWhenAtCapacity() {
        val gs = makeGameState()
        val academy = AcademyService.academyOf(gs)
        // BASIC capacity = 4. Enche.
        repeat(4) { academy.prospects.add(AcademyService.generateProspect(gs, AcademyTier.BASIC)) }
        assertFalse(AcademyService.hasFreeSlot(gs))
    }

    @Test
    fun hasFreeSlot_trueWhenBelowCapacity() {
        val gs = makeGameState()
        AcademyService.academyOf(gs).prospects.add(
            AcademyService.generateProspect(gs, AcademyTier.BASIC))
        assertTrue(AcademyService.hasFreeSlot(gs))
    }

    // ── Avaliação ───────────────────────────────────────────────────────

    @Test
    fun evaluateProspect_revealsAndCharges() {
        val gs = makeGameState(budget = 100_000L)
        val academy = AcademyService.academyOf(gs)
        val p = AcademyService.generateProspect(gs, AcademyTier.BASIC)
        academy.prospects.add(p)

        val result = AcademyService.evaluateProspect(gs, p.id)
        assertEquals(AcademyService.EvaluateResult.OK, result)
        assertTrue(AcademyService.prospectById(gs, p.id)!!.evaluated)
        assertEquals(100_000L - AcademyService.EVALUATION_COST, gs.budget)
    }

    @Test
    fun evaluateProspect_alreadyEvaluated() {
        val gs = makeGameState(budget = 100_000L)
        val academy = AcademyService.academyOf(gs)
        val p = AcademyService.generateProspect(gs, AcademyTier.BASIC).copy(evaluated = true)
        academy.prospects.add(p)
        assertEquals(AcademyService.EvaluateResult.ALREADY_EVALUATED,
            AcademyService.evaluateProspect(gs, p.id))
    }

    @Test
    fun evaluateProspect_insufficientFunds() {
        val gs = makeGameState(budget = 1_000L)
        val academy = AcademyService.academyOf(gs)
        val p = AcademyService.generateProspect(gs, AcademyTier.BASIC)
        academy.prospects.add(p)
        assertEquals(AcademyService.EvaluateResult.INSUFFICIENT_FUNDS,
            AcademyService.evaluateProspect(gs, p.id))
        assertEquals(1_000L, gs.budget)  // não cobrou
    }

    // ── Recrutamento manual ─────────────────────────────────────────────

    @Test
    fun recruitManually_addsProspectAndCharges() {
        val gs = makeGameState(budget = 100_000L)
        val (result, prospect) = AcademyService.recruitManually(gs)
        assertEquals(AcademyService.RecruitResult.OK, result)
        assertNotNull(prospect)
        assertEquals(1, AcademyService.prospects(gs).size)
        assertEquals(100_000L - AcademyService.MANUAL_RECRUIT_COST, gs.budget)
    }

    @Test
    fun recruitManually_capacityFull() {
        val gs = makeGameState(budget = 10_000_000L)
        val academy = AcademyService.academyOf(gs)
        repeat(4) { academy.prospects.add(AcademyService.generateProspect(gs, AcademyTier.BASIC)) }
        val (result, prospect) = AcademyService.recruitManually(gs)
        assertEquals(AcademyService.RecruitResult.CAPACITY_FULL, result)
        assertNull(prospect)
    }

    @Test
    fun recruitManually_insufficientFunds() {
        val gs = makeGameState(budget = 1_000L)
        val (result, _) = AcademyService.recruitManually(gs)
        assertEquals(AcademyService.RecruitResult.INSUFFICIENT_FUNDS, result)
        assertEquals(1_000L, gs.budget)
    }

    // ── Liberação ───────────────────────────────────────────────────────

    @Test
    fun releaseProspect_removesFromAcademy() {
        val gs = makeGameState()
        val academy = AcademyService.academyOf(gs)
        val p = AcademyService.generateProspect(gs, AcademyTier.BASIC)
        academy.prospects.add(p)
        assertTrue(AcademyService.releaseProspect(gs, p.id))
        assertTrue(AcademyService.prospects(gs).isEmpty())
    }

    // ── Promoção ────────────────────────────────────────────────────────

    @Test
    fun promoteProspect_removesAndReturnsResult() {
        val gs = makeGameState()
        val academy = AcademyService.academyOf(gs)
        val p = AcademyService.generateProspect(gs, AcademyTier.BASIC)
        academy.prospects.add(p)

        val result = AcademyService.promoteProspect(gs, p.id)
        assertNotNull(result)
        assertEquals(p.id, result!!.prospect.id)
        assertTrue(result.suggestedSalary > 0)
        // Saiu da base.
        assertTrue(AcademyService.prospects(gs).isEmpty())
    }

    @Test
    fun promoteProspect_notFoundReturnsNull() {
        val gs = makeGameState()
        assertNull(AcademyService.promoteProspect(gs, "inexistente"))
    }

    @Test
    fun suggestedSalaryFor_scalesWithOverall() {
        val low  = AcademyService.suggestedSalaryFor(makeProspect(overall = 50))
        val mid  = AcademyService.suggestedSalaryFor(makeProspect(overall = 62))
        val high = AcademyService.suggestedSalaryFor(makeProspect(overall = 78))
        assertTrue("salário cresce com overall", low < mid && mid < high)
    }

    // ── Upgrade de tier ─────────────────────────────────────────────────

    @Test
    fun upgrade_okWhenFundsAndReputation() {
        val gs = makeGameState(budget = 1_000_000L)
        gs.coachProfile.reputation = 60  // >= PRO.minReputation (55)
        assertEquals(AcademyService.UpgradeResult.OK, AcademyService.upgrade(gs))
        assertEquals(AcademyTier.PRO, AcademyService.tier(gs))
        assertEquals(1_000_000L - AcademyTier.PRO.upgradeCost, gs.budget)
    }

    @Test
    fun upgrade_lowReputation() {
        val gs = makeGameState(budget = 1_000_000L)
        gs.coachProfile.reputation = 10
        assertEquals(AcademyService.UpgradeResult.LOW_REPUTATION, AcademyService.upgrade(gs))
        assertEquals(AcademyTier.BASIC, AcademyService.tier(gs))
    }

    @Test
    fun upgrade_insufficientFunds() {
        val gs = makeGameState(budget = 1_000L)
        gs.coachProfile.reputation = 99
        assertEquals(AcademyService.UpgradeResult.INSUFFICIENT_FUNDS, AcademyService.upgrade(gs))
        assertEquals(AcademyTier.BASIC, AcademyService.tier(gs))
    }

    @Test
    fun upgrade_alreadyMaxAtElite() {
        val gs = makeGameState(budget = 100_000_000L)
        gs.coachProfile.reputation = 99
        AcademyService.academyOf(gs).tier = AcademyTier.ELITE
        assertEquals(AcademyService.UpgradeResult.ALREADY_MAX, AcademyService.upgrade(gs))
    }

    @Test
    fun nextTier_chainsCorrectly() {
        assertEquals(AcademyTier.PRO, AcademyService.nextTier(AcademyTier.BASIC))
        assertEquals(AcademyTier.ELITE, AcademyService.nextTier(AcademyTier.PRO))
        assertNull(AcademyService.nextTier(AcademyTier.ELITE))
    }

    // ── tickDaily: desenvolvimento ──────────────────────────────────────

    @Test
    fun tickDaily_isIdempotentForSameDay() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val academy = AcademyService.academyOf(gs)
        academy.prospects.add(makeProspect(overall = 50, potential = 80))
        academy.lastRecruitDate = "2026-04-01"

        gs.currentDate = "2026-04-15"
        AcademyService.tickDaily(gs)
        val afterFirst = AcademyService.prospects(gs).first().currentOverall

        // Mesmo dia de novo: days == 0, não deve desenvolver mais.
        AcademyService.tickDaily(gs)
        assertEquals(afterFirst, AcademyService.prospects(gs).first().currentOverall)
    }

    @Test
    fun tickDaily_developsProspectTowardPotential() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val academy = AcademyService.academyOf(gs)
        academy.prospects.add(makeProspect(overall = 50, potential = 85, age = 16))
        academy.lastRecruitDate = "2026-04-01"
        gs.lastAcademyTickDate = "2026-04-01"

        // Avança ~3 meses em saltos de 7 dias (cada salto = 1 passo de dev).
        var date = LocalDate.parse(gs.currentDate)
        repeat(13) {
            date = date.plusDays(7)
            gs.currentDate = date.toString()
            AcademyService.tickDaily(gs)
        }
        val overall = AcademyService.prospects(gs).first().currentOverall
        assertTrue("prospect deve ter evoluído de 50 (foi para $overall)", overall > 50)
        assertTrue("não pode ultrapassar o potencial", overall <= 85)
    }

    @Test
    fun tickDaily_doesNotExceedPotential() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val academy = AcademyService.academyOf(gs)
        academy.prospects.add(makeProspect(overall = 79, potential = 80, age = 16))
        academy.lastRecruitDate = "2026-04-01"
        gs.lastAcademyTickDate = "2026-04-01"

        var date = LocalDate.parse(gs.currentDate)
        repeat(20) {
            date = date.plusDays(7)
            gs.currentDate = date.toString()
            AcademyService.tickDaily(gs)
        }
        assertEquals(80, AcademyService.prospects(gs).first().currentOverall)
    }

    @Test
    fun tickDaily_reportsReadyMilestone() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val academy = AcademyService.academyOf(gs)
        // Quase pronto: precisa chegar a potential-2 = 83.
        academy.prospects.add(makeProspect(overall = 80, potential = 85, age = 16))
        academy.lastRecruitDate = "2026-04-01"
        gs.lastAcademyTickDate = "2026-04-01"

        var ready = false
        var date = LocalDate.parse(gs.currentDate)
        repeat(20) {
            date = date.plusDays(7)
            gs.currentDate = date.toString()
            val result = AcademyService.tickDaily(gs)
            if (result.readyNow.isNotEmpty()) ready = true
        }
        assertTrue("deveria reportar prospect pronto em algum tick", ready)
    }

    @Test
    fun tickDaily_recruitsAfterInterval() {
        val gs = makeGameState(currentDate = "2026-04-01")
        val academy = AcademyService.academyOf(gs)
        academy.lastRecruitDate = "2026-04-01"  // recrutamento há 0 dias
        gs.lastAcademyTickDate = "2026-04-01"

        // Avança 31 dias (> RECRUIT_INTERVAL_DAYS = 30) com a base vazia.
        gs.currentDate = "2026-05-02"
        val result = AcademyService.tickDaily(gs)
        assertTrue("deveria recrutar um talento após o intervalo",
            result.recruited.isNotEmpty() || AcademyService.prospects(gs).isNotEmpty())
    }

    // ── Custo de manutenção ─────────────────────────────────────────────

    @Test
    fun weeklyMaintenanceCost_matchesTier() {
        val gs = makeGameState()
        assertEquals(AcademyTier.BASIC.weeklyCost, AcademyService.weeklyMaintenanceCost(gs))
        AcademyService.academyOf(gs).tier = AcademyTier.ELITE
        assertEquals(AcademyTier.ELITE.weeklyCost, AcademyService.weeklyMaintenanceCost(gs))
    }

    // ── AcademyProspect: helpers ────────────────────────────────────────

    @Test
    fun prospect_developmentPercent_isRatio() {
        assertEquals(50, makeProspect(overall = 40, potential = 80).developmentPercent())
        assertEquals(100, makeProspect(overall = 80, potential = 80).developmentPercent())
    }

    @Test
    fun prospect_isReady_whenNearPotential() {
        assertTrue(makeProspect(overall = 83, potential = 85).isReady())   // 85-2
        assertFalse(makeProspect(overall = 70, potential = 85).isReady())
    }

    @Test
    fun prospect_potentialBand_thresholds() {
        assertEquals("Excepcional", makeProspect(potential = 88).potentialBand())
        assertEquals("Alto", makeProspect(potential = 78).potentialBand())
        assertEquals("Promissor", makeProspect(potential = 68).potentialBand())
        assertEquals("Mediano", makeProspect(potential = 58).potentialBand())
        assertEquals("Limitado", makeProspect(potential = 50).potentialBand())
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun makeProspect(
        overall: Int = 55,
        potential: Int = 75,
        age: Int = 17
    ) = AcademyProspect(
        id = "prospect_test_${overall}_${potential}_$age",
        nome = "Teste",
        nomeReal = "Jogador Teste",
        role = "MID",
        idade = age,
        currentOverall = overall,
        potential = potential
    )
}
