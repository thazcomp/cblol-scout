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
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SquadManager
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

/**
 * Tela da **categoria de base (academia)**.
 *
 * Duas abas:
 *  - **PROSPECTS**: jovens da base, com overall atual, potencial (oculto até
 *    avaliação), barra de desenvolvimento e ações (Avaliar / Promover / Liberar).
 *    Um botão no header recruta um novo talento manualmente.
 *  - **ACADEMIA**: tiers (BASIC/PRO/ELITE) com capacidade, velocidade de
 *    desenvolvimento, potencial máximo, custo semanal e botão de upgrade.
 *
 * Toda a regra vive no [AcademyService] (JVM-puro); a promoção materializa o
 * prospect como [com.cblol.scout.data.Player] via
 * [GameRepository.addPromotedProspect] e roda a validação de elenco.
 *
 * **SOLID:**
 *  - **SRP**: Activity só orquestra UI; regras no AcademyService.
 *  - **OCP**: novos tiers entram no enum; a UI reflete automaticamente.
 *  - **DIP**: depende de AcademyService + GameRepository.
 */
class AcademyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAcademyBinding
    private var currentTab = TAB_PROSPECTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAcademyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.btnRecruit.setOnClickListener { onRecruit() }
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
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.academy_tab_prospects))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.academy_tab_facility))
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
        val tier = AcademyService.tier(gs)
        val prospects = AcademyService.prospects(gs)
        binding.tvAcademySubtitle.text = getString(
            R.string.academy_subtitle_format,
            "${tier.emoji} ${tier.label}",
            prospects.size, tier.capacity
        )

        when (currentTab) {
            TAB_PROSPECTS -> renderProspects(prospects)
            TAB_FACILITY  -> renderFacility()
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
        // Ordena: prontos primeiro, depois por potencial (avaliados) / overall.
        val sorted = prospects.sortedWith(
            compareByDescending<AcademyProspect> { it.isReady() }
                .thenByDescending { it.currentOverall }
        )
        binding.recycler.adapter = ProspectAdapter(
            items = sorted,
            onEvaluate = ::onEvaluate,
            onPromote = ::onPromote,
            onRelease = ::onRelease
        )
    }

    private fun renderFacility() {
        binding.btnRecruit.visibility = View.GONE
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = TierAdapter(
            tiers = AcademyTier.values().toList(),
            currentTier = AcademyService.tier(GameRepository.current()),
            onUpgrade = ::onUpgrade
        )
    }

    // ── Ações ────────────────────────────────────────────────────────────

    private fun onRecruit() {
        val gs = GameRepository.current()
        val (result, prospect) = AcademyService.recruitManually(gs)
        when (result) {
            AcademyService.RecruitResult.OK -> {
                GameRepository.save(applicationContext)
                stylizedDialog(this)
                    .setTitle(R.string.academy_recruited_title)
                    .setMessage(getString(R.string.academy_recruited_msg,
                        prospect!!.nome, prospect.role, prospect.idade,
                        "%,d".format(AcademyService.MANUAL_RECRUIT_COST)))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
                renderState()
            }
            AcademyService.RecruitResult.CAPACITY_FULL ->
                showError(getString(R.string.academy_error_capacity))
            AcademyService.RecruitResult.INSUFFICIENT_FUNDS ->
                showError(getString(R.string.academy_error_funds_recruit,
                    "%,d".format(AcademyService.MANUAL_RECRUIT_COST), "%,d".format(gs.budget)))
        }
    }

    private fun onEvaluate(prospect: AcademyProspect) {
        val gs = GameRepository.current()
        stylizedDialog(this)
            .setTitle(R.string.academy_evaluate_confirm_title)
            .setMessage(getString(R.string.academy_evaluate_confirm_msg,
                prospect.nome, "%,d".format(AcademyService.EVALUATION_COST)))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                when (AcademyService.evaluateProspect(gs, prospect.id)) {
                    AcademyService.EvaluateResult.OK -> {
                        GameRepository.save(applicationContext)
                        stylizedDialog(this)
                            .setTitle(R.string.academy_evaluated_title)
                            .setMessage(getString(R.string.academy_evaluated_msg,
                                prospect.nome, prospect.potential, prospect.potentialBand()))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    AcademyService.EvaluateResult.INSUFFICIENT_FUNDS ->
                        showError(getString(R.string.academy_error_funds_evaluate,
                            "%,d".format(AcademyService.EVALUATION_COST), "%,d".format(gs.budget)))
                    AcademyService.EvaluateResult.ALREADY_EVALUATED ->
                        showError(getString(R.string.academy_error_already_evaluated))
                    AcademyService.EvaluateResult.NOT_FOUND -> renderState()
                }
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onPromote(prospect: AcademyProspect) {
        val gs = GameRepository.current()
        val salary = AcademyService.suggestedSalaryFor(prospect)
        val readyNote = if (!prospect.isReady())
            getString(R.string.academy_promote_not_ready_note) else ""
        stylizedDialog(this)
            .setTitle(R.string.academy_promote_confirm_title)
            .setMessage(getString(R.string.academy_promote_confirm_msg,
                prospect.nome, prospect.role, prospect.currentOverall,
                "%,d".format(salary)) + readyNote)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                val promotion = AcademyService.promoteProspect(gs, prospect.id)
                if (promotion == null) { renderState(); return@setPositiveButton }
                val teamName = GameRepository.snapshot(applicationContext)
                    .times.find { it.id == gs.managerTeamId }?.nome ?: gs.managerTeamId
                GameRepository.addPromotedProspect(
                    applicationContext, promotion.prospect, promotion.suggestedSalary, teamName
                )
                GameRepository.log(
                    "ACADEMY",
                    "${prospect.nome} subiu da base para o elenco principal (overall ${prospect.currentOverall})."
                )
                // Cobertura jornalística da promoção (joia revelada).
                com.cblol.scout.domain.usecase.NewsService.reportAcademyPromotion(
                    gs, prospect.nome, teamName, prospect.currentOverall
                )
                GameRepository.save(applicationContext)
                SquadManager.validateAndFixRoster(applicationContext)
                stylizedDialog(this)
                    .setTitle(R.string.academy_promoted_title)
                    .setMessage(getString(R.string.academy_promoted_msg, prospect.nome))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
                renderState()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onRelease(prospect: AcademyProspect) {
        stylizedDialog(this)
            .setTitle(R.string.academy_release_confirm_title)
            .setMessage(getString(R.string.academy_release_confirm_msg, prospect.nome))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                AcademyService.releaseProspect(GameRepository.current(), prospect.id)
                GameRepository.save(applicationContext)
                renderState()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onUpgrade(target: AcademyTier) {
        val gs = GameRepository.current()
        when (AcademyService.upgrade(gs)) {
            AcademyService.UpgradeResult.OK -> {
                GameRepository.save(applicationContext)
                stylizedDialog(this)
                    .setTitle(R.string.academy_upgraded_title)
                    .setMessage(getString(R.string.academy_upgraded_msg, target.label))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
                renderState()
            }
            AcademyService.UpgradeResult.ALREADY_MAX ->
                showError(getString(R.string.academy_upgrade_error_max))
            AcademyService.UpgradeResult.LOW_REPUTATION ->
                showError(getString(R.string.academy_upgrade_error_reputation,
                    target.minReputation, gs.coachProfile.reputation))
            AcademyService.UpgradeResult.INSUFFICIENT_FUNDS ->
                showError(getString(R.string.academy_upgrade_error_funds,
                    "%,d".format(target.upgradeCost), "%,d".format(gs.budget)))
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
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_academy_prospect, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val p = items[position]
            val ctx = h.itemView.context

            h.tvName.text = "${p.role} · ${p.nome}"
            h.tvAge.text = ctx.getString(R.string.academy_age_format, p.idade)
            h.tvOverall.text = ctx.getString(R.string.academy_overall_format, p.currentOverall)
            h.tvRealname.text = p.nomeReal

            // Potencial: número exato se avaliado, faixa qualitativa se não.
            if (p.evaluated) {
                h.tvPotential.text = ctx.getString(
                    R.string.academy_potential_evaluated, p.potential, p.potentialBand())
                h.tvPotential.setTextColor(ContextCompat.getColor(ctx, R.color.champion_gold))
            } else {
                h.tvPotential.text = ctx.getString(
                    R.string.academy_potential_hidden, p.potentialBand())
                h.tvPotential.setTextColor(ContextCompat.getColor(ctx, R.color.color_on_surface_variant))
            }

            h.pbDev.progress = p.developmentPercent()
            h.tvDevLabel.text = ctx.getString(R.string.academy_dev_format, p.developmentPercent())

            // Barra lateral verde quando pronto para subir.
            val ready = p.isReady()
            h.accentBar.setBackgroundColor(ContextCompat.getColor(
                ctx, if (ready) R.color.state_success else R.color.color_outline_variant))

            // Botão avaliar some quando já avaliado.
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
            val tvName: TextView    = v.findViewById(R.id.tv_tier_name)
            val tvStatus: TextView  = v.findViewById(R.id.tv_tier_status)
            val tvCapacity: TextView = v.findViewById(R.id.tv_tier_capacity)
            val tvGrowth: TextView  = v.findViewById(R.id.tv_tier_growth)
            val tvPotential: TextView = v.findViewById(R.id.tv_tier_potential)
            val tvWeekly: TextView  = v.findViewById(R.id.tv_tier_weekly)
            val tvUpgrade: TextView = v.findViewById(R.id.tv_tier_upgrade_cost)
            val btnUpgrade: MaterialButton = v.findViewById(R.id.btn_tier_upgrade)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_academy_tier, parent, false)
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
