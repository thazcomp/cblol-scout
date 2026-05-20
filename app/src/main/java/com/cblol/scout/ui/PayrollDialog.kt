package com.cblol.scout.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.ContractService
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors

/**
 * Dialog de Folha Salarial.
 *
 * Mostra:
 *  - Total mensal da folha + comparação com a receita semanal de patrocínios
 *  - Breakdown por jogador (role, nome, salário, status do contrato)
 *  - Indicador visual de saúde financeira (folha sustentável ou não)
 *
 * Aberto pelo card "Folha Salarial" no Hub. Ao tocar num jogador da lista,
 * abre o [PlayerDetailDialog] para renegociar o contrato — fluxo de "mexer
 * na folha salarial" pedido pelo usuário.
 *
 * **SOLID:**
 *  - **SRP**: só apresenta a folha; cálculos vêm de [GameEngine] e [ContractService].
 *  - **OCP**: novas colunas/linhas se adicionam sem alterar o cálculo.
 *  - **DIP**: lê de [GameRepository], não acopla a nenhuma Activity específica.
 */
object PayrollDialog {

    fun show(activity: Activity, onChanged: () -> Unit = {}) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_payroll, null)
        val gs = GameRepository.current()

        val roster = GameRepository.rosterOf(activity.applicationContext, gs.managerTeamId)
            .sortedWith(compareBy({ roleOrder(it.role) }, { !it.titular }))

        val totalMonthly = GameEngine.totalMonthlyPayroll(activity.applicationContext)
        val weeklySponsors = com.cblol.scout.domain.usecase.SponsorService
            .totalWeeklyIncomeFromSponsors(gs) + gs.sponsorshipPerWeek
        val monthlySponsors = weeklySponsors * 4  // aproximação: 4 semanas/mês

        // Header com total + saúde financeira
        view.findViewById<TextView>(R.id.tv_payroll_total).text =
            activity.getString(R.string.payroll_total_format, "%,d".format(totalMonthly))

        val balance = monthlySponsors - totalMonthly
        val tvHealth = view.findViewById<TextView>(R.id.tv_payroll_health)
        if (balance >= 0) {
            tvHealth.text = activity.getString(R.string.payroll_health_positive,
                "%,d".format(balance))
            tvHealth.setTextColor(activity.resources.getColor(R.color.state_success, null))
        } else {
            tvHealth.text = activity.getString(R.string.payroll_health_negative,
                "%,d".format(-balance))
            tvHealth.setTextColor(activity.resources.getColor(R.color.state_danger, null))
        }

        // Lista de jogadores
        val container = view.findViewById<LinearLayout>(R.id.ll_payroll_list)
        container.removeAllViews()
        roster.forEach { player ->
            container.addView(buildPlayerRow(activity, player, totalMonthly, onChanged))
        }

        stylizedDialog(activity)
            .setTitle(R.string.payroll_title)
            .setView(view)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    /**
     * Constrói uma linha da folha para um jogador. Mostra role + nome,
     * salário, % da folha total e status do contrato. Toque abre o
     * PlayerDetailDialog (que tem o botão de renegociar).
     */
    private fun buildPlayerRow(
        activity: Activity,
        player: Player,
        totalMonthly: Long,
        onChanged: () -> Unit
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.item_payroll_row, null)
        val gs = GameRepository.current()
        val salary = player.contrato.salario_mensal_estimado_brl ?: 0L
        val pct = if (totalMonthly > 0) (salary * 100 / totalMonthly).toInt() else 0

        row.findViewById<View>(R.id.view_pr_role_bar)
            .setBackgroundColor(TeamColors.roleColor(player.role))
        row.findViewById<TextView>(R.id.tv_pr_role).text = player.role
        row.findViewById<TextView>(R.id.tv_pr_name).text =
            if (player.titular) player.nome_jogo else "${player.nome_jogo} (R)"
        row.findViewById<TextView>(R.id.tv_pr_salary).text =
            activity.getString(R.string.payroll_salary_format, "%,d".format(salary))
        row.findViewById<TextView>(R.id.tv_pr_pct).text =
            activity.getString(R.string.payroll_pct_format, pct)

        // Status do contrato (cor por urgência)
        val status = ContractService.statusOf(gs, player)
        val tvStatus = row.findViewById<TextView>(R.id.tv_pr_status)
        tvStatus.text = status.label
        tvStatus.setTextColor(when (status) {
            ContractService.ContractStatus.ACTIVE   ->
                activity.resources.getColor(R.color.color_on_surface_variant, null)
            ContractService.ContractStatus.EXPIRING ->
                activity.resources.getColor(R.color.state_warning, null)
            ContractService.ContractStatus.EXPIRED  ->
                activity.resources.getColor(R.color.state_danger, null)
        })

        // Toque abre o detalhe do jogador (de onde dá pra renegociar)
        row.setOnClickListener {
            PlayerDetailDialog.show(activity, player, onChanged = onChanged)
        }
        return row
    }

    private fun roleOrder(role: String): Int = when (role) {
        "TOP" -> 0; "JNG" -> 1; "MID" -> 2; "ADC" -> 3; "SUP" -> 4; else -> 5
    }
}
