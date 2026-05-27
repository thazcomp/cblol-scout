package com.cblol.scout.domain.usecase

import com.cblol.scout.data.BankLoan
import com.cblol.scout.data.BankState
import com.cblol.scout.data.FinancialHealth
import com.cblol.scout.data.GameState
import com.cblol.scout.data.LoanOffer
import com.cblol.scout.domain.GameConstants
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Sistema **bancário** — empréstimos emergenciais + saúde financeira.
 *
 * É a rede de segurança do gerente contra o orçamento zerar/ficar negativo.
 * Oferece linhas de crédito ([LoanOffer]) que entregam dinheiro à vista e são
 * pagas em **parcelas semanais com juros**, descontadas todo domingo pelo
 * [com.cblol.scout.game.GameEngine].
 *
 * **Auxílios anti-falência (o que o sistema oferece ao jogador):**
 *  1. [financialHealth] — classifica o caixa em 🟢/🟡/🔴 para o aviso visual.
 *  2. [shouldWarn] — sinaliza quando o Hub deve alertar sobre caixa baixo.
 *  3. [creditLimit] / [availableCredit] — teto de endividamento responsável,
 *     proporcional ao patrocínio e à reputação, para o jogador não cavar um
 *     buraco impagável.
 *  4. [offersFor] — linhas de crédito adequadas ao limite disponível.
 *  5. [recommendedLoanFor] — sugere a menor linha que cobre o rombo atual.
 *
 * **Ciclo de um empréstimo:**
 *  - [takeLoan]: credita o principal no orçamento e cria um [BankLoan] com
 *    parcelas calculadas (principal × (1+juros) ÷ semanas).
 *  - [chargeWeeklyInstallments]: chamado pelo GameEngine todo domingo; desconta
 *    uma parcela de cada empréstimo ativo e remove os quitados.
 *  - [payOffEarly]: quita o saldo devedor de uma vez (livra das parcelas
 *    futuras).
 *
 * **SOLID:**
 *  - **SRP**: cada função tem um papel (classificar saúde, ofertar, cobrar...).
 *  - **OCP**: novas linhas de crédito entram em [CATALOG] sem mudar a lógica.
 *  - **DIP**: JVM-puro; opera sobre [GameState]. Sem Android, 100% testável.
 */
object BankService {

    // ── Acesso / migração ───────────────────────────────────────────────

    /** Garante a existência (não-nula) do estado bancário. */
    fun bankOf(state: GameState): BankState {
        var b = state.bank
        if (b == null) {
            b = BankState()
            state.bank = b
        }
        return b
    }

    /** Empréstimos ativos (lista não-nula). */
    fun activeLoans(state: GameState): List<BankLoan> = bankOf(state).loans

    /** Tem algum empréstimo ativo? */
    fun hasActiveLoans(state: GameState): Boolean = bankOf(state).loans.isNotEmpty()

    /** Saldo devedor total (soma do que falta pagar em todos os empréstimos). */
    fun totalDebt(state: GameState): Long =
        bankOf(state).loans.sumOf { it.outstandingBalance }

    /** Soma das parcelas semanais de todos os empréstimos ativos. */
    fun weeklyInstallmentsTotal(state: GameState): Long =
        bankOf(state).loans.sumOf { it.installmentAmount }

    // ── Saúde financeira ────────────────────────────────────────────────

    /**
     * Classifica a saúde financeira pelo orçamento atual:
     *  - 🔴 CRITICAL: orçamento < [GameConstants.Bank.CRITICAL_BUDGET] (ou negativo)
     *  - 🟡 WARNING:  orçamento < [GameConstants.Bank.WARNING_BUDGET]
     *  - 🟢 HEALTHY:  acima disso
     */
    fun financialHealth(state: GameState): FinancialHealth = when {
        state.budget < GameConstants.Bank.CRITICAL_BUDGET -> FinancialHealth.CRITICAL
        state.budget < GameConstants.Bank.WARNING_BUDGET  -> FinancialHealth.WARNING
        else                                              -> FinancialHealth.HEALTHY
    }

    /**
     * O Hub deve exibir aviso de caixa baixo? True quando a saúde NÃO está
     * saudável (amarelo ou vermelho).
     */
    fun shouldWarn(state: GameState): Boolean =
        financialHealth(state) != FinancialHealth.HEALTHY

    /**
     * Mensagem curta de orientação conforme a saúde financeira, para o aviso
     * visual. Em PT-BR, acionável.
     */
    fun healthAdvice(state: GameState): String = when (financialHealth(state)) {
        FinancialHealth.CRITICAL ->
            "Caixa crítico! Considere vender jogadores, cortar custos ou pegar um empréstimo no Banco."
        FinancialHealth.WARNING ->
            "Caixa apertado. Acompanhe a folha salarial e evite gastos grandes agora."
        FinancialHealth.HEALTHY ->
            "Finanças saudáveis."
    }

    // ── Limite de crédito ───────────────────────────────────────────────

    /**
     * Teto de endividamento total do time. Proporcional ao patrocínio semanal
     * (capacidade de pagar parcelas) com um piso de segurança e um bônus por
     * reputação do técnico (bancos confiam mais em quem tem nome).
     */
    fun creditLimit(state: GameState): Long {
        val base = state.sponsorshipPerWeek * GameConstants.Bank.CREDIT_LIMIT_WEEKS
        // Reputação 0-100 vira multiplicador 0.5x..1.5x.
        val repFactor = 0.5 + (state.coachProfile.reputation.coerceIn(0, 100) / 100.0)
        val limit = (base * repFactor).roundToLong()
        return maxOf(limit, GameConstants.Bank.MIN_CREDIT_LIMIT)
    }

    /**
     * Crédito ainda disponível = limite − saldo devedor atual. Nunca negativo.
     */
    fun availableCredit(state: GameState): Long =
        (creditLimit(state) - totalDebt(state)).coerceAtLeast(0L)

    // ── Catálogo de linhas de crédito ───────────────────────────────────

    /**
     * Linhas de crédito disponíveis para o gerente, FILTRADAS pelo crédito
     * disponível (não oferece o que ele não pode tomar) e pela reputação mínima.
     *
     * O catálogo é gerado dinamicamente a partir do limite de crédito, para
     * sempre fazer sentido com o tamanho do time — um time tier S vê linhas
     * maiores que um tier B.
     */
    fun offersFor(state: GameState): List<LoanOffer> {
        val available = availableCredit(state)
        val reputation = state.coachProfile.reputation
        return buildCatalog(state).filter {
            it.principal <= available && reputation >= it.minReputation
        }
    }

    /**
     * Sugere a MENOR linha de crédito que cobre um rombo de [amountNeeded]
     * (ex: a folha que vai vencer). Retorna null se nenhuma linha disponível
     * cobre, ou se não há necessidade (amountNeeded <= 0).
     */
    fun recommendedLoanFor(state: GameState, amountNeeded: Long): LoanOffer? {
        if (amountNeeded <= 0) return null
        return offersFor(state)
            .filter { it.principal >= amountNeeded }
            .minByOrNull { it.principal }
    }

    /**
     * Constrói o catálogo de linhas escalonado pelo limite de crédito do time.
     * Cada linha tem juros e prazo coerentes: valores maiores têm juros um
     * pouco maiores (risco) e prazos mais longos.
     */
    private fun buildCatalog(state: GameState): List<LoanOffer> {
        val limit = creditLimit(state)
        // Quatro degraus proporcionais ao limite: 15%, 35%, 65%, 100%.
        fun round50k(v: Long): Long = ((v / 50_000.0).roundToLong() * 50_000L).coerceAtLeast(50_000L)
        return listOf(
            LoanOffer(
                id = "loan_micro",
                label = "Adiantamento Rápido",
                emoji = "💸",
                principal = round50k((limit * 0.15).roundToLong()),
                interestRate = 0.08,
                weeks = 6,
                minReputation = 0,
                description = "Pequeno aporte para fechar a semana. Juros baixos, prazo curto."
            ),
            LoanOffer(
                id = "loan_emergency",
                label = "Crédito Emergencial",
                emoji = "🆘",
                principal = round50k((limit * 0.35).roundToLong()),
                interestRate = 0.15,
                weeks = 10,
                minReputation = 0,
                description = "Para cobrir a folha quando o caixa aperta de verdade."
            ),
            LoanOffer(
                id = "loan_investment",
                label = "Linha de Investimento",
                emoji = "📈",
                principal = round50k((limit * 0.65).roundToLong()),
                interestRate = 0.22,
                weeks = 16,
                minReputation = 45,
                description = "Capital para reforçar o elenco no mercado. Juros médios."
            ),
            LoanOffer(
                id = "loan_mega",
                label = "Crédito Estrutural",
                emoji = "🏦",
                principal = round50k(limit),
                interestRate = 0.30,
                weeks = 24,
                minReputation = 65,
                description = "Grande aporte para projetos de longo prazo. Juros altos, prazo longo."
            )
        )
    }

    // ── Contratar empréstimo ────────────────────────────────────────────

    /** Resultado de uma tentativa de tomar empréstimo. */
    sealed class TakeResult {
        data class Ok(val loan: BankLoan) : TakeResult()
        /** Excede o crédito disponível. [available] é quanto ainda cabe. */
        data class ExceedsCredit(val available: Long) : TakeResult()
        object LowReputation : TakeResult()
    }

    /**
     * Calcula o valor da parcela semanal de uma linha: principal com juros,
     * dividido pelo número de semanas, arredondado para cima (banco não perde
     * centavo).
     */
    fun installmentFor(offer: LoanOffer): Long {
        val total = offer.principal * (1.0 + offer.interestRate)
        return ceil(total / offer.weeks).toLong()
    }

    /**
     * Contrata uma linha de crédito: credita o principal no orçamento e
     * registra o [BankLoan] ativo. Valida crédito disponível e reputação.
     */
    fun takeLoan(state: GameState, offer: LoanOffer): TakeResult {
        if (state.coachProfile.reputation < offer.minReputation) return TakeResult.LowReputation
        val available = availableCredit(state)
        if (offer.principal > available) return TakeResult.ExceedsCredit(available)

        val bank = bankOf(state)
        val installment = installmentFor(offer)
        val loan = BankLoan(
            id = "loan_${UUID.randomUUID()}",
            label = offer.label,
            principal = offer.principal,
            interestRate = offer.interestRate,
            totalInstallments = offer.weeks,
            installmentsPaid = 0,
            installmentAmount = installment,
            takenOn = state.currentDate
        )
        bank.loans.add(loan)
        bank.totalBorrowedEver += offer.principal
        state.budget += offer.principal
        return TakeResult.Ok(loan)
    }

    // ── Cobrança semanal de parcelas ────────────────────────────────────

    /** Resultado da cobrança semanal, para o GameEngine logar/relatar. */
    data class InstallmentResult(
        val totalCharged: Long = 0L,
        val interestPortion: Long = 0L,
        val loansPaidOff: List<BankLoan> = emptyList()
    )

    /**
     * Desconta UMA parcela semanal de cada empréstimo ativo do orçamento.
     * Idempotente por semana via [BankState.lastInstallmentDate]: só cobra se
     * passou ao menos 7 dias desde a última cobrança (ou se nunca cobrou).
     *
     * Empréstimos que chegam à última parcela são marcados como quitados e
     * removidos da lista ativa.
     *
     * Chamado pelo [com.cblol.scout.game.GameEngine] no bloco semanal (domingo),
     * junto das demais despesas. NÃO impede o orçamento de ficar negativo — a
     * dívida é dívida; o objetivo é justamente pressionar o jogador a se
     * organizar (mas o limite de crédito evita que isso vire impagável).
     */
    fun chargeWeeklyInstallments(state: GameState): InstallmentResult {
        val bank = bankOf(state)
        if (bank.loans.isEmpty()) return InstallmentResult()

        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return InstallmentResult()
        val last = bank.lastInstallmentDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        if (last != null && ChronoUnit.DAYS.between(last, today) < 7) {
            return InstallmentResult()
        }
        bank.lastInstallmentDate = state.currentDate

        var totalCharged = 0L
        var interestPortion = 0L
        val paidOff = mutableListOf<BankLoan>()

        bank.loans.forEach { loan ->
            val amount = loan.installmentAmount
            state.budget -= amount
            totalCharged += amount
            loan.installmentsPaid += 1

            // Fração de juros desta parcela (proporcional ao total de juros).
            val totalInterest = loan.totalRepayable - loan.principal
            interestPortion += (totalInterest.toDouble() / loan.totalInstallments).roundToLong()

            if (loan.isPaidOff) paidOff += loan
        }

        bank.totalInterestPaid += interestPortion
        bank.loans.removeAll { it.isPaidOff }

        return InstallmentResult(
            totalCharged = totalCharged,
            interestPortion = interestPortion,
            loansPaidOff = paidOff
        )
    }

    // ── Quitação antecipada ─────────────────────────────────────────────

    /** Resultado de uma tentativa de quitar antecipadamente. */
    sealed class PayoffResult {
        data class Ok(val amountPaid: Long) : PayoffResult()
        data class InsufficientFunds(val needed: Long) : PayoffResult()
        object NotFound : PayoffResult()
    }

    /**
     * Quita antecipadamente o saldo devedor de um empréstimo, removendo-o da
     * lista ativa. Cobra o [BankLoan.outstandingBalance] do orçamento de uma
     * vez. Exige saldo suficiente (não deixa o jogador zerar o caixa numa
     * quitação que não consegue pagar).
     */
    fun payOffEarly(state: GameState, loanId: String): PayoffResult {
        val bank = bankOf(state)
        val loan = bank.loans.find { it.id == loanId } ?: return PayoffResult.NotFound
        val balance = loan.outstandingBalance
        if (state.budget < balance) return PayoffResult.InsufficientFunds(balance)

        state.budget -= balance
        bank.loans.removeAll { it.id == loanId }
        return PayoffResult.Ok(balance)
    }
}
