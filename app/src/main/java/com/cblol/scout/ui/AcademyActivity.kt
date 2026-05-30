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
import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AcademyTier
import com.cblol.scout.databinding.ActivityAcademyBinding
import com.cblol.scout.domain.usecase.AcademyService
import com.cblol.scout.domain.usecase.AcademyUiState
import com.cblol.scout.domain.usecase.EvaluateProspectUseCase
import com.cblol.scout.domain.usecase.PromoteProspectUseCase
import com.cblol.scout.domain.usecase.RecruitProspectUseCase
import com.cblol.scout.domain.usecase.UpgradeAcademyUseCase
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.AcademyEvent
import com.cblol.scout.ui.viewmodel.AcademyViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Tela da **categoria de base (academia)**.
 *
 * **MVVM**: Activity observa [AcademyViewModel.state] (tier + prospects +
 * orçamento + reputação) e dispara diálogos a partir dos [AcademyEvent]s
 * emitidos pelos UseCases.
 *
 * Toda regra (recrutar, avaliar, promover, liberar, upgrade) vive no
 * [AcademyService] + nos UseCases que cuidam de log/save/notícia.
 */
class AcademyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAcademyBinding
    private val vm: AcademyViewModel by viewModel()
    private var currentTab = TAB_PROSPECTS
    private var lastState: AcademyUiState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAcademyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.btnRecruit.setOnClickListener { vm.recruitProspect() }

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

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(GameRepository.current().managerTeamId))
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.academy_tab_prospects))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.academy_tab_facility))
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

    private fun render(state: AcademyUiState) {
        binding.tvAcademySubtitle.text = getString(
            R.string.academy_subtitle_format,
            "${state.tier.emoji} ${state.tier.label}",
            state.prospects.size, state.tier.capacity
        )
        when (currentTab) {
            TAB_PROSPECTS -> renderProspects(state.prospects)
            TAB_FACILITY  -> renderFacility(state.tier)
        }
    }

    private fun renderProspects(prospects: List<AcademyProspect>) {
        binding.btnRecruit.visibility = View.VISIBLE
        if (prospects.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.academy_empty_prospects)
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        val sorted = prospects.sortedWith(
            compareByDescending<AcademyProspect> { it.isReady() }
                .thenByDescending { it.currentOverall }
        )
        binding.recycler.adapter = ProspectAdapter(
            items = sorted,
            onEvaluate = ::confirmEvaluate,
            onPromote = ::confirmPromote,
            onRelease = ::confirmRelease
        )
    }

    private fun renderFacility(currentTier: AcademyTier) {
        binding.btnRecruit.visibility = View.GONE
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = TierAdapter(
            tiers = AcademyTier.values().toList(),
            currentTier = currentTier,
            onUpgrade = { vm.upgradeAcademy(it) }
        )
    }

    // ── Diálogos de confirmação ──────────────────────────────────────────

    private fun confirmEvaluate(prospect: AcademyProspect) {
        stylizedDialog(this)
            .setTitle(R.string.academy_evaluate_confirm_title)
            .setMessage(getString(R.string.academy_evaluate_confirm_msg,
                prospect.nome, "%,d".format(AcademyService.EVALUATION_COST)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.evaluateProspect(prospect) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun confirmPromote(prospect: AcademyProspect) {
        val salary = AcademyService.suggestedSalaryFor(prospect)
        val readyNote = if (!prospect.isReady())
            getString(R.string.academy_promote_not_ready_note) else ""
        stylizedDialog(this)
            .setTitle(R.string.academy_promote_confirm_title)
            .setMessage(getString(R.string.academy_promote_confirm_msg,
                prospect.nome, prospect.role, prospect.currentOverall,
                "%,d".format(salary)) + readyNote)
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.promoteProspect(prospect) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun confirmRelease(prospect: AcademyProspect) {
        stylizedDialog(this)
            .setTitle(R.string.academy_release_confirm_title)
            .setMessage(getString(R.string.academy_release_confirm_msg, prospect.nome))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.releaseProspect(prospect) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    // ── Tratamento dos eventos do VM ─────────────────────────────────────

    private fun handleEvent(event: AcademyEvent) {
        when (event) {
            is AcademyEvent.RecruitDone  -> handleRecruitResult(event.result)
            is AcademyEvent.EvaluateDone -> handleEvaluateResult(event.prospect, event.result)
            is AcademyEvent.PromoteDone  -> handlePromoteResult(event.prospect, event.result)
            is AcademyEvent.UpgradeDone  -> handleUpgradeResult(event.target, event.result)
        }
    }

    private fun handleRecruitResult(result: RecruitProspectUseCase.Result) {
        when (result) {
            is RecruitProspectUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.academy_recruited_title)
                    .setMessage(getString(R.string.academy_recruited_msg,
                        result.prospect.nome, result.prospect.role, result.prospect.idade,
                        "%,d".format(result.cost)))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            RecruitProspectUseCase.Result.CapacityFull ->
                showError(getString(R.string.academy_error_capacity))
            is RecruitProspectUseCase.Result.InsufficientFunds ->
                showError(getString(R.string.academy_error_funds_recruit,
                    "%,d".format(result.cost), "%,d".format(result.budget)))
        }
    }

    private fun handleEvaluateResult(prospect: AcademyProspect, result: EvaluateProspectUseCase.Result) {
        when (result) {
            is EvaluateProspectUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.academy_evaluated_title)
                    .setMessage(getString(R.string.academy_evaluated_msg,
                        result.prospect.nome, result.prospect.potential, result.prospect.potentialBand()))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is EvaluateProspectUseCase.Result.InsufficientFunds ->
                showError(getString(R.string.academy_error_funds_evaluate,
                    "%,d".format(result.cost), "%,d".format(result.budget)))
            EvaluateProspectUseCase.Result.AlreadyEvaluated ->
                showError(getString(R.string.academy_error_already_evaluated))
            EvaluateProspectUseCase.Result.NotFound -> Unit
        }
    }

    private fun handlePromoteResult(prospect: AcademyProspect, result: PromoteProspectUseCase.Result) {
        when (result) {
            is PromoteProspectUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.academy_promoted_title)
                    .setMessage(getString(R.string.academy_promoted_msg, result.prospectName))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            PromoteProspectUseCase.Result.NotFound -> Unit
        }
    }

    private fun handleUpgradeResult(target: AcademyTier, result: UpgradeAcademyUseCase.Result) {
        when (result) {
            is UpgradeAcademyUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.academy_upgraded_title)
                    .setMessage(getString(R.string.academy_upgraded_msg, result.newTier.label))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            UpgradeAcademyUseCase.Result.AlreadyMax ->
                showError(getString(R.string.academy_upgrade_error_max))
            is UpgradeAcademyUseCase.Result.LowReputation ->
                showError(getString(R.string.academy_upgrade_error_reputation,
                    result.required, result.current))
            is UpgradeAcademyUseCase.Result.InsufficientFunds ->
                showError(getString(R.string.academy_upgrade_error_funds,
                    "%,d".format(result.cost), "%,d".format(result.budget)))
        }
    }

    private fun showError(msg: String) {
        stylizedDialog(this)
            .setMessage(msg)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    companion object {
        private const val TAB_PROSPECTS = 0
        private const val TAB_FACILITY  = 1

        fun intent(context: Context) = Intent(context, AcademyActivity::class.java)
    }

    // ── Adapter: prospects ───────────────────────────────────────────────

    private class ProspectAdapter(
        private val items: List<AcademyProspect>,
        private val onEvaluate: (AcademyProspect) -> Unit,
        private val onPromote: (AcademyProspect) -> Unit,
        private val onRelease: (AcademyProspect) -> Unit
    ) : RecyclerView.Adapter<ProspectAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val accentBar: View        = v.findViewById(R.id.view_accent_bar)
            val tvName: TextView       = v.findViewById(R.id.tv_prospect_name)
            val tvAge: TextView        = v.findViewById(R.id.tv_prospect_age)
            val tvOverall: TextView    = v.findViewById(R.id.tv_prospect_overall)
            val tvRealname: TextView   = v.findViewById(R.id.tv_prospect_realname)
            val tvPotential: TextView  = v.findViewById(R.id.tv_prospect_potential)
            val pbDev: ProgressBar     = v.findViewById(R.id.pb_prospect_dev)
            val tvDevLabel: TextView   = v.findViewById(R.id.tv_prospect_dev_label)
            val btnEvaluate: MaterialButton = v.findViewById(R.id.btn_evaluate)
            val btnPromote: MaterialButton  = v.findViewById(R.id.btn_promote)
            val btnRelease: MaterialButton  = v.findViewById(R.id.btn_release)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_academy_prospect, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val p = items[position]
            val ctx = h.itemView.context

            h.tvName.text = "${p.role} · ${p.nome}"
            h.tvAge.text = ctx.getString(R.string.academy_age_format, p.idade)
            h.tvOverall.text = ctx.getString(R.string.academy_overall_format, p.currentOverall)
            h.tvRealname.text = p.nomeReal

            if (p.evaluated) {
                h.tvPotential.text = ctx.getString(R.string.academy_potential_evaluated,
                    p.potential, p.potentialBand())
                h.tvPotential.setTextColor(ContextCompat.getColor(ctx, R.color.champion_gold))
            } else {
                h.tvPotential.text = ctx.getString(R.string.academy_potential_hidden, p.potentialBand())
                h.tvPotential.setTextColor(ContextCompat.getColor(ctx, R.color.color_on_surface_variant))
            }

            h.pbDev.progress = p.developmentPercent()
            h.tvDevLabel.text = ctx.getString(R.string.academy_dev_format, p.developmentPercent())

            val ready = p.isReady()
            h.accentBar.setBackgroundColor(ContextCompat.getColor(
                ctx, if (ready) R.color.state_success else R.color.color_outline_variant))

            h.btnEvaluate.visibility = if (p.evaluated) View.GONE else View.VISIBLE

            h.btnEvaluate.setOnClickListener { onEvaluate(p) }
            h.btnPromote.setOnClickListener { onPromote(p) }
            h.btnRelease.setOnClickListener { onRelease(p) }
        }
    }

    // ── Adapter: tiers da academia ───────────────────────────────────────

    private class TierAdapter(
        private val tiers: List<AcademyTier>,
        private val currentTier: AcademyTier,
        private val onUpgrade: (AcademyTier) -> Unit
    ) : RecyclerView.Adapter<TierAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView      = v.findViewById(R.id.tv_tier_name)
            val tvStatus: TextView    = v.findViewById(R.id.tv_tier_status)
            val tvCapacity: TextView  = v.findViewById(R.id.tv_tier_capacity)
            val tvGrowth: TextView    = v.findViewById(R.id.tv_tier_growth)
            val tvPotential: TextView = v.findViewById(R.id.tv_tier_potential)
            val tvWeekly: TextView    = v.findViewById(R.id.tv_tier_weekly)
            val tvUpgrade: TextView   = v.findViewById(R.id.tv_tier_upgrade_cost)
            val btnUpgrade: MaterialButton = v.findViewById(R.id.btn_tier_upgrade)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_academy_tier, parent, false)
        )

        override fun getItemCount() = tiers.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val tier = tiers[position]
            val ctx  = h.itemView.context
            h.tvName.text = "${tier.emoji} ${tier.label}"
            h.tvCapacity.text = ctx.getString(R.string.academy_tier_capacity_format, tier.capacity)
            h.tvGrowth.text = ctx.getString(R.string.academy_tier_growth_format,
                "%.1f".format(tier.growthFactor))
            h.tvPotential.text = ctx.getString(R.string.academy_tier_potential_format, tier.maxPotential)
            h.tvWeekly.text = ctx.getString(R.string.academy_tier_weekly_format,
                "%,d".format(tier.weeklyCost))

            val currentOrdinal = currentTier.ordinal
            val thisOrdinal = tier.ordinal
            when {
                thisOrdinal < currentOrdinal -> {
                    h.tvStatus.text = ctx.getString(R.string.academy_tier_status_owned)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal -> {
                    h.tvStatus.text = ctx.getString(R.string.academy_tier_status_current)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal + 1 -> {
                    h.tvStatus.text = ctx.getString(R.string.academy_tier_status_next)
                    h.tvUpgrade.visibility = View.VISIBLE
                    h.tvUpgrade.text = ctx.getString(R.string.academy_tier_upgrade_format,
                        "%,d".format(tier.upgradeCost), tier.minReputation)
                    h.btnUpgrade.visibility = View.VISIBLE
                    h.btnUpgrade.setOnClickListener { onUpgrade(tier) }
                }
                else -> {
                    h.tvStatus.text = ctx.getString(R.string.academy_tier_status_locked)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
            }
        }
    }
}
