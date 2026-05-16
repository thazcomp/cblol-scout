package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.databinding.ActivityTeamSelectBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.TeamSelectViewModel
import com.cblol.scout.util.JsonLoader
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Seleção inicial de time (entry point após login).
 *
 * SOLID:
 * - **SRP**: tiers e budgets vêm de [GameConstants.Economy]; layout e binding isolados.
 * - **OCP**: novos tiers se adicionam em [tierBudget] / [tierSponsor].
 * - **DIP**: depende de [TeamSelectViewModel].
 */
class TeamSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeamSelectBinding
    private val vm: TeamSelectViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val username = intent.getStringExtra(EXTRA_USERNAME) ?: DEFAULT_USERNAME
        binding.tvGreeting.text = getString(R.string.team_select_greeting, username)

        observeViewModel()
        binding.recyclerTeams.layoutManager = GridLayoutManager(this, GRID_COLUMNS)

        CoroutineScope(Dispatchers.Main).launch {
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            binding.tvSubtitle.text = getString(R.string.team_select_subtitle,
                data.meta.split.removePrefix("2026 "))
            binding.recyclerTeams.adapter = TeamAdapter(data) { showStartDialog(username, it) }
        }

        vm.checkSave()
    }

    // ── Observers ────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.hasSave.observe(this) { has ->
            if (has) showContinueDialog()
        }
        vm.careerStarted.observe(this) { started ->
            if (started) {
                startActivity(Intent(this, ManagerHubActivity::class.java))
                finish()
            }
        }
    }

    private fun showContinueDialog() {
        val gs = GameRepository.load(applicationContext) ?: return
        stylizedDialog(this)
            .setTitle(R.string.dialog_continue_career)
            .setMessage(getString(R.string.team_select_continue_message, gs.managerTeamId.uppercase()))
            .setPositiveButton(R.string.btn_continue_career) { _, _ ->
                startActivity(Intent(this, ManagerHubActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.btn_new_career) { _, _ -> vm.clearAndRestart() }
            .setCancelable(false).show()
    }

    private fun showStartDialog(defaultName: String, team: Team) {
        val budget  = tierBudget(team.tier_orcamento)
        val sponsor = tierSponsor(team.tier_orcamento)
        val input = EditText(this).apply {
            setText(defaultName); setSelection(text.length)
            hint = getString(R.string.team_select_hint_coach_name)
            setPadding(INPUT_PAD_H, INPUT_PAD_V, INPUT_PAD_H, INPUT_PAD_V)
        }
        stylizedDialog(this)
            .setTitle(getString(R.string.dialog_start_career, team.nome))
            .setMessage(getString(R.string.team_select_start_message,
                team.tier_orcamento, "%,d".format(budget), "%,d".format(sponsor)))
            .setView(input)
            .setPositiveButton(R.string.btn_start) { _, _ ->
                vm.startCareer(input.text.toString().ifBlank { defaultName }, team.id)
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    // ── Tier → economia (regra de domínio) ──────────────────────────────

    private fun tierBudget(tier: String): Long = when (tier) {
        "S" -> GameConstants.Economy.STARTING_BUDGET_TIER_S
        "A" -> GameConstants.Economy.STARTING_BUDGET_TIER_A
        else -> GameConstants.Economy.STARTING_BUDGET_TIER_B
    }

    private fun tierSponsor(tier: String): Long = when (tier) {
        "S" -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_S
        "A" -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_A
        else -> GameConstants.Economy.WEEKLY_SPONSOR_TIER_B
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class TeamAdapter(
        private val data: SnapshotData,
        private val onClick: (Team) -> Unit
    ) : RecyclerView.Adapter<TeamAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView      = view.findViewById(R.id.card_team)
            val bar: View           = view.findViewById(R.id.view_team_color)
            val tvName: TextView    = view.findViewById(R.id.tv_team_name)
            val tvTier: TextView    = view.findViewById(R.id.tv_team_tier)
            val tvPlayers: TextView = view.findViewById(R.id.tv_team_players)
            val tvAvg: TextView     = view.findViewById(R.id.tv_team_avg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_team_card, parent, false)
        )

        override fun getItemCount() = data.times.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val team    = data.times[position]
            val ctx     = holder.itemView.context
            val players = data.jogadores.filter { it.time_id == team.id && it.titular }
            val avg     = if (players.isNotEmpty()) players.sumOf { it.overallRating() } / players.size else 0
            holder.bar.setBackgroundColor(TeamColors.forTeam(team.id))
            holder.tvName.text    = team.nome
            holder.tvTier.text    = ctx.getString(R.string.team_select_tier_label, team.tier_orcamento)
            holder.tvPlayers.text = ctx.getString(R.string.team_select_players, players.size)
            holder.tvAvg.text     = ctx.getString(R.string.team_select_overall, avg)
            holder.card.setOnClickListener { onClick(team) }
        }
    }

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_TEAM_ID  = "extra_team_id"

        private const val DEFAULT_USERNAME = "Técnico"
        private const val GRID_COLUMNS     = 2
        private const val INPUT_PAD_H      = 48
        private const val INPUT_PAD_V      = 32
    }
}
