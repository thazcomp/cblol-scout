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
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.databinding.ActivityManagerHubBinding
import com.cblol.scout.ui.viewmodel.ManagerHubViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ManagerHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerHubBinding
    private val vm: ManagerHubViewModel by viewModel()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private var pendingMatchId: String = ""
    private var pendingMapNumber: Int = 1
    private var pendingPlayerTeamId: String = ""
    private var pendingOpponentTeamId: String = ""

    private var autoORmanualPicksDialog: AlertDialog? = null
    private var confirmQuitDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerHubBinding.inflate(layoutInflater)
        pendingMatchId = savedInstanceState?.getString("pendingMatchId") ?: ""
        pendingMapNumber = savedInstanceState?.getInt("pendingMapNumber", 1) ?: 1
        pendingPlayerTeamId = savedInstanceState?.getString("pendingPlayerTeamId") ?: ""
        pendingOpponentTeamId = savedInstanceState?.getString("pendingOpponentTeamId") ?: ""
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        vm.sessionReady.observe(this) { ready ->
            if (!ready) {
                startActivity(Intent(this, TeamSelectActivity::class.java))
                finish()
            }
        }

        vm.hubState.observe(this) { state ->
            binding.toolbar.title    = state.teamName
            binding.toolbar.subtitle = "Técnico: ${state.managerName}"
            binding.toolbar.setBackgroundColor(TeamColors.forTeam(state.teamId))
            binding.toolbar.setTitleTextColor(Color.WHITE)
            binding.toolbar.setSubtitleTextColor(Color.WHITE)

            binding.tvDate.text   = LocalDate.parse(state.currentDate).format(dateFormatter)
            binding.tvBudget.text = "R$ ${"%,d".format(state.budget)}"
            binding.tvBudget.setTextColor(
                if (state.budget < 0) Color.parseColor("#E84057") else Color.parseColor("#C89B3C")
            )
            binding.tvPayroll.text   = "R$ ${"%,d".format(state.monthlyPayroll)} / mês"
            binding.tvSquadInfo.text = "${state.rosterSize} jogadores (${state.starterCount} titulares)"

            val next = state.nextMatch
            if (next != null) {
                val daysUntil = LocalDate.parse(state.currentDate)
                    .until(LocalDate.parse(next.date)).days
                binding.tvNextMatch.text     = next.label
                binding.tvNextMatchDate.text = "${LocalDate.parse(next.date).format(dateFormatter)} · Rodada ${next.round} · em $daysUntil dias"
                binding.btnPlayNextMatch.isEnabled = true
            } else {
                binding.tvNextMatch.text     = "Sem partidas pendentes"
                binding.tvNextMatchDate.text = "Split encerrado"
                binding.btnPlayNextMatch.isEnabled = false
            }

            binding.recyclerLog.layoutManager = LinearLayoutManager(this)
            binding.recyclerLog.adapter = LogAdapter(state.log)
        }

        binding.btnPlayNextMatch.setOnClickListener { openPickBanOrSim() }
        binding.cardSquad.setOnClickListener      { startActivity(Intent(this, SquadActivity::class.java)) }
        binding.cardMarket.setOnClickListener     { startActivity(Intent(this, TransferMarketActivity::class.java)) }
        binding.cardSchedule.setOnClickListener   { startActivity(Intent(this, ScheduleActivity::class.java)) }
        binding.cardStandings.setOnClickListener  { startActivity(Intent(this, StandingsActivity::class.java)) }
        binding.btnQuit.setOnClickListener        { confirmQuit() }

        vm.init()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun openPickBanOrSim() {
        val next = vm.hubState.value?.nextMatch ?: return
        val playerTeamId = com.cblol.scout.game.GameRepository.current().managerTeamId

        autoORmanualPicksDialog = AlertDialog.Builder(this)
            .setTitle(next.label)
            .setMessage("Deseja fazer o pick & ban antes da partida?")
            .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                pendingMatchId        = next.matchId
                pendingMapNumber      = 1
                pendingPlayerTeamId   = playerTeamId
                pendingOpponentTeamId = next.opponentId
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this, PickBanActivity::class.java).apply {
                        putExtra("player_team_id",   playerTeamId)
                        putExtra("opponent_team_id", next.opponentId)
                        putExtra("match_id",         next.matchId)
                        putExtra("map_number",       1)
                    },
                    PickBanActivity.REQUEST_PICK_BAN
                )
            }
            .setNegativeButton("Simular direto") { _, _ ->
                // Limpa as variáveis de estado antes de simular direto
                // para evitar conflitos quando se volta de MatchResultActivity
                pendingMatchId        = ""
                pendingMapNumber      = 1
                pendingPlayerTeamId   = ""
                pendingOpponentTeamId = ""
                startActivity(Intent(this, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, next.matchId))
            }
            .show()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return
        if (pendingMatchId.isEmpty() || pendingPlayerTeamId.isEmpty()) return

        val bluePicks = data.getStringArrayListExtra("blue_picks")?.toList() ?: return
        val redPicks  = data.getStringArrayListExtra("red_picks")?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra("blue_bans")?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra("red_bans")?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra("map_number", pendingMapNumber)

        // Salva o plano no match para que o LiveMatchEngine use os campeões escolhidos
        val gs = com.cblol.scout.game.GameRepository.current()
        gs.matches.find { it.id == pendingMatchId }?.pickBanPlan =
            PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans)
        com.cblol.scout.game.GameRepository.save(applicationContext)

        // Abre o MatchSimulationActivity — ele lê o plano e simula com os campeões escolhidos
        startActivity(
            Intent(this, MatchSimulationActivity::class.java)
                .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, pendingMatchId)
        )
    }

    private fun confirmQuit() {
        confirmQuitDialog = AlertDialog.Builder(this)
            .setTitle("Encerrar carreira?")
            .setMessage("Isso apagará seu save permanentemente.")
            .setPositiveButton("Sim, encerrar") { _, _ ->
                vm.clearCareer()
                startActivity(Intent(this, TeamSelectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private class LogAdapter(private val items: List<LogEntry>) :
        RecyclerView.Adapter<LogAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView = v.findViewById(R.id.tv_log_icon)
            val tvDate: TextView = v.findViewById(R.id.tv_log_date)
            val tvMsg: TextView  = v.findViewById(R.id.tv_log_msg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val e = items[i]
            h.tvIcon.text = when (e.type) {
                "MATCH" -> "⚔️"; "TRANSFER" -> "🔄"; "ECONOMY" -> "💰"
                "CONTRACT" -> "📝"; "SQUAD" -> "👥"; "CAREER" -> "🎯"; else -> "•"
            }
            h.tvDate.text = e.date
            h.tvMsg.text  = e.message
        }
    }

    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingMatchId", pendingMatchId)
    }

    override fun onDestroy() {
        if (autoORmanualPicksDialog?.isShowing == true) autoORmanualPicksDialog?.dismiss()
        if (confirmQuitDialog?.isShowing == true) confirmQuitDialog?.dismiss()
        super.onDestroy()
    }
}
