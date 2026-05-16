package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.databinding.ActivitySquadBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.PromoteResult
import com.cblol.scout.game.SquadManager
import com.cblol.scout.ui.viewmodel.SquadViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Tela de gerenciamento de elenco.
 *
 * SOLID:
 * - **SRP**: tabs / lista / adapter / dialogs separados.
 * - **OCP**: cores de overall ranking via [overallColorRes] (mapeamento por bracket).
 * - **DIP**: depende de [SquadViewModel] e [SquadManager]. Strings/cores via res.
 */
class SquadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySquadBinding
    private val vm: SquadViewModel by viewModel()
    private var showStarters = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySquadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        setupHeader()
        setupTabs()
        observeViewModel()
        vm.load()
    }

    override fun onResume() {
        super.onResume()
        val logs = SquadManager.validateAndFixRoster(applicationContext)
        if (logs.isNotEmpty()) {
            stylizedDialog(this)
                .setTitle(R.string.dialog_squad_adjusted_title)
                .setMessage(logs.joinToString("\n"))
                .setPositiveButton(R.string.btn_ok, null).show()
        }
        vm.load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupHeader() {
        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val team = snap.times.find { it.id == gs.managerTeamId } ?: return
        binding.toolbar.title = getString(R.string.squad_title, team.nome)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.recycler.layoutManager = LinearLayoutManager(this)
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.squad_tab_starters))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.squad_tab_bench))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showStarters = (tab.position == 0)
                renderCurrentTab()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun observeViewModel() {
        vm.starters.observe(this) { starters ->
            binding.tabs.getTabAt(0)?.text = getString(R.string.squad_tab_starters_count, starters.size)
            if (showStarters) renderList(starters, isStarter = true)
        }
        vm.reserves.observe(this) { reserves ->
            binding.tabs.getTabAt(1)?.text = getString(R.string.squad_tab_bench_count, reserves.size)
            if (!showStarters) renderList(reserves, isStarter = false)
        }
        vm.swapResult.observe(this) { ok ->
            if (ok) toast(R.string.squad_swap_done)
        }
        vm.promoteResult.observe(this) { result ->
            val msgRes = when (result) {
                is PromoteResult.Swapped  -> R.string.squad_replaced
                is PromoteResult.Promoted -> R.string.squad_promoted
                else -> return@observe
            }
            if (msgRes == R.string.squad_replaced) {
                toast(getString(R.string.squad_replaced, (result as PromoteResult.Swapped).replaced.nome_jogo))
            } else {
                toast(R.string.squad_promoted)
            }
        }
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun renderCurrentTab() {
        if (showStarters) vm.starters.value?.let { renderList(it, true) }
        else              vm.reserves.value?.let { renderList(it, false) }
    }

    private fun renderList(list: List<Player>, isStarter: Boolean) {
        binding.recycler.adapter = SquadAdapter(
            players = list,
            isStarter = isStarter,
            onActionClick = { p ->
                if (isStarter) openSwapDialog(p) else vm.promote(p.id)
            },
            onItemClick = { p -> PlayerDetailDialog.show(this, p) { vm.load() } }
        )
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.setText(
            if (isStarter) R.string.squad_empty_starters else R.string.squad_empty_bench
        )
    }

    private fun openSwapDialog(starter: Player) {
        val candidates = SquadManager.reservesForRoleOf(applicationContext, starter)
        if (candidates.isEmpty()) {
            stylizedDialog(this)
                .setTitle(R.string.dialog_no_substitutes_title)
                .setMessage(getString(R.string.dialog_no_substitutes_message, starter.role))
                .setPositiveButton(R.string.btn_ok, null).show()
            return
        }
        val items = candidates.map { getString(R.string.player_overall_format, it.nome_jogo, it.overallRating()) }
            .toTypedArray()
        stylizedDialog(this)
            .setTitle(getString(R.string.dialog_substitute_title, starter.nome_jogo, starter.role))
            .setItems(items) { _, which -> vm.swap(starter.id, candidates[which].id) }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun toast(@androidx.annotation.StringRes res: Int) =
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ── Adapter ──────────────────────────────────────────────────────────

    private class SquadAdapter(
        private val players: List<Player>,
        private val isStarter: Boolean,
        private val onActionClick: (Player) -> Unit,
        private val onItemClick: (Player) -> Unit
    ) : RecyclerView.Adapter<SquadAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View       = v.findViewById(R.id.view_sq_bar)
            val tvRole: TextView    = v.findViewById(R.id.tv_sq_role)
            val tvName: TextView    = v.findViewById(R.id.tv_sq_name)
            val tvInfo: TextView    = v.findViewById(R.id.tv_sq_info)
            val tvOvr: TextView     = v.findViewById(R.id.tv_sq_overall)
            val btnAction: TextView = v.findViewById(R.id.btn_sq_action)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_squad_player, parent, false)
        )

        override fun getItemCount() = players.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val p   = players[i]
            val ctx = h.itemView.context
            h.viewBar.setBackgroundColor(TeamColors.forTeam(p.time_id))
            h.tvRole.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = ROLE_BG_CORNER
                setColor(TeamColors.roleColor(p.role))
            }
            h.tvRole.text = p.role
            h.tvName.text = p.nome_jogo
            val salary = p.contrato.salario_mensal_estimado_brl ?: 0L
            h.tvInfo.text = ctx.getString(R.string.squad_player_info,
                p.stats_brutas.kda.toString(), p.stats_brutas.cs_min.toString(), "%,d".format(salary))
            val ovr = p.overallRating()
            h.tvOvr.text = ovr.toString()
            h.tvOvr.setTextColor(ContextCompat.getColor(ctx, overallColorRes(ovr)))
            h.btnAction.setText(if (isStarter) R.string.squad_action_swap else R.string.squad_action_promote)
            h.btnAction.setOnClickListener { onActionClick(p) }
            h.itemView.setOnClickListener { onItemClick(p) }
        }

        @ColorRes
        private fun overallColorRes(ovr: Int): Int = when {
            ovr >= GameConstants.Economy.OVERALL_BRACKET_MYTHIC    -> R.color.rarity_mythic
            ovr >= GameConstants.Economy.OVERALL_BRACKET_LEGENDARY -> R.color.rarity_legendary
            ovr >= GameConstants.Economy.OVERALL_BRACKET_EPIC      -> R.color.rarity_epic
            else                                                   -> R.color.rarity_rare
        }

        companion object {
            private const val ROLE_BG_CORNER = 4f
        }
    }
}
