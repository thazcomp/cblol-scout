package com.cblol.scout

import com.cblol.scout.data.FinancialHealth
import com.cblol.scout.data.LoanOffer
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.BankService
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

/**
 * Testes do [BankService] — sistema bancário (empréstimos + saúde financeira).
 *
 * O serviço é JVM-puro: opera sobre [com.cblol.scout.data.GameState] sem
 * tocar repositórios ou Android. Todas as partes são determinísticas e
 * testáveis diretamente. Para datas, manipulamos `currentDate` e
 * `lastInstallmentDate` para controlar a janela de 7 dias da cobrança semanal.
 */
class BankServiceTest {

    // ── Saúde financeira ────────────────────────────────────────────────

    @Test
    fun financialHealth_healthy_aboveWarningThreshold() {
        val gs = makeGameState(budget = 1_000_000L)
        assertEquals(FinancialHealth.HEALTHY, BankService.financialHealth(gs))
        assertFalse(BankService.shouldWarn(gs))
    }

    @Test
    fun financialHealth_warning_betweenThresholds() {
        val gs = makeGameState(budget = 300_000L)
        assertEquals(FinancialHealth.WARNING, BankService.financialHealth(gs))
        assertTrue(BankService.shouldWarn(gs))
    }

    @Test
    fun financialHealth_critical_belowCriticalThreshold() {
        val gs = makeGameState(budget = 50_000L)
        assertEquals(FinancialHealth.CRITICAL, BankService.financialHealth(gs))
        assertTrue(BankService.shouldWarn(gs))
    }

    @Test
    fun financialHealth_critical_whenNegative() {
        val gs = makeGameState(budget = -100_000L)
        assertEquals(FinancialHealth.CRITICAL, BankService.financialHealth(gs))
    }

    @Test
    fun healthAdvice_isNonEmptyForAllStates() {
        listOf(1_000_000L, 300_000L, 0L).forEach { budget ->
            val gs = makeGameState(budget = budget)
            assertTrue(BankService.healthAdvice(gs).isNotBlank())
        }
    }

    // ── Limite de crédito ───────────────────────────────────────────────

    @Test
    fun creditLimit_scalesWithSponsorshipAndReputation() {
        // Reputação 50 → repFactor = 1.0. Limite = patrocínio * 20.
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 50
        val expected = 500_000L * GameConstants.Bank.CREDIT_LIMIT_WEEKS
        assertEquals(expected, BankService.creditLimit(gs))
    }

    @Test
    fun creditLimit_higherReputationGivesMoreCredit() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 0
        val lowRep = BankService.creditLimit(gs)
        gs.coachProfile.reputation = 100
        val highRep = BankService.creditLimit(gs)
        assertTrue("reputação alta deve gerar mais crédito", highRep > lowRep)
    }

    @Test
    fun creditLimit_respectsMinimumFloor() {
        // Patrocínio baixíssimo + reputação 0 → ainda assim respeita o piso.
        val gs = makeGameState(sponsorshipPerWeek = 1_000L)
        gs.coachProfile.reputation = 0
        assertTrue(BankService.creditLimit(gs) >= GameConstants.Bank.MIN_CREDIT_LIMIT)
    }

    @Test
    fun availableCredit_reducesByOutstandingDebt() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L, budget = 5_000_000L)
        gs.coachProfile.reputation = 50
        val limit = BankService.creditLimit(gs)
        val before = BankService.availableCredit(gs)
        assertEquals(limit, before)

        // Toma empréstimo e verifica que o crédito disponível cai.
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        val result = BankService.takeLoan(gs, offer)
        assertTrue(result is BankService.TakeResult.Ok)
        val after = BankService.availableCredit(gs)
        assertTrue("crédito disponível deve cair após tomar empréstimo", after < before)
    }

    // ── Cálculo de parcela ──────────────────────────────────────────────

    @Test
    fun installmentFor_isPrincipalPlusInterestDividedByWeeks_ceiled() {
        val offer = LoanOffer(
            id = "test", label = "Teste", emoji = "💸",
            principal = 1_000_000L, interestRate = 0.20, weeks = 10
        )
        // (1_000_000 * 1.2) / 10 = 120_000.
        assertEquals(120_000L, BankService.installmentFor(offer))
    }

    @Test
    fun installmentFor_roundsUpToNeverLoseCentavo() {
        val offer = LoanOffer(
            id = "test", label = "Teste", emoji = "💸",
            principal = 100_001L, interestRate = 0.10, weeks = 7
        )
        // 110_001.1 / 7 = 15_714.44... → ceil = 15_715.
        val expected = ceil(100_001L * 1.10 / 7).toLong()
        assertEquals(expected, BankService.installmentFor(offer))
    }

    // ── Contratar empréstimo ────────────────────────────────────────────

    @Test
    fun takeLoan_ok_creditsBudgetAndCreatesActiveLoan() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L, budget = 100_000L)
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }

        val result = BankService.takeLoan(gs, offer)
        assertTrue(result is BankService.TakeResult.Ok)

        // Orçamento creditado.
        assertEquals(100_000L + offer.principal, gs.budget)
        // Empréstimo ativo registrado.
        assertEquals(1, BankService.activeLoans(gs).size)
        val loan = BankService.activeLoans(gs).first()
        assertEquals(offer.principal, loan.principal)
        assertEquals(offer.weeks, loan.totalInstallments)
        assertEquals(BankService.installmentFor(offer), loan.installmentAmount)
    }

    @Test
    fun takeLoan_exceedsCredit_isRejected() {
        val gs = makeGameState(sponsorshipPerWeek = 50_000L)
        gs.coachProfile.reputation = 0
        // Cria oferta sintética bem maior que o limite mínimo.
        val limit = BankService.creditLimit(gs)
        val tooBig = LoanOffer(
            id = "huge", label = "Gigante", emoji = "🏦",
            principal = limit + 1_000_000L, interestRate = 0.10, weeks = 10
        )
        val result = BankService.takeLoan(gs, tooBig)
        assertTrue(result is BankService.TakeResult.ExceedsCredit)
        assertEquals(0, BankService.activeLoans(gs).size)
    }

    @Test
    fun takeLoan_lowReputation_isRejected() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 10
        val offer = LoanOffer(
            id = "premium", label = "Premium", emoji = "🏦",
            principal = 200_000L, interestRate = 0.10, weeks = 10,
            minReputation = 70
        )
        assertEquals(BankService.TakeResult.LowReputation, BankService.takeLoan(gs, offer))
    }

    // ── Cobrança semanal ────────────────────────────────────────────────

    @Test
    fun chargeWeeklyInstallments_chargesEachLoanOnce() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, offer)

        val budgetBefore = gs.budget
        val expectedInstallment = BankService.activeLoans(gs).first().installmentAmount
        val result = BankService.chargeWeeklyInstallments(gs)

        assertEquals(expectedInstallment, result.totalCharged)
        assertEquals(budgetBefore - expectedInstallment, gs.budget)
        assertEquals(1, BankService.activeLoans(gs).first().installmentsPaid)
    }

    @Test
    fun chargeWeeklyInstallments_idempotentWithinSameWeek() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, offer)

        BankService.chargeWeeklyInstallments(gs)
        val budgetAfterFirst = gs.budget

        // Mesmo dia → não cobra de novo (diferença < 7 dias).
        BankService.chargeWeeklyInstallments(gs)
        assertEquals(budgetAfterFirst, gs.budget)

        // 5 dias depois → ainda não cobra.
        gs.currentDate = "2026-04-10"
        BankService.chargeWeeklyInstallments(gs)
        assertEquals(budgetAfterFirst, gs.budget)
    }

    @Test
    fun chargeWeeklyInstallments_chargesAgainAfter7Days() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, offer)

        BankService.chargeWeeklyInstallments(gs)
        val budgetAfterFirst = gs.budget

        gs.currentDate = "2026-04-12"  // +7 dias
        val expectedInstallment = BankService.activeLoans(gs).first().installmentAmount
        BankService.chargeWeeklyInstallments(gs)
        assertEquals(budgetAfterFirst - expectedInstallment, gs.budget)
        assertEquals(2, BankService.activeLoans(gs).first().installmentsPaid)
    }

    @Test
    fun chargeWeeklyInstallments_removesPaidOffLoans() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = LoanOffer(
            id = "loan_short", label = "Curto", emoji = "💸",
            principal = 100_000L, interestRate = 0.10, weeks = 2
        )
        BankService.takeLoan(gs, offer)

        // Primeira parcela.
        BankService.chargeWeeklyInstallments(gs)
        assertEquals(1, BankService.activeLoans(gs).size)

        // Segunda parcela: quita o empréstimo.
        gs.currentDate = "2026-04-12"
        val result = BankService.chargeWeeklyInstallments(gs)
        assertEquals(1, result.loansPaidOff.size)
        assertEquals(0, BankService.activeLoans(gs).size)
    }

    @Test
    fun chargeWeeklyInstallments_doesNothingWhenNoActiveLoans() {
        val gs = makeGameState(budget = 5_000_000L)
        val budgetBefore = gs.budget
        val result = BankService.chargeWeeklyInstallments(gs)
        assertEquals(0L, result.totalCharged)
        assertEquals(budgetBefore, gs.budget)
    }

    // ── Quitação antecipada ─────────────────────────────────────────────

    @Test
    fun payOffEarly_ok_removesLoanAndDeductsBalance() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, offer)
        val loan = BankService.activeLoans(gs).first()
        val balance = loan.outstandingBalance
        val budgetBefore = gs.budget

        val result = BankService.payOffEarly(gs, loan.id)
        assertTrue(result is BankService.PayoffResult.Ok)
        assertEquals(balance, (result as BankService.PayoffResult.Ok).amountPaid)
        assertEquals(budgetBefore - balance, gs.budget)
        assertEquals(0, BankService.activeLoans(gs).size)
    }

    @Test
    fun payOffEarly_insufficientFunds_isRejected() {
        val gs = makeGameState(
            sponsorshipPerWeek = 500_000L, budget = 5_000_000L,
            currentDate = "2026-04-05"
        )
        gs.coachProfile.reputation = 50
        val offer = BankService.offersFor(gs).first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, offer)
        val loan = BankService.activeLoans(gs).first()

        // Esvazia o caixa para forçar fundos insuficientes.
        gs.budget = 1_000L
        val result = BankService.payOffEarly(gs, loan.id)
        assertTrue(result is BankService.PayoffResult.InsufficientFunds)
        // Empréstimo continua ativo.
        assertEquals(1, BankService.activeLoans(gs).size)
    }

    @Test
    fun payOffEarly_notFound_returnsNotFound() {
        val gs = makeGameState(budget = 5_000_000L)
        assertEquals(
            BankService.PayoffResult.NotFound,
            BankService.payOffEarly(gs, "id_inexistente")
        )
    }

    // ── Catálogo ────────────────────────────────────────────────────────

    @Test
    fun offersFor_filtersByAvailableCredit() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 50

        // Sem dívida: todas as linhas (com reputação ok) aparecem.
        val initial = BankService.offersFor(gs)
        assertTrue(initial.isNotEmpty())

        // Toma o maior empréstimo possível para esgotar quase todo o crédito.
        val biggest = initial.maxByOrNull { it.principal }!!
        BankService.takeLoan(gs, biggest)

        // Agora o crédito disponível deve ser menor → catálogo encolhe.
        val afterBig = BankService.offersFor(gs)
        assertTrue("catálogo deve diminuir após esgotar crédito",
            afterBig.size <= initial.size)
        // E nenhuma das ofertas restantes deve exceder o crédito disponível.
        val available = BankService.availableCredit(gs)
        afterBig.forEach { assertTrue(it.principal <= available) }
    }

    @Test
    fun offersFor_filtersByReputation() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 30  // reputação baixa
        val offers = BankService.offersFor(gs)
        // Linhas com minReputation > 30 (investment 45, mega 65) devem sumir.
        assertTrue(offers.none { it.minReputation > 30 })
    }

    @Test
    fun recommendedLoanFor_picksSmallestLineThatCovers() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L)
        gs.coachProfile.reputation = 50
        val rec = BankService.recommendedLoanFor(gs, amountNeeded = 200_000L)
        assertNotNull(rec)
        assertTrue("linha sugerida deve cobrir o valor necessário",
            rec!!.principal >= 200_000L)
        // E não deve haver linha menor (que ainda cubra) que a sugerida.
        val betterCandidate = BankService.offersFor(gs)
            .filter { it.principal >= 200_000L && it.principal < rec.principal }
        assertTrue(betterCandidate.isEmpty())
    }

    @Test
    fun recommendedLoanFor_zeroOrNegativeNeed_returnsNull() {
        val gs = makeGameState()
        assertNull(BankService.recommendedLoanFor(gs, 0L))
        assertNull(BankService.recommendedLoanFor(gs, -100L))
    }

    // ── Migração / acesso null-safe ─────────────────────────────────────

    @Test
    fun bankOf_createsStateIfNull() {
        val gs = makeGameState()
        gs.bank = null
        assertNotNull(BankService.bankOf(gs))
        assertNotNull(gs.bank)
        assertEquals(0, BankService.activeLoans(gs).size)
    }

    @Test
    fun totalDebt_andWeeklyInstallments_sumAcrossLoans() {
        val gs = makeGameState(sponsorshipPerWeek = 500_000L, budget = 10_000_000L)
        gs.coachProfile.reputation = 70

        val offers = BankService.offersFor(gs)
        val a = offers.first { it.id == "loan_micro" }
        val b = offers.first { it.id == "loan_emergency" }
        BankService.takeLoan(gs, a)
        BankService.takeLoan(gs, b)

        val expectedDebt = BankService.activeLoans(gs).sumOf { it.outstandingBalance }
        val expectedWeekly = BankService.activeLoans(gs).sumOf { it.installmentAmount }
        assertEquals(expectedDebt, BankService.totalDebt(gs))
        assertEquals(expectedWeekly, BankService.weeklyInstallmentsTotal(gs))
    }
}
