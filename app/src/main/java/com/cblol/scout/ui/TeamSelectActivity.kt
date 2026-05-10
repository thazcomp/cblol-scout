package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.databinding.ActivityTeamSelectBinding
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.JsonLoader
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TeamSelectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_TEAM_ID  = "extra_team_id"
    }

    private lateinit var binding: ActivityTeamSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeamSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Se já existe carreira, oferece continuar
        if (GameRepository.hasSave(applicationContext)) {
            val gs = GameRepository.load(applicationContext)
            if (gs != null) {
                AlertDialog.Builder(this)
                    .setTitle("Continuar carreira?")
                    .setMessage("Você já tem uma carreira como técnico do ${gs.managerTeamId.uppercase()}. Deseja continuar?")
                    .setPositiveButton("Continuar") { _, _ ->
                        startActivity(Intent(this, ManagerHubActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("Nova carreira") { _, _ ->
                        GameRepository.clear(applicationContext)
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "Técnico"
        binding.tvGreeting.text = "Olá, $username 👋"

        binding.recyclerTeams.layoutManager = GridLayoutManager(this, 2)

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            binding.tvSubtitle.text = "Escolha um time para iniciar sua carreira no CBLOL ${data.meta.split.removePrefix("2026 ")}"
            binding.recyclerTeams.adapter = TeamAdapter(data) { team -> showStartCareerDialog(username, team) }
        }
    }

    private fun showStartCareerDialog(defaultName: String, team: Team) {
        val (budget, sponsor) = when (team.tier_orcamento) {
            "S" -> 5_000_000L to 600_000L
            "A" -> 3_000_000L to 350_000L
            else -> 1_500_000L to 200_000L
        }

        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(text.length)
            hint = "Nome do técnico"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Iniciar carreira no ${team.nome}")
            .setMessage(
                "Tier ${team.tier_orcamento}\n" +
                "💰 Orçamento inicial: R$ ${"%,d".format(budget)}\n" +
                "📈 Patrocínio semanal: R$ ${"%,d".format(sponsor)}\n\n" +
                "O split começa em 28/03/2026. Você terá 7 dias de pré-temporada."
            )
            .setView(input)
            .setPositiveButton("Começar") { _, _ ->
                val name = input.text.toString().ifBlank { defaultName }
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        GameEngine.startNewCareer(applicationContext, name, team.id)
                    }
                    startActivity(Intent(this@TeamSelectActivity, ManagerHubActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private class TeamAdapter(
        private val data: SnapshotData,
        private val onClick: (Team) -> Unit
    ) : RecyclerView.Adapter<TeamAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView   = view.findViewById(R.id.card_team)
            val bar: View        = view.findViewById(R.id.view_team_color)
            val tvName: TextView = view.findViewById(R.id.tv_team_name)
            val tvTier: TextView = view.findViewById(R.id.tv_team_tier)
            val tvPlayers: TextView = view.findViewById(R.id.tv_team_players)
            val tvAvgRating: TextView = view.findViewById(R.id.tv_team_avg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_team_card, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = data.times.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val team = data.times[position]
            val players = data.jogadores.filter { it.time_id == team.id && it.titular }
            val avg = if (players.isNotEmpty())
                players.sumOf { it.overallRating() } / players.size else 0

            holder.bar.setBackgroundColor(TeamColors.forTeam(team.id))
            holder.tvName.text    = team.nome
            holder.tvTier.text    = "Tier ${team.tier_orcamento}"
            holder.tvPlayers.text = "${players.size} jogadores"
            holder.tvAvgRating.text = "OVR $avg"
            holder.card.setOnClickListener { onClick(team) }
        }
    }
}
