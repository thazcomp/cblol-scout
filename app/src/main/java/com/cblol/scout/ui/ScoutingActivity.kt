package com.cblol.scout.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.data.ScoutingDepartmentTier
import com.cblol.scout.databinding.ActivityScoutingBinding
import com.cblol.scout.domain.usecase.ScoutingService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout

/**
 * Tela de Olheiros.
 *
 * Duas abas:
 *  - **ATIVOS**: jogadores em scouting agora, com barra de progresso até o
 *    próximo nível + botão CANCELAR
 *  - **DEPARTAMENTO**: tier atual (BASIC/PRO/ELITE), slots usados/disponíveis,
 *    custo semanal, botão UPGRADE
 *
 * O **start** de novos scoutings acontece pelos cards do mercado de
 * transferências (`TransferMarketActivity` ganha um botão "Escotear" quando
 * o jogador clica em qualquer card de jogador externo). Esta tela gerencia
 * apenas os já iniciados.
 *
 * **SOLID:**
 *  - **SRP**: Activity orquestra UI; o `ScoutingService` cuida das regras.
 *  - **OCP**: novos tiers = entry no enum, layout reflete automaticamente.
 *  - **DIP**: depende apenas de `ScoutingService` e `GameRepository`.
 */
class ScoutingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoutingBinding
    private var currentTab = TAB_ACTIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoutingBinding.inflate(layoutInflater)
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
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.scouting_tab_active))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.scouting_tab_department))
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
        val tier = ScoutingService.tier(gs)
        val active = ScoutingService.activeScouts(gs)
        binding.tvDeptSubtitle.text = getString(
            R.string.scouting_dept_subtitle_format,
            tier.label,
            active.size, tier.maxConcurrentScouts
        )

        when (currentTab) {
            TAB_ACTIVE     -> renderActive(active)
            TAB_DEPARTMENT -> renderDepartment()
        }
    }

    private fun renderActive(activePlayerIds: List<String>) {
        if (activePlayerIds.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.scouting_empty_active)
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE

        // Resolve cada playerId para um Player real (1ª divisão OU 2ª divisão)
        val players = activePlayerIds.mapNotNull { resolvePlayer(it) }
        binding.recycler.adapter = ActiveScoutAdapter(
            items = players,
            onCancel = ::onCancelScouting,
            onClick = ::openPlayerDetail
        )
    }

    private fun renderDepartment() {
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        val tiers = ScoutingDepartmentTier.values().toList()
        binding.recycler.adapter = DepartmentAdapter(
            tiers = tiers,
            currentTier = ScoutingService.tier(GameRepository.current()),
            onUpgrade = ::onUpgrade
        )
    }

    /**
     * Resolve um playerId para o `Player` correspondente. Procura primeiro no
     * snapshot da 1ª divisão; se não achar, tenta a 2ª divisão procedural.
     */
    private fun resolvePlayer(playerId: String): Player? {
        val fromSnap = GameRepository.snapshot(applicationContext).jogadores.find { it.id == playerId }
        if (fromSnap != null) return fromSnap
        return com.cblol.scout.util.SecondDivisionGenerator.generate().find { it.id == playerId }
    }

    // ── Ações ────────────────────────────────────────────────────────────

    /**
     * Abre a visualização detalhada do jogador escotado. Como o jogador NÃO
     * pertence ao elenco do gerente, o [PlayerDetailDialog] cai no modo somente
     * visualização (sem ações de gerência nem de mercado). Os atributos exibidos
     * respeitam o nível de scouting atual via `applyScoutingVisibility`.
     */
    private fun openPlayerDetail(player: Player) {
        PlayerDetailDialog.show(this, player) { renderState() }
    }

    private fun onCancelScouting(player: Player) {
        stylizedDialog(this)
            .setTitle(R.string.scouting_cancel_confirm_title)
            .setMessage(getString(R.string.scouting_cancel_confirm_msg, player.nome_jogo))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                ScoutingService.cancelScouting(GameRepository.current(), player.id)
                GameRepository.save(applicationContext)
                renderState()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onUpgrade(target: ScoutingDepartmentTier) {
        val gs = GameRepository.current()
        when (val result = ScoutingService.upgradeDepartment(gs)) {
            ScoutingService.UpgradeResult.OK -> {
                GameRepository.save(applicationContext)
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgraded_title)
                    .setMessage(getString(R.string.scouting_upgraded_msg, target.label))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
                renderState()
            }
            ScoutingService.UpgradeResult.ALREADY_MAX -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(R.string.scouting_upgrade_error_max)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            ScoutingService.UpgradeResult.LOW_REPUTATION -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(getString(R.string.scouting_upgrade_error_reputation,
                        target.minReputation, gs.coachProfile.reputation))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            ScoutingService.UpgradeResult.INSUFFICIENT_FUNDS -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(getString(R.string.scouting_upgrade_error_funds,
                        "%,d".format(target.upgradeCost), "%,d".format(gs.budget)))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
        }
    }

    companion object {
        private const val TAB_ACTIVE     = 0
        private const val TAB_DEPARTMENT = 1
    }

    // ── Adapter: scoutings ativos ────────────────────────────────────────

    private class ActiveScoutAdapter(
        private val items: List<Player>,
        private val onCancel: (Player) -> Unit,
        private val onClick: (Player) -> Unit
    ) : RecyclerView.Adapter<ActiveScoutAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView      = v.findViewById(R.id.tv_scout_name)
            val tvTeam: TextView      = v.findViewById(R.id.tv_scout_team)
            val tvLevel: TextView     = v.findViewById(R.id.tv_scout_level)
            val tvProgress: TextView  = v.findViewById(R.id.tv_scout_progress)
            val pbLevel: ProgressBar  = v.findViewById(R.id.pb_scout_level)
            val btnCancel: com.google.android.material.button.MaterialButton =
                v.findViewById(R.id.btn_scout_cancel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scouting_target, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val player = items[position]
            val ctx    = h.itemView.context
            val gs     = GameRepository.current()
            val level  = ScoutingService.scoutLevelOf(gs, player.id)
            val ov     = gs.playerOverrides[player.id]
            val daysAccum = ov?.scoutDaysAccumulated ?: 0
            val daysPerLevel = ScoutingService.tier(gs).daysPerLevel

            h.tvName.text     = "${player.role} · ${player.nome_jogo}"
            h.tvTeam.text     = player.time_nome
            h.tvLevel.text    = ctx.getString(R.string.scouting_level_format,
                level, ScoutingService.MAX_LEVEL)
            h.tvProgress.text = ctx.getString(R.string.scouting_progress_format,
                daysAccum, daysPerLevel)
            h.pbLevel.max     = daysPerLevel
            h.pbLevel.progress = daysAccum

            h.itemView.setOnClickListener { onClick(player) }
            h.btnCancel.setOnClickListener { onCancel(player) }
        }
    }

    // ── Adapter: tiers do departamento ───────────────────────────────────

    private class DepartmentAdapter(
        private val tiers: List<ScoutingDepartmentTier>,
        private val currentTier: ScoutingDepartmentTier,
        private val onUpgrade: (ScoutingDepartmentTier) -> Unit
    ) : RecyclerView.Adapter<DepartmentAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTier: TextView    = v.findViewById(R.id.tv_dept_tier)
            val tvStatus: TextView  = v.findViewById(R.id.tv_dept_status)
            val tvSlots: TextView   = v.findViewById(R.id.tv_dept_slots)
            val tvDays: TextView    = v.findViewById(R.id.tv_dept_days)
            val tvWeekly: TextView  = v.findViewById(R.id.tv_dept_weekly)
            val tvUpgrade: TextView = v.findViewById(R.id.tv_dept_upgrade_cost)
            val btnUpgrade: com.google.android.material.button.MaterialButton =
                v.findViewById(R.id.btn_dept_upgrade)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scouting_department_tier, parent, false)
        )

        override fun getItemCount() = tiers.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val tier = tiers[position]
            val ctx  = h.itemView.context
            h.tvTier.text   = tier.label
            h.tvSlots.text  = ctx.getString(R.string.scouting_dept_slots_format,
                tier.maxConcurrentScouts)
            h.tvDays.text   = ctx.getString(R.string.scouting_dept_days_format,
                tier.daysPerLevel)
            h.tvWeekly.text = ctx.getString(R.string.scouting_dept_weekly_format,
                "%,d".format(tier.weeklyMaintenanceCost))

            val currentOrdinal = currentTier.ordinal
            val thisOrdinal = tier.ordinal
            when {
                thisOrdinal < currentOrdinal -> {
                    // Tier abaixo do atual — já adquirido
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_owned)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal -> {
                    // Tier atual
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_current)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal + 1 -> {
                    // Próximo tier — upgrade disponível
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_next)
                    h.tvUpgrade.visibility = View.VISIBLE
                    h.tvUpgrade.text = ctx.getString(R.string.scouting_dept_upgrade_format,
                        "%,d".format(tier.upgradeCost), tier.minReputation)
                    h.btnUpgrade.visibility = View.VISIBLE
                    h.btnUpgrade.setOnClickListener { onUpgrade(tier) }
                }
                else -> {
                    // Tier futuro mas requer upgrade do anterior primeiro
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_locked)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
            }
        }
    }
}
