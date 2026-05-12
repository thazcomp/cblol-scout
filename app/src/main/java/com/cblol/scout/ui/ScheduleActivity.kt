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
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.SeriesState
import com.cblol.scout.databinding.ActivityScheduleBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding

    // Estado da série BO3 em andamento (limpo ao finalizar)
    private var pendingMatchId: String = ""
    private var pendingMapNumber: Int = 1
    private var pendingPlayerTeamId: String = ""
    private var pendingOpponentTeamId: String = ""

    // ────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        refreshList()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Monta / atualiza o RecyclerView ──────────────────────────────────
    private fun refreshList() {
        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)

        val sorted = gs.matches.sortedWith(compareBy({ it.round }, { it.date }))

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = MatchAdapter(
            matches      = sorted,
            currentDate  = gs.currentDate,
            myTeamId     = gs.managerTeamId,
            teamNames    = snap.times.associate { it.id to it.nome },
            onMatchClick = { m -> handleMatchClick(m) }
        )

        // Scroll para a próxima partida do meu time
        val nextIdx = sorted.indexOfFirst {
            !it.played &&
                    (it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId)
        }
        if (nextIdx >= 0) binding.recycler.scrollToPosition(nextIdx)
    }

    // ── Toque em uma partida ─────────────────────────────────────────────
    private fun handleMatchClick(m: Match) {
        val snap     = GameRepository.snapshot(applicationContext)
        val homeName = snap.times.find { it.id == m.homeTeamId }?.nome ?: m.homeTeamId
        val awayName = snap.times.find { it.id == m.awayTeamId }?.nome ?: m.awayTeamId

        if (m.played) {
            AlertDialog.Builder(this)
                .setTitle("Rodada ${m.round}")
                .setMessage("$homeName  ${m.homeScore} — ${m.awayScore}  $awayName")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val gs        = GameRepository.current()
        val isMyMatch = m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId

        if (isMyMatch) {
            val opponentId = if (m.homeTeamId == gs.managerTeamId) m.awayTeamId else m.homeTeamId
            AlertDialog.Builder(this)
                .setTitle("$homeName vs $awayName")
                .setMessage("Deseja fazer o pick & ban deste mapa?")
                .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                    startPickBanPhase(
                        matchId        = m.id,
                        playerTeamId   = gs.managerTeamId,
                        opponentTeamId = opponentId,
                        mapNumber      = 1
                    )
                }
                .setNegativeButton("Simular direto") { _, _ ->
                    startActivity(
                        Intent(this, MatchSimulationActivity::class.java)
                            .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, m.id)
                    )
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("$homeName vs $awayName")
                .setMessage("Acompanhar partida em tempo acelerado?")
                .setPositiveButton("Assistir") { _, _ ->
                    startActivity(
                        Intent(this, MatchSimulationActivity::class.java)
                            .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, m.id)
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ── Abre o pick & ban para o mapa indicado ───────────────────────────
    private fun startPickBanPhase(
        matchId: String,
        playerTeamId: String,
        opponentTeamId: String,
        mapNumber: Int
    ) {
        pendingMatchId        = matchId
        pendingMapNumber      = mapNumber
        pendingPlayerTeamId   = playerTeamId
        pendingOpponentTeamId = opponentTeamId

        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra("player_team_id",   playerTeamId)
                putExtra("opponent_team_id", opponentTeamId)
                putExtra("match_id",         matchId)
                putExtra("map_number",       mapNumber)
            },
            PickBanActivity.REQUEST_PICK_BAN
        )
    }

    // ── Recebe resultado do PickBanActivity ──────────────────────────────
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PickBanActivity.REQUEST_PICK_BAN
            || resultCode != RESULT_OK
            || data == null) return

        val bluePicks = data.getStringArrayListExtra("blue_picks") ?: arrayListOf()
        val redPicks  = data.getStringArrayListExtra("red_picks")  ?: arrayListOf()
        val blueBans  = data.getStringArrayListExtra("blue_bans")  ?: arrayListOf()
        val redBans   = data.getStringArrayListExtra("red_bans")   ?: arrayListOf()
        val mapNum    = data.getIntExtra("map_number", pendingMapNumber)

        // Salva o pick & ban no Match para histórico
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == pendingMatchId }
        match?.pickBanPlan = PickBanPlan(
            mapNumber = mapNum,
            bluePicks = bluePicks.toList(),
            redPicks  = redPicks.toList(),
            blueBans  = blueBans.toList(),
            redBans   = redBans.toList()
        )

        // Determina picks de cada lado para o simulador
        val playerIsBlue  = (mapNum % 2 == 1)
        val playerPicks   = if (playerIsBlue) bluePicks else redPicks
        val opponentPicks = if (playerIsBlue) redPicks  else bluePicks

        // Simula o mapa e obtém o vencedor
        val mapWinnerTeamId = simulateMapWithPicks(
            mapNumber     = mapNum,
            playerPicks   = playerPicks,
            opponentPicks = opponentPicks,
            playerIsBlue  = playerIsBlue
        )

        // Atualiza o placar parcial da série em gs.seriesState
        val prevSeries    = gs.seriesState[pendingMatchId] ?: SeriesState()
        val updatedSeries = prevSeries.recordMap(mapWinnerTeamId == pendingPlayerTeamId)
        gs.seriesState[pendingMatchId] = updatedSeries

        val snap         = GameRepository.snapshot(applicationContext)
        val playerName   = snap.times.find { it.id == pendingPlayerTeamId }?.nome  ?: "Você"
        val opponentName = snap.times.find { it.id == pendingOpponentTeamId }?.nome ?: "Oponente"
        val pw           = updatedSeries.playerWins
        val ow           = updatedSeries.opponentWins

        when {
            updatedSeries.isFinished -> {
                finalizeMatch(pendingMatchId, pw, ow)
                showSeriesResult(playerName, opponentName, pw, ow)
            }
            else -> {
                val mapMsg = if (mapWinnerTeamId == pendingPlayerTeamId)
                    "✅ Mapa $mapNum: você venceu!"
                else
                    "❌ Mapa $mapNum: oponente venceu."

                AlertDialog.Builder(this)
                    .setTitle("Resultado — Mapa $mapNum  ($pw–$ow)")
                    .setMessage("$mapMsg\n\nContinuar para o Mapa ${mapNum + 1}?")
                    .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                        startPickBanPhase(
                            matchId        = pendingMatchId,
                            playerTeamId   = pendingPlayerTeamId,
                            opponentTeamId = pendingOpponentTeamId,
                            mapNumber      = mapNum + 1
                        )
                    }
                    .setNegativeButton("Simular restante") { _, _ ->
                        startActivity(
                            Intent(this, MatchSimulationActivity::class.java)
                                .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, pendingMatchId)
                        )
                    }
                    .show()
            }
        }
    }

    // ── Simula um mapa com bônus pelos picks escolhidos ──────────────────
    private fun simulateMapWithPicks(
        mapNumber: Int,
        playerPicks: List<String>,
        opponentPicks: List<String>,
        playerIsBlue: Boolean
    ): String {
        // Overall médio do elenco titular de cada time (usa overallRating() de Player)
        fun avgOverall(teamId: String): Double {
            val roster = GameRepository.rosterOf(applicationContext, teamId).filter { it.titular }
            return if (roster.isEmpty()) 75.0
            else roster.map { it.overallRating().toDouble() }.average()
        }

        val playerBase   = avgOverall(pendingPlayerTeamId)
        val opponentBase = avgOverall(pendingOpponentTeamId)

        // Bônus por picks confirmados: cada pick vale +1 ponto (máx +5)
        val playerBonus   = playerPicks.size.coerceAtMost(5).toDouble()
        val opponentBonus = opponentPicks.size.coerceAtMost(5).toDouble()

        // Vantagem lado azul (+2), alterna por mapa
        val playerStrength   = playerBase   + playerBonus   + if (playerIsBlue)  2.0 else 0.0
        val opponentStrength = opponentBase + opponentBonus + if (!playerIsBlue) 2.0 else 0.0

        // Ruído ±8 para variância
        val noise = (-8..8).random().toDouble()

        return if (playerStrength + noise > opponentStrength)
            pendingPlayerTeamId
        else
            pendingOpponentTeamId
    }

    // ── Grava resultado final no GameRepository e salva ──────────────────
    private fun finalizeMatch(matchId: String, playerScore: Int, opponentScore: Int) {
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: return

        val playerIsHome = match.homeTeamId == pendingPlayerTeamId
        match.homeScore  = if (playerIsHome) playerScore   else opponentScore
        match.awayScore  = if (playerIsHome) opponentScore else playerScore
        match.played     = true

        gs.seriesState.remove(matchId)

        GameRepository.save(applicationContext)
        refreshList()
    }

    // ── Dialog de resultado final da série ───────────────────────────────
    private fun showSeriesResult(
        playerName: String,
        opponentName: String,
        playerWins: Int,
        opponentWins: Int
    ) {
        AlertDialog.Builder(this)
            .setTitle(if (playerWins > opponentWins) "🏆 Vitória!" else "💔 Derrota")
            .setMessage("$playerName  $playerWins — $opponentWins  $opponentName")
            .setPositiveButton("OK", null)
            .show()
    }

    // ════════════════════════════════════════════════════════════════════
    // Adapter interno — idêntico ao original
    // ════════════════════════════════════════════════════════════════════
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
            val m        = matches[i]
            val homeName = teamNames[m.homeTeamId] ?: m.homeTeamId
            val awayName = teamNames[m.awayTeamId] ?: m.awayTeamId

            h.tvRound.text = "Rodada ${m.round}"
            h.tvDate.text  = LocalDate.parse(m.date).format(dateFmt)
            h.tvHome.text  = homeName
            h.tvAway.text  = awayName
            h.viewHomeBar.setBackgroundColor(TeamColors.forTeam(m.homeTeamId))
            h.viewAwayBar.setBackgroundColor(TeamColors.forTeam(m.awayTeamId))

            val isMine = m.homeTeamId == myTeamId || m.awayTeamId == myTeamId
            h.viewBar.setBackgroundColor(
                if (isMine) Color.parseColor("#C89B3C") else Color.parseColor("#3C3C41")
            )

            when {
                m.played -> {
                    h.tvScore.text = "${m.homeScore}-${m.awayScore}"
                    h.tvScore.setTextColor(Color.parseColor("#F0E6D2"))
                }
                m.date == currentDate -> {
                    h.tvScore.text = "HOJE"
                    h.tvScore.setTextColor(Color.parseColor("#FFB800"))
                }
                m.date < currentDate -> {
                    h.tvScore.text = "—"
                    h.tvScore.setTextColor(Color.parseColor("#A09B8C"))
                }
                else -> {
                    h.tvScore.text = "vs"
                    h.tvScore.setTextColor(Color.parseColor("#A09B8C"))
                }
            }

            h.itemView.setOnClickListener { onMatchClick(m) }
        }
    }
}