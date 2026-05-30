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
import com.cblol.scout.domain.usecase.BankUiState
import com.cblol.scout.domain.usecase.PayOffLoanUseCase
import com.cblol.scout.domain.usecase.TakeLoanUseCase
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.BankEvent
import com.cblol.scout.ui.viewmodel.BankViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Tela do **Banco** — empréstimos emergenciais + visão de saúde financeira.
 *
 * **MVVM**: Activity observa [BankViewModel.state] (banner de saúde,
 * orçamento, crédito, ofertas, dívidas) e responde a [BankEvent]s para
 * mostrar diálogos de resultado.
 *
 * Regras de juros, parcelas, limite de crédito vivem no [BankService]; a
 * sequência "regra → log → save" nos UseCases. A Activity só dispara
 * diálogos e renderiza o estado.
 */
class BankActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBankBinding
    private val vm: BankViewModel by viewModel()
    private var currentTab = TAB_OFFERS
    private var lastState: BankUiState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBankBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)

        vm.state.observe(this) { state ->
            lastState = state
            render(state)
        }
        vm.events.observe(this) { ev -> ev.consume()?.let(::handleEvent) }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun setupToolbar() {
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(GameRepository.current().managerTeamId))
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bank_tab_offers))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.bank_tab_loans))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                lastState?.let(::render)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun render(state: BankUiState) {
        renderHealthBanner(state)

        // Orçamento (vermelho se negativo)
        binding.tvBankBudget.text = getString(R.string.bank_budget_value, "%,d".format(state.budget))
        val budgetColorRes = if (state.budget < 0) R.color.hub_budget_negative
                             else R.color.hub_budget_positive
        binding.tvBankBudget.setTextColor(ContextCompat.getColor(this, budgetColorRes))

        // Crédito disponível
        binding.tvBankCredit.text = getString(R.string.bank_credit_value, "%,d".format(state.availableCredit))

        // Resumo de dívida (só se houver)
        if (state.totalDebt > 0) {
            binding.tvBankDebtSummary.visibility = View.VISIBLE
            binding.tvBankDebtSummary.text = getString(R.string.bank_debt_summary_format,
                "%,d".format(state.totalDebt), "%,d".format(state.weeklyInstallments))
        } else {
            binding.tvBankDebtSummary.visibility = View.GONE
        }

        when (currentTab) {
            TAB_OFFERS -> renderOffers(state.offers)
            TAB_LOANS  -> renderActiveLoans(state.activeLoans)
        }
    }

    private fun renderHealthBanner(state: BankUiState) {
        val (bgRes, fgRes) = when (state.health) {
            FinancialHealth.HEALTHY  -> R.color.state_success to R.color.pick_ban_bg
            FinancialHealth.WARNING  -> R.color.state_warning to R.color.pick_ban_bg
            FinancialHealth.CRITICAL -> R.color.state_danger  to android.R.color.white
        }
        binding.tvHealthBanner.setBackgroundColor(ContextCompat.getColor(this, bgRes))
        binding.tvHealthBanner.setTextColor(ContextCompat.getColor(this, fgRes))
        binding.tvHealthBanner.text = "${state.health.emoji} ${state.health.label} · ${state.healthAdvice}"
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
        binding.recycler.adapter = OfferAdapter(offers, ::confirmTakeLoan)
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
        binding.recycler.adapter = ActiveLoanAdapter(loans, ::confirmPayOff)
    }

    // ── Diálogos de confirmação ──────────────────────────────────────────

    private fun confirmTakeLoan(offer: LoanOffer) {
        val installment = BankService.installmentFor(offer)
        val total = installment * offer.weeks
        stylizedDialog(this)
            .setTitle(R.string.bank_take_confirm_title)
            .setMessage(getString(R.string.bank_take_confirm_msg,
                offer.label,
                "%,d".format(offer.principal),
                "%,d".format(installment),
                offer.weeks,
                "%,d".format(total),
                (offer.interestRate * 100).toInt()))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.contractLoan(offer) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun confirmPayOff(loan: BankLoan) {
        val balance = loan.outstandingBalance
        val budget = lastState?.budget ?: 0L
        stylizedDialog(this)
            .setTitle(R.string.bank_payoff_confirm_title)
            .setMessage(getString(R.string.bank_payoff_confirm_msg,
                loan.label, "%,d".format(balance), "%,d".format(budget)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.payOffLoan(loan) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    // ── Tratamento dos eventos do VM ─────────────────────────────────────

    private fun handleEvent(event: BankEvent) {
        when (event) {
            is BankEvent.TakeLoanDone -> handleTakeLoanResult(event.offer, event.result)
            is BankEvent.PayOffDone   -> handlePayOffResult(event.loan, event.result)
        }
    }

    private fun handleTakeLoanResult(offer: LoanOffer, result: TakeLoanUseCase.Result) {
        when (result) {
            is TakeLoanUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.bank_taken_title)
                    .setMessage(getString(R.string.bank_taken_msg,
                        "%,d".format(offer.principal),
                        "%,d".format(result.installment), offer.weeks))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is TakeLoanUseCase.Result.ExceedsCredit -> {
                showError(getString(R.string.bank_error_exceeds_credit,
                    "%,d".format(result.requested), "%,d".format(result.available)))
            }
            is TakeLoanUseCase.Result.LowReputation -> {
                showError(getString(R.string.bank_error_low_reputation,
                    result.required, result.current))
            }
        }
    }

    private fun handlePayOffResult(loan: BankLoan, result: PayOffLoanUseCase.Result) {
        when (result) {
            is PayOffLoanUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setMessage(getString(R.string.bank_payoff_done,
                        result.loanLabel, "%,d".format(result.amountPaid)))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is PayOffLoanUseCase.Result.InsufficientFunds -> {
                showError(getString(R.string.bank_payoff_insufficient,
                    "%,d".format(result.needed), "%,d".format(result.budget)))
            }
            PayOffLoanUseCase.Result.NotFound -> Unit
        }
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
            LayoutInflater.from(parent.context).inflate(R.layout.item_loan_offer, parent, false)
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

            h.tvTerms.text = ctx.getString(R.string.bank_loan_terms_format,
                offer.weeks, (offer.interestRate * 100).toInt())

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
            LayoutInflater.from(parent.context).inflate(R.layout.item_active_loan, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val loan = items[position]
            val ctx  = h.itemView.context

            h.tvEmoji.text = emojiForLoanId(loan.id, loan.label)
            h.tvName.text  = loan.label

            h.tvProgress.text = ctx.getString(R.string.bank_active_loan_progress,
                loan.installmentsPaid, loan.totalInstallments)
            h.pb.progress = loan.repaymentPercent()

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

        private fun emojiForLoanId(id: String, label: String): String = when {
            id.contains("micro")      -> "💸"
            id.contains("emergency")  -> "🆘"
            id.contains("investment") -> "📈"
            id.contains("mega")       -> "🏦"
            label.contains("Emergencial", ignoreCase = true) -> "🆘"
            else -> "🏦"
        }
    }
}
