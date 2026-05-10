package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.databinding.ActivityTeamSelectBinding
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

        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "Usuário"
        binding.tvGreeting.text = "Olá, $username 👋"

        binding.recyclerTeams.layoutManager = GridLayoutManager(this, 2)

        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { JsonLoader.loadSnapshot(applicationContext) }
            binding.tvSubtitle.text = "Escolha um time do CBLOL ${data.meta.split.removePrefix("2026 ")} para gerenciar"
            binding.recyclerTeams.adapter = TeamAdapter(data) { team -> openTeam(team) }
        }
    }

    private fun openTeam(team: Team) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_TEAM_ID, team.id)
        )
    }

    /**
     * Adapter inline. Cada card mostra:
     *  - barra colorida do time
     *  - nome do time
     *  - tier de orçamento (S/A/B)
     *  - quantidade de jogadores titulares
     */
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
