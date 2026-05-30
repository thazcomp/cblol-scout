package com.cblol.scout.game.engine

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.domain.usecase.AcademyService
import com.cblol.scout.domain.usecase.BankService
import com.cblol.scout.domain.usecase.ScoutingService
import com.cblol.scout.domain.usecase.SponsorService
import com.cblol.scout.game.AdvanceReport
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import java.time.LocalDate

/**
 * Processa os eventos financeiros recorrentes da carreira:
 *  - **Domingo (dia 7 da semana ISO)**: patrocínio semanal + receitas dos
 *    contratos de patrocínio + manutenções (olheiros, academia) + parcelas
 *    de empréstimos bancários.
 *  - **Dia 1 do mês**: folha salarial.
 *  - **Sempre**: avalia a saúde financeira e marca aviso no relatório se
 *    necessário.
 *
 * Extraído do [com.cblol.scout.game.GameEngine] para isolar a "calculadora
 * financeira" do orquestrador de ticks. Mantém SRP — quem coordena o dia
 * não precisa conhecer as regras de juros, multas e periodicidade.
 */
internal object EconomyProcessor {

    /** Dia da semana usado para os pagamentos semanais (ISO: 7 = domingo). */
    private const val PAYDAY_OF_WEEK = 7

    /** Dia do mês usado para pagamento da folha. */
    private const val PAYROLL_DAY_OF_MONTH = 1

    /**
     * Aplica todos os efeitos financeiros do dia [date] sobre [gs], anotando
     * receitas/despesas em [report].
     */
    fun process(context: Context, gs: GameState, date: LocalDate, report: AdvanceReport) {
        if (date.dayOfWeek.value == PAYDAY_OF_WEEK) {
            applyWeeklyIncomeAndCosts(gs, report)
        }

        // Ofertas de patrocínio novas — independem do dia da semana, mas o
        // service mantém o intervalo interno (idempotente por data).
        SponsorService.generateOffersIfDue(gs)

        if (date.dayOfMonth == PAYROLL_DAY_OF_MONTH) {
            applyMonthlyPayroll(context, gs, report)
        }

        // Aviso de saúde financeira: se o caixa entrou em zona de atenção/crítica
        // após os movimentos do dia, marca o relatório para o Hub poder
        // alertar o gerente e sugerir o Banco.
        if (BankService.shouldWarn(gs)) {
            report.financialHealthWarning = BankService.financialHealth(gs)
        }
    }

    /**
     * Bloco de domingo: patrocínio base + contratos de patrocinadores + custos
     * de manutenção (olheiros, academia) + cobrança das parcelas de empréstimo.
     */
    private fun applyWeeklyIncomeAndCosts(gs: GameState, report: AdvanceReport) {
        // 1. Patrocínio fixo do tier do time
        gs.budget += gs.sponsorshipPerWeek
        report.income += gs.sponsorshipPerWeek
        GameRepository.log(
            "ECONOMY",
            "Patrocínio semanal recebido: R$ ${"%,d".format(gs.sponsorshipPerWeek)}"
        )

        // 2. Patrocinadores contratados (sponsors ativos)
        val sponsorResult = SponsorService.paySponsorsWeekly(gs)
        if (sponsorResult.totalPaid > 0) {
            report.income += sponsorResult.totalPaid
            GameRepository.log(
                "ECONOMY",
                "Patrocínios: R$ ${"%,d".format(sponsorResult.totalPaid)} recebidos (${gs.activeSponsors?.size ?: 0} ativos)"
            )
        }
        sponsorResult.expiredContracts.forEach { contract ->
            GameRepository.log(
                "ECONOMY",
                "Patrocínio com ${contract.sponsor.name} expirou. Total recebido: R$ ${"%,d".format(contract.totalReceived)}"
            )
        }

        // 3. Manutenção do departamento de olheiros
        val scoutingFee = ScoutingService.weeklyMaintenanceCost(gs)
        if (scoutingFee > 0) {
            gs.budget -= scoutingFee
            report.expense += scoutingFee
            GameRepository.log(
                "ECONOMY",
                "Manutenção do departamento de olheiros: R$ ${"%,d".format(scoutingFee)} (${ScoutingService.tier(gs).label})"
            )
        }

        // 4. Manutenção da categoria de base
        val academyFee = AcademyService.weeklyMaintenanceCost(gs)
        if (academyFee > 0) {
            gs.budget -= academyFee
            report.expense += academyFee
            GameRepository.log(
                "ECONOMY",
                "Manutenção da categoria de base: R$ ${"%,d".format(academyFee)} (${AcademyService.tier(gs).label})"
            )
        }

        // 5. Parcelas semanais de empréstimos bancários
        val loanResult = BankService.chargeWeeklyInstallments(gs)
        if (loanResult.totalCharged > 0) {
            report.expense += loanResult.totalCharged
            GameRepository.log(
                "ECONOMY",
                "Parcelas de empréstimo pagas: R$ ${"%,d".format(loanResult.totalCharged)}"
            )
        }
        loanResult.loansPaidOff.forEach { loan ->
            GameRepository.log(
                "ECONOMY",
                "✅ Empréstimo \"${loan.label}\" quitado!"
            )
        }
    }

    /** Folha salarial mensal (dia 1 de cada mês). */
    private fun applyMonthlyPayroll(context: Context, gs: GameState, report: AdvanceReport) {
        val total = GameEngine.totalMonthlyPayroll(context)
        gs.budget -= total
        report.expense += total
        GameRepository.log(
            "ECONOMY",
            "Folha salarial paga: R$ ${"%,d".format(total)}"
        )
    }
}
