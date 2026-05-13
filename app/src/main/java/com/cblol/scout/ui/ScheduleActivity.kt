package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.databinding.ActivityScheduleBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.ScheduleEvent
import com.cblol.scout.ui.viewmodel.ScheduleViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val vm: ScheduleViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)

        vm.matches.observe(this) { matches ->
            renderList(matches)
        }

        vm.event.observe(this) { event ->
            when (event) {
                is ScheduleEvent.ShowResult -> showSeriesResult(
                    event.playerName, event.opponentName, event.pw, event.ow
                )
                is ScheduleEvent.NextMap -> showNextMapDialog(
                    event.mapNum, event.playerWon, event.pw, event.ow
                )
            }
        }

        vm.loadMatches()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun renderList(matches: List<Match>) {
        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val sorted = matches.sortedWith(compareBy({ it.round }, { it.date }))

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = MatchAdapter(
            matches     = sorted,
            currentDate = gs.currentDate,
            myTeamId    = gs.managerTeamId,
            teamNames   = snap.times.associate { it.id to it.nome },
            onMatchClick = { m -> handleMatchClick(m) }
        )

        val nextIdx = sorted.indexOfFirst {
            !it.played &&
                (it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId)
        }
        if (nextIdx >= 0) binding.recycler.scrollToPosition(nextIdx)
    }

    private fun handleMatchClick(m: Match) {
        val snap     = GameRepository.snapshot(applicationContext)
        val gs       = GameRepository.current()
        val homeName = snap.times.find { it.id == m.homeTeamId }?.nome ?: m.homeTeamId
        val awayName = snap.times.find { it.id == m.awayTeamId }?.nome ?: m.awayTeamId

        if (m.played) {
            AlertDialog.Builder(this)
                .setTitle("Rodada ${m.round}")
                .setMessage("$homeName  ${m.homeScore} — ${m.awayScore}  $awayName")
                .setPositiveButton("OK", null).show()
            return
        }

        val isMyMatch = m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId

        if (isMyMatch) {
            val opponentId = if (m.homeTeamId == gs.managerTeamId) m.awayTeamId else m.homeTeamId
            AlertDialog.Builder(this)
                .setTitle("$homeName vs $awayName")
                .setMessage("Deseja fazer o pick & ban deste mapa?")
                .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                    startPickBan(m.id, gs.managerTeamId, opponentId, 1)
                }
                .setNegativeButton("Simular direto") { _, _ ->
                    startActivity(Intent(this, MatchSimulationActivity::class.java)
                        .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, m.id))
                }.show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("$homeName vs $awayName")
                .setMessage("Acompanhar partida?")
                .setPositiveButton("Assistir") { _, _ ->
                    startActivity(Intent(this, MatchSimulationActivity::class.java)
                        .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, m.id))
                }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    private fun startPickBan(matchId: String, playerTeamId: String, opponentId: String, mapNum: Int) {
        vm.pendingMatchId        = matchId
        vm.pendingMapNumber      = mapNum
        vm.pendingPlayerTeamId   = playerTeamId
        vm.pendingOpponentTeamId = opponentId
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra("player_team_id",   playerTeamId)
                putExtra("opponent_team_id", opponentId)
                putExtra("match_id",         matchId)
                putExtra("map_number",       mapNum)
            },
            PickBanActivity.REQUEST_PICK_BAN
        )
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return

        val bluePicks = data.getStringArrayListExtra("blue_picks")?.toList() ?: return
        val redPicks  = data.getStringArrayListExtra("red_picks")?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra("blue_bans")?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra("red_bans")?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra("map_number", vm.pendingMapNumber)

        val snap         = GameRepository.snapshot(applicationContext)
        val playerName   = snap.times.find { it.id == vm.pendingPlayerTeamId }?.nome  ?: "Você"
        val opponentName = snap.times.find { it.id == vm.pendingOpponentTeamId }?.nome ?: "Oponente"

        vm.handlePickBanResult(bluePicks, redPicks, blueBans, redBans, mapNum, playerName, opponentName)
    }

    private fun showSeriesResult(playerName: String, opponentName: String, pw: Int, ow: Int) {
        AlertDialog.Builder(this)
            .setTitle(if (pw > ow) "🏆 Vitória!" else "💔 Derrota")
            .setMessage("$playerName  $pw — $ow  $opponentName")
            .setPositiveButton("OK", null).show()
    }

    private fun showNextMapDialog(mapNum: Int, playerWon: Boolean, pw: Int, ow: Int) {
        val msg = if (playerWon) "✅ Mapa $mapNum: você venceu!" else "❌ Mapa $mapNum: oponente venceu."
        AlertDialog.Builder(this)
            .setTitle("Resultado — Mapa $mapNum  ($pw–$ow)")
            .setMessage("$msg\n\nContinuar para o Mapa ${mapNum + 1}?")
            .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                startPickBan(vm.pendingMatchId, vm.pendingPlayerTeamId, vm.pendingOpponentTeamId, mapNum + 1)
            }
            .setNegativeButton("Simular restante") { _, _ ->
                startActivity(Intent(this, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, vm.pendingMatchId))
            }.show()
    }

    private class MatchAdapter(
        private val matches: List<Match>,
        private val currentDate: String,
        private val myTeamId: String,
        private val teamNames: Map<String, String>,
        private val onMatchClick: (Match) -> Unit
    ) : RecyclerView.Adapter<MatchAdapter.VH>() {

        private val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View     = v.findViewById(R.id.view_match_round_bar)
            val tvRound: TextView = v.findViewById(R.id.tv_match_round)
            val tvDate: TextView  = v.findViewById(R.id.tv_match_date)
            val tvHome: TextView  = v.findViewById(R.id.tv_match_home)
            val tvAway: TextView  = v.findViewById(R.id.tv_match_away)
            val tvScore: TextView = v.findViewById(R.id.tv_match_score)
            val viewHomeBar: View = v.findViewById(R.id.view_match_home_color)
            val viewAwayBar: View = v.findViewById(R.id.view_match_away_color)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
        )

        override fun getItemCount() = matches.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val m = matches[i]
            val homeName = teamNames[m.homeTeamId] ?: m.homeTeamId
            val awayName = teamNames[m.awayTeamId] ?: m.awayTeamId
            h.tvRound.text = "Rodada ${m.round}"
            h.tvDate.text  = LocalDate.parse(m.date).format(dateFmt)
            h.tvHome.text  = homeName
            h.tvAway.text  = awayName
            h.viewHomeBar.setBackgroundColor(TeamColors.forTeam(m.homeTeamId))
            h.viewAwayBar.setBackgroundColor(TeamColors.forTeam(m.awayTeamId))
            h.viewBar.setBackgroundColor(
                if (m.homeTeamId == myTeamId || m.awayTeamId == myTeamId)
                    Color.parseColor("#C89B3C") else Color.parseColor("#3C3C41")
            )
            when {
                m.played          -> { h.tvScore.text = "${m.homeScore}-${m.awayScore}"; h.tvScore.setTextColor(Color.parseColor("#F0E6D2")) }
                m.date == currentDate -> { h.tvScore.text = "HOJE"; h.tvScore.setTextColor(Color.parseColor("#FFB800")) }
                m.date < currentDate  -> { h.tvScore.text = "—";    h.tvScore.setTextColor(Color.parseColor("#A09B8C")) }
                else                  -> { h.tvScore.text = "vs";   h.tvScore.setTextColor(Color.parseColor("#A09B8C")) }
            }
            h.itemView.setOnClickListener { onMatchClick(m) }
        }
    }
}
