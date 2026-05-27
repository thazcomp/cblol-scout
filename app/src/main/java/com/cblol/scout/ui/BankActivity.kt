package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.BankLoan
import com.cblol.scout.data.FinancialHealth
import com.cblol.scout.data.LoanOffer
import com.cblol.scout.databinding.ActivityBankBinding
import com.cblol.scout.domain.usecase.BankService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

/**
 * Tela do **Banco** — empréstimos emergenciais + visão de saúde financeira.
 *
 * Duas abas:
 *  - **EMPRÉSTIMOS**: linhas de crédito disponíveis (filtradas por crédito
 *    disponível e reputação). Toque CONTRATAR para tomar o empréstimo.
 *  - **MINHAS DÍVIDAS**: empréstimos ativos com barra de quitação, saldo
 *    devedor e botão QUITAR (paga o saldo todo de uma vez).
 *
 * Header mostra um **banner colorido** com a saúde financeira atual
 * (🟢/🟡/🔴 + dica de ação), o orçamento atual e o crédito disponível.
 *
 * **SOLID:**
 *  - **SRP**: Activity só orquestra UI; regras no [BankService].
 *  - **OCP**: novas linhas de crédito entram no catálogo do service sem mexer
 *    aqui (a UI itera o que receber).
 *  - **DIP**: depende de [BankService] + [GameRepository].
 */
class BankActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBankBinding
    private var currentTab = TAB_OFFERS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBankBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        val gs = GameRepository.current()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(gs.managerTeamId))
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bank_tab_offers))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bank_tab_loans))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                renderState()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun renderState() {
        val gs = GameRepository.current()
        renderHealthBanner(BankService.financialHealth(gs))

        // Orçamento atual (vermelho se negativo)
        binding.tvBankBudget.text = getString(R.string.bank_budget_value, "%,d".format(gs.budget))
        val budgetColorRes = if (gs.budget < 0) R.color.hub_budget_negative
                             else R.color.hub_budget_positive
        binding.tvBankBudget.setTextColor(ContextCompat.getColor(this, budgetColorRes))

        // Crédito disponível
        val available = BankService.availableCredit(gs)
        binding.tvBankCredit.text = getString(R.string.bank_credit_value, "%,d".format(available))

        // Resumo de dívida (só se houver)
        val totalDebt = BankService.totalDebt(gs)
        if (totalDebt > 0) {
            binding.tvBankDebtSummary.visibility = View.VISIBLE
            val weekly = BankService.weeklyInstallmentsTotal(gs)
            binding.tvBankDebtSummary.text = getString(
                R.string.bank_debt_summary_format,
                "%,d".format(totalDebt), "%,d".format(weekly)
            )
        } else {
            binding.tvBankDebtSummary.visibility = View.GONE
        }

        when (currentTab) {
            TAB_OFFERS -> renderOffers(BankService.offersFor(gs))
            TAB_LOANS  -> renderActiveLoans(BankService.activeLoans(gs))
        }
    }

    /**
     * Pinta o banner de saúde financeira com cor + emoji + dica acionável.
     * Verde, amarelo ou vermelho — espelhando o [FinancialHealth].
     */
    private fun renderHealthBanner(health: FinancialHealth) {
        val gs = GameRepository.current()
        val (bgRes, fgRes) = when (health) {
            FinancialHealth.HEALTHY  -> R.color.state_success to R.color.pick_ban_bg
            FinancialHealth.WARNING  -> R.color.state_warning to R.color.pick_ban_bg
            FinancialHealth.CRITICAL -> R.color.state_danger  to android.R.color.white
        }
        binding.tvHealthBanner.setBackgroundColor(ContextCompat.getColor(this, bgRes))
        binding.tvHealthBanner.setTextColor(ContextCompat.getColor(this, fgRes))
        binding.tvHealthBanner.text = "${health.emoji} ${health.label} · ${BankService.healthAdvice(gs)}"
    }

    private fun renderOffers(offers: List<LoanOffer>) {
        if (offers.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.bank_empty_offers)
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = OfferAdapter(offers, ::onTakeLoan)
    }

    private fun renderActiveLoans(loans: List<BankLoan>) {
        if (loans.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.bank_empty_loans)
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = ActiveLoanAdapter(loans, ::onPayOffLoan)
    }

    // ── Ações ────────────────────────────────────────────────────────────

    private fun onTakeLoan(offer: LoanOffer) {
        val gs = GameRepository.current()
        val installment = BankService.installmentFor(offer)
        val total = installment * offer.weeks
        stylizedDialog(this)
            .setTitle(R.string.bank_take_confirm_title)
            .setMessage(getString(
                R.string.bank_take_confirm_msg,
                offer.label,
                "%,d".format(offer.principal),
                "%,d".format(installment),
                offer.weeks,
                "%,d".format(total),
                (offer.interestRate * 100).toInt()
            ))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                when (val result = BankService.takeLoan(gs, offer)) {
                    is BankService.TakeResult.Ok -> {
                        GameRepository.log(
                            "BANK",
                            "💰 Empréstimo \"${offer.label}\" contratado: R$ ${"%,d".format(offer.principal)} creditados."
                        )
                        GameRepository.save(applicationContext)
                        stylizedDialog(this)
                            .setTitle(R.string.bank_taken_title)
                            .setMessage(getString(R.string.bank_taken_msg,
                                "%,d".format(offer.principal),
                                "%,d".format(installment), offer.weeks))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    is BankService.TakeResult.ExceedsCredit ->
                        showError(getString(R.string.bank_error_exceeds_credit,
                            "%,d".format(offer.principal), "%,d".format(result.available)))
                    BankService.TakeResult.LowReputation ->
                        showError(getString(R.string.bank_error_low_reputation,
                            offer.minReputation, gs.coachProfile.reputation))
                }
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onPayOffLoan(loan: BankLoan) {
        val gs = GameRepository.current()
        val balance = loan.outstandingBalance
        stylizedDialog(this)
            .setTitle(R.string.bank_payoff_confirm_title)
            .setMessage(getString(R.string.bank_payoff_confirm_msg,
                loan.label, "%,d".format(balance), "%,d".format(gs.budget)))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                when (val result = BankService.payOffEarly(gs, loan.id)) {
                    is BankService.PayoffResult.Ok -> {
                        GameRepository.log(
                            "BANK",
                            "✅ Empréstimo \"${loan.label}\" quitado antecipadamente (R$ ${"%,d".format(result.amountPaid)})."
                        )
                        GameRepository.save(applicationContext)
                        stylizedDialog(this)
                            .setMessage(getString(R.string.bank_payoff_done,
                                loan.label, "%,d".format(result.amountPaid)))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    is BankService.PayoffResult.InsufficientFunds ->
                        showError(getString(R.string.bank_payoff_insufficient,
                            "%,d".format(result.needed), "%,d".format(gs.budget)))
                    BankService.PayoffResult.NotFound -> renderState()
                }
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun showError(msg: String) {
        stylizedDialog(this)
            .setMessage(msg)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    companion object {
        private const val TAB_OFFERS = 0
        private const val TAB_LOANS  = 1

        fun intent(context: Context) = Intent(context, BankActivity::class.java)
    }

    // ── Adapter: ofertas de empréstimo ───────────────────────────────────

    private class OfferAdapter(
        private val items: List<LoanOffer>,
        private val onTake: (LoanOffer) -> Unit
    ) : RecyclerView.Adapter<OfferAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvEmoji: TextView       = v.findViewById(R.id.tv_loan_emoji)
            val tvName: TextView        = v.findViewById(R.id.tv_loan_name)
            val tvDescription: TextView = v.findViewById(R.id.tv_loan_description)
            val tvPrincipal: TextView   = v.findViewById(R.id.tv_loan_principal)
            val tvInstallment: TextView = v.findViewById(R.id.tv_loan_installment)
            val tvTotal: TextView       = v.findViewById(R.id.tv_loan_total)
            val tvTerms: TextView       = v.findViewById(R.id.tv_loan_terms)
            val btnTake: MaterialButton = v.findViewById(R.id.btn_loan_take)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_loan_offer, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val offer = items[position]
            val ctx   = h.itemView.context

            h.tvEmoji.text       = offer.emoji
            h.tvName.text        = offer.label
            h.tvDescription.text = offer.description

            val installment = BankService.installmentFor(offer)
            val total       = installment * offer.weeks
            h.tvPrincipal.text   = "R$ ${"%,d".format(offer.principal)}"
            h.tvInstallment.text = "R$ ${"%,d".format(installment)}"
            h.tvTotal.text       = "R$ ${"%,d".format(total)}"

            h.tvTerms.text = ctx.getString(
                R.string.bank_loan_terms_format,
                offer.weeks, (offer.interestRate * 100).toInt()
            )

            h.btnTake.setOnClickListener { onTake(offer) }
        }
    }

    // ── Adapter: empréstimos ativos ──────────────────────────────────────

    private class ActiveLoanAdapter(
        private val items: List<BankLoan>,
        private val onPayOff: (BankLoan) -> Unit
    ) : RecyclerView.Adapter<ActiveLoanAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val accent: View              = v.findViewById(R.id.view_loan_accent)
            val tvEmoji: TextView         = v.findViewById(R.id.tv_active_loan_emoji)
            val tvName: TextView          = v.findViewById(R.id.tv_active_loan_name)
            val tvProgress: TextView      = v.findViewById(R.id.tv_active_loan_progress)
            val pb: ProgressBar           = v.findViewById(R.id.pb_active_loan)
            val tvInstallment: TextView   = v.findViewById(R.id.tv_active_loan_installment)
            val tvBalance: TextView       = v.findViewById(R.id.tv_active_loan_balance)
            val btnPayoff: MaterialButton = v.findViewById(R.id.btn_active_loan_payoff)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_active_loan, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val loan = items[position]
            val ctx  = h.itemView.context

            // Emoji baseado no id da linha (sem persistir o emoji no BankLoan
            // para manter o save enxuto).
            h.tvEmoji.text = emojiForLoanId(loan.id, loan.label)
            h.tvName.text  = loan.label

            h.tvProgress.text = ctx.getString(
                R.string.bank_active_loan_progress,
                loan.installmentsPaid, loan.totalInstallments
            )
            h.pb.progress = loan.repaymentPercent()

            // Cor da barra lateral: verde se passou de 70% (quase quitado),
            // amarelo entre 30-70%, vermelho < 30%.
            val accentRes = when {
                loan.repaymentPercent() >= 70 -> R.color.state_success
                loan.repaymentPercent() >= 30 -> R.color.state_warning
                else                          -> R.color.state_danger
            }
            h.accent.setBackgroundColor(ContextCompat.getColor(ctx, accentRes))

            h.tvInstallment.text = "R$ ${"%,d".format(loan.installmentAmount)}"
            h.tvBalance.text     = "R$ ${"%,d".format(loan.outstandingBalance)}"

            h.btnPayoff.setOnClickListener { onPayOff(loan) }
        }

        /**
         * Inferência simples de emoji a partir do id/label da linha. Mantém a
         * UI viva sem ter de salvar o emoji junto do empréstimo.
         */
        private fun emojiForLoanId(id: String, label: String): String = when {
            id.contains("micro")       -> "💸"
            id.contains("emergency")   -> "🆘"
            id.contains("investment")  -> "📈"
            id.contains("mega")        -> "🏦"
            label.contains("Emergencial", ignoreCase = true) -> "🆘"
            else -> "🏦"
        }
    }
}
