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
import com.cblol.scout.domain.usecase.ScoutingUiState
import com.cblol.scout.domain.usecase.UpgradeScoutingUseCase
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.ScoutingEvent
import com.cblol.scout.ui.viewmodel.ScoutingViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Tela de **Olheiros**.
 *
 * **MVVM**: observa [ScoutingViewModel.state] (tier do departamento + lista
 * de scoutings ativos com players já resolvidos) e responde a
 * [ScoutingEvent] para mostrar diálogos de upgrade.
 *
 * A resolução de "playerId → Player" (que precisava olhar snapshot E gerador
 * procedural da 2ª divisão) vive no [com.cblol.scout.domain.usecase.GetScoutingStateUseCase] —
 * a Activity recebe a lista pronta.
 */
class ScoutingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoutingBinding
    private val vm: ScoutingViewModel by viewModel()
    private var currentTab = TAB_ACTIVE
    private var lastState: ScoutingUiState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoutingBinding.inflate(layoutInflater)
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
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.scouting_tab_active))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.scouting_tab_department))
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

    private fun render(state: ScoutingUiState) {
        binding.tvDeptSubtitle.text = getString(
            R.string.scouting_dept_subtitle_format,
            state.tier.label,
            state.activeScouts.size, state.maxConcurrent
        )
        when (currentTab) {
            TAB_ACTIVE     -> renderActive(state.activeScouts)
            TAB_DEPARTMENT -> renderDepartment(state.tier)
        }
    }

    private fun renderActive(players: List<Player>) {
        if (players.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.scouting_empty_active)
            return
        }
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = ActiveScoutAdapter(
            items = players,
            onCancel = ::confirmCancel,
            onClick  = ::openPlayerDetail
        )
    }

    private fun renderDepartment(currentTier: ScoutingDepartmentTier) {
        binding.recycler.visibility = View.VISIBLE
        binding.tvEmpty.visibility  = View.GONE
        binding.recycler.adapter = DepartmentAdapter(
            tiers = ScoutingDepartmentTier.values().toList(),
            currentTier = currentTier,
            onUpgrade = { vm.upgrade() }
        )
    }

    /**
     * Abre o detalhe do jogador escotado. Como o jogador NÃO pertence ao
     * elenco, o dialog cai em modo somente visualização. Visibility dos
     * atributos respeita o nível de scouting atual.
     */
    private fun openPlayerDetail(player: Player) {
        PlayerDetailDialog.show(this, player) { vm.refresh() }
    }

    // ── Diálogos de confirmação ──────────────────────────────────────────

    private fun confirmCancel(player: Player) {
        stylizedDialog(this)
            .setTitle(R.string.scouting_cancel_confirm_title)
            .setMessage(getString(R.string.scouting_cancel_confirm_msg, player.nome_jogo))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.cancel(player) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun handleEvent(event: ScoutingEvent) {
        when (event) {
            is ScoutingEvent.CancelDone -> Unit  // refresh já cuida do render
            is ScoutingEvent.UpgradeDone -> handleUpgradeResult(event.result)
        }
    }

    private fun handleUpgradeResult(result: UpgradeScoutingUseCase.Result) {
        when (result) {
            is UpgradeScoutingUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgraded_title)
                    .setMessage(getString(R.string.scouting_upgraded_msg, result.newTier.label))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            UpgradeScoutingUseCase.Result.AlreadyMax -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(R.string.scouting_upgrade_error_max)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is UpgradeScoutingUseCase.Result.LowReputation -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(getString(R.string.scouting_upgrade_error_reputation,
                        result.required, result.current))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is UpgradeScoutingUseCase.Result.InsufficientFunds -> {
                stylizedDialog(this)
                    .setTitle(R.string.scouting_upgrade_error_title)
                    .setMessage(getString(R.string.scouting_upgrade_error_funds,
                        "%,d".format(result.cost), "%,d".format(result.budget)))
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
            // O nível atual + progresso do scouting são por-jogador e podem
            // mudar a cada tick; lê on-the-fly do GameRepository em vez de
            // empurrar tudo para o state (que ficaria pesado pra uma info
            // muito específica de bind de célula).
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
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_owned)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal -> {
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_current)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
                thisOrdinal == currentOrdinal + 1 -> {
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_next)
                    h.tvUpgrade.visibility = View.VISIBLE
                    h.tvUpgrade.text = ctx.getString(R.string.scouting_dept_upgrade_format,
                        "%,d".format(tier.upgradeCost), tier.minReputation)
                    h.btnUpgrade.visibility = View.VISIBLE
                    h.btnUpgrade.setOnClickListener { onUpgrade(tier) }
                }
                else -> {
                    h.tvStatus.text = ctx.getString(R.string.scouting_dept_status_locked)
                    h.tvUpgrade.visibility = View.GONE
                    h.btnUpgrade.visibility = View.GONE
                }
            }
        }
    }
}
