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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.Match
import com.cblol.scout.data.SeriesState
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.databinding.ActivityManagerHubBinding
import com.cblol.scout.game.AdvanceReport
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela central do modo carreira. Mostra:
 *  - Status do time (data atual, orçamento, próxima partida)
 *  - Botões pra acessar elenco, mercado, calendário, classificação
 *  - "Avançar dia" (1d ou até a próxima partida)
 *  - Log de eventos recentes
 */
class ManagerHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerHubBinding
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Estado de série em andamento (pick & ban lançado daqui)
    private var pendingMatchId: String = ""
    private var pendingMapNumber: Int = 1
    private var pendingPlayerTeamId: String = ""
    private var pendingOpponentTeamId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerHubBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (GameRepository.load(applicationContext) == null) {
            // Sem save — volta pra seleção
            startActivity(Intent(this, TeamSelectActivity::class.java))
            finish()
            return
        }

        binding.btnAdvanceDay.setOnClickListener { advanceDays(1) }
        binding.btnAdvanceWeek.setOnClickListener { advanceDays(7) }
        binding.btnAdvanceMatch.setOnClickListener { advanceUntilNextMatch() }
        binding.tvNextMatch.setOnClickListener { openLiveSimForNextMatch() }
        binding.tvNextMatchDate.setOnClickListener { openLiveSimForNextMatch() }
        binding.cardSquad.setOnClickListener {
            startActivity(Intent(this, SquadActivity::class.java))
        }
        binding.cardMarket.setOnClickListener {
            startActivity(Intent(this, TransferMarketActivity::class.java))
        }
        binding.cardSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        binding.cardStandings.setOnClickListener {
            startActivity(Intent(this, StandingsActivity::class.java))
        }
        binding.btnQuit.setOnClickListener { confirmQuit() }
    }

    override fun onResume() {
        super.onResume()
        refreshHud()
    }

    private fun refreshHud() {
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val team = snap.times.find { it.id == gs.managerTeamId }!!

        binding.toolbar.title = team.nome
        binding.toolbar.subtitle = "Técnico: ${gs.managerName}"
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(team.id))
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.toolbar.setSubtitleTextColor(Color.WHITE)

        binding.tvDate.text = LocalDate.parse(gs.currentDate).format(dateFormatter)
        binding.tvBudget.text = "R$ ${"%,d".format(gs.budget)}"
        binding.tvBudget.setTextColor(
            if (gs.budget < 0) Color.parseColor("#E84057") else Color.parseColor("#C89B3C")
        )

        val payroll = GameEngine.totalMonthlyPayroll(applicationContext)
        binding.tvPayroll.text = "R$ ${"%,d".format(payroll)} / mês"

        val rosterSize = GameRepository.rosterOf(applicationContext, gs.managerTeamId).size
        val starters = GameRepository.rosterOf(applicationContext, gs.managerTeamId)
            .count { it.titular }
        binding.tvSquadInfo.text = "$rosterSize jogadores ($starters titulares)"

        // Próxima partida
        val next = GameEngine.nextMatchForManager()
        if (next != null) {
            val home = snap.times.find { it.id == next.homeTeamId }!!.nome
            val away = snap.times.find { it.id == next.awayTeamId }!!.nome
            val dateStr = LocalDate.parse(next.date).format(dateFormatter)
            val daysUntil = LocalDate.parse(gs.currentDate).until(LocalDate.parse(next.date)).days
            binding.tvNextMatch.text = "$home  vs  $away"
            binding.tvNextMatchDate.text = "$dateStr · Rodada ${next.round} · em $daysUntil dias"
        } else {
            binding.tvNextMatch.text = "Sem partidas pendentes"
            binding.tvNextMatchDate.text = "Split encerrado"
        }

        // Log
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = LogAdapter(gs.gameLog.take(30))
    }

    private fun advanceDays(days: Int) {
        binding.btnAdvanceDay.isEnabled = false
        binding.btnAdvanceWeek.isEnabled = false
        binding.btnAdvanceMatch.isEnabled = false

        lifecycleScope.launch {
            val report = withContext(Dispatchers.Default) {
                GameEngine.advanceDays(applicationContext, days)
            }
            refreshHud()
            showAdvanceReport(report)
            binding.btnAdvanceDay.isEnabled = true
            binding.btnAdvanceWeek.isEnabled = true
            binding.btnAdvanceMatch.isEnabled = true
        }
    }

    private fun advanceUntilNextMatch() {
        val next = GameEngine.nextMatchForManager() ?: return
        val days = LocalDate.parse(GameRepository.current().currentDate)
            .until(LocalDate.parse(next.date)).days + 1
        if (days > 0) advanceDays(days)
    }

    /** Abre pick & ban (se for partida do meu time) ou simulador direto. */
    private fun openLiveSimForNextMatch() {
        val next = GameEngine.nextMatchForManager() ?: return
        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val homeName = snap.times.find { it.id == next.homeTeamId }?.nome ?: next.homeTeamId
        val awayName = snap.times.find { it.id == next.awayTeamId }?.nome ?: next.awayTeamId
        val opponentId = if (next.homeTeamId == gs.managerTeamId) next.awayTeamId else next.homeTeamId

        AlertDialog.Builder(this)
            .setTitle("$homeName vs $awayName")
            .setMessage("Deseja fazer o pick & ban antes da partida?")
            .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                pendingMatchId        = next.id
                pendingMapNumber      = 1
                pendingPlayerTeamId   = gs.managerTeamId
                pendingOpponentTeamId = opponentId
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this, PickBanActivity::class.java).apply {
                        putExtra("player_team_id",   gs.managerTeamId)
                        putExtra("opponent_team_id", opponentId)
                        putExtra("match_id",         next.id)
                        putExtra("map_number",       1)
                    },
                    PickBanActivity.REQUEST_PICK_BAN
                )
            }
            .setNegativeButton("Simular direto") { _, _ ->
                startActivity(
                    Intent(this, MatchSimulationActivity::class.java)
                        .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, next.id)
                )
            }
            .show()
    }

    private fun showAdvanceReport(r: AdvanceReport) {
        if (r.matchesPlayed == 0 && r.income == 0L && r.expense == 0L) return
        val sb = StringBuilder()
        if (r.matchesPlayed > 0) sb.appendLine("⚔️  ${r.matchesPlayed} partida(s) simulada(s)")
        if (r.myWin) sb.appendLine("🏆 Seu time venceu!")
        if (r.myLoss) sb.appendLine("💔 Seu time perdeu.")
        if (r.income > 0) sb.appendLine("💵 +R$ ${"%,d".format(r.income)}")
        if (r.expense > 0) sb.appendLine("💸 −R$ ${"%,d".format(r.expense)}")

        AlertDialog.Builder(this)
            .setTitle("Resumo")
            .setMessage(sb.toString().trim())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setTitle("Encerrar carreira?")
            .setMessage("Isso apagará seu save permanentemente.")
            .setPositiveButton("Sim, encerrar") { _, _ ->
                GameRepository.clear(applicationContext)
                startActivity(Intent(this, TeamSelectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return

        val bluePicks = data.getStringArrayListExtra("blue_picks") ?: arrayListOf()
        val redPicks  = data.getStringArrayListExtra("red_picks")  ?: arrayListOf()
        val blueBans  = data.getStringArrayListExtra("blue_bans")  ?: arrayListOf()
        val redBans   = data.getStringArrayListExtra("red_bans")   ?: arrayListOf()
        val mapNum    = data.getIntExtra("map_number", pendingMapNumber)

        // Salva o pick & ban no Match
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == pendingMatchId }
        match?.pickBanPlan = PickBanPlan(
            mapNumber = mapNum,
            bluePicks = bluePicks.toList(),
            redPicks  = redPicks.toList(),
            blueBans  = blueBans.toList(),
            redBans   = redBans.toList()
        )

        // Simula o mapa
        val playerIsBlue  = (mapNum % 2 == 1)
        val playerPicks   = if (playerIsBlue) bluePicks else redPicks
        val opponentPicks = if (playerIsBlue) redPicks  else bluePicks
        val mapWinner     = simulateMapWithPicks(mapNum, playerPicks, opponentPicks, playerIsBlue)

        val prev    = gs.seriesState[pendingMatchId] ?: SeriesState()
        val updated = prev.recordMap(mapWinner == pendingPlayerTeamId)
        gs.seriesState[pendingMatchId] = updated

        val snap         = GameRepository.snapshot(applicationContext)
        val playerName   = snap.times.find { it.id == pendingPlayerTeamId }?.nome  ?: "Você"
        val opponentName = snap.times.find { it.id == pendingOpponentTeamId }?.nome ?: "Oponente"
        val pw = updated.playerWins
        val ow = updated.opponentWins

        when {
            updated.isFinished -> {
                finalizeMatch(pendingMatchId, pw, ow)
                AlertDialog.Builder(this)
                    .setTitle(if (pw > ow) "🏆 Vitória!" else "💔 Derrota")
                    .setMessage("$playerName  $pw — $ow  $opponentName")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> {
                val msg = if (mapWinner == pendingPlayerTeamId)
                    "✅ Mapa $mapNum: você venceu!"
                else
                    "❌ Mapa $mapNum: oponente venceu."
                AlertDialog.Builder(this)
                    .setTitle("Resultado — Mapa $mapNum  ($pw–$ow)")
                    .setMessage("$msg\n\nContinuar para o Mapa ${mapNum + 1}?")
                    .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                        pendingMapNumber = mapNum + 1
                        @Suppress("DEPRECATION")
                        startActivityForResult(
                            Intent(this, PickBanActivity::class.java).apply {
                                putExtra("player_team_id",   pendingPlayerTeamId)
                                putExtra("opponent_team_id", pendingOpponentTeamId)
                                putExtra("match_id",         pendingMatchId)
                                putExtra("map_number",       mapNum + 1)
                            },
                            PickBanActivity.REQUEST_PICK_BAN
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

    private fun simulateMapWithPicks(
        mapNumber: Int,
        playerPicks: List<String>,
        opponentPicks: List<String>,
        playerIsBlue: Boolean
    ): String {
        fun avgOverall(teamId: String): Double {
            val roster = GameRepository.rosterOf(applicationContext, teamId).filter { it.titular }
            return if (roster.isEmpty()) 75.0 else roster.map { it.overallRating().toDouble() }.average()
        }
        val playerStr   = avgOverall(pendingPlayerTeamId)   + playerPicks.size.coerceAtMost(5)   + if (playerIsBlue)  2.0 else 0.0
        val opponentStr = avgOverall(pendingOpponentTeamId) + opponentPicks.size.coerceAtMost(5) + if (!playerIsBlue) 2.0 else 0.0
        return if (playerStr + (-8..8).random() > opponentStr) pendingPlayerTeamId else pendingOpponentTeamId
    }

    private fun finalizeMatch(matchId: String, playerScore: Int, opponentScore: Int) {
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: return
        val playerIsHome = match.homeTeamId == pendingPlayerTeamId
        match.homeScore  = if (playerIsHome) playerScore   else opponentScore
        match.awayScore  = if (playerIsHome) opponentScore else playerScore
        match.played     = true
        gs.seriesState.remove(matchId)
        GameRepository.save(applicationContext)
        refreshHud()
    }

    /** Adapter inline pro log de eventos. */
    private class LogAdapter(private val items: List<LogEntry>) :
        RecyclerView.Adapter<LogAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView = v.findViewById(R.id.tv_log_icon)
            val tvDate: TextView = v.findViewById(R.id.tv_log_date)
            val tvMsg: TextView = v.findViewById(R.id.tv_log_msg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val e = items[i]
            h.tvIcon.text = when (e.type) {
                "MATCH" -> "⚔️"
                "TRANSFER" -> "🔄"
                "ECONOMY" -> "💰"
                "CONTRACT" -> "📝"
                "SQUAD" -> "👥"
                "CAREER" -> "🎯"
                else -> "•"
            }
            h.tvDate.text = e.date
            h.tvMsg.text = e.message
        }
    }
}
