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
        binding.cardSquad.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(TeamSelectActivity.EXTRA_TEAM_ID, GameRepository.current().managerTeamId))
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
