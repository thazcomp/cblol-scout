package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

/**
 * Calendário do split. Mostra todas as 56 partidas em ordem de rodada/data.
 * Permite simular, fazer pick & ban manual ou assistir partidas de outros times.
 *
 * SOLID:
 * - **SRP**: clique em partida é roteado por [handleMatchClick] →
 *   [showPlayedDialog] / [showMyMatchDialog] / [showOtherMatchDialog].
 * - **DIP**: depende de [ScheduleViewModel] (Koin); strings/cores via res.
 */
class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding
    private val vm: ScheduleViewModel by viewModel()

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        observeViewModel()
        vm.loadMatches()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshMatches()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Observers ────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.matches.observe(this) { renderList(it) }
        vm.event.observe(this) { event ->
            when (event) {
                is ScheduleEvent.ShowResult -> startActivity(event.result.toResultIntent(this))
                is ScheduleEvent.LaunchSimulation -> launchSimulation(event.matchId)
            }
        }
    }

    private fun launchSimulation(matchId: String) {
        startActivity(Intent(this, MatchSimulationActivity::class.java)
            .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId))
    }

    // ── Lista de partidas ────────────────────────────────────────────────

    private fun renderList(matches: List<Match>) {
        val gs   = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)
        val sorted = matches.sortedWith(compareBy({ it.round }, { it.date }))

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = MatchAdapter(
            matches      = sorted,
            currentDate  = gs.currentDate,
            myTeamId     = gs.managerTeamId,
            teamNames    = snap.times.associate { it.id to it.nome },
            onMatchClick = { m -> handleMatchClick(m) }
        )

        val nextIdx = sorted.indexOfFirst {
            !it.played &&
                (it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId)
        }
        if (nextIdx >= 0) binding.recycler.scrollToPosition(nextIdx)
    }

    // ── Clique em uma partida ────────────────────────────────────────────

    private fun handleMatchClick(m: Match) {
        val snap     = GameRepository.snapshot(applicationContext)
        val gs       = GameRepository.current()
        val homeName = snap.times.find { it.id == m.homeTeamId }?.nome ?: m.homeTeamId
        val awayName = snap.times.find { it.id == m.awayTeamId }?.nome ?: m.awayTeamId

        when {
            m.played -> showPlayedDialog(m, homeName, awayName)
            m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId ->
                showMyMatchDialog(m, gs.managerTeamId, homeName, awayName)
            else ->
                showOtherMatchDialog(m, homeName, awayName)
        }
    }

    private fun showPlayedDialog(m: Match, homeName: String, awayName: String) {
        stylizedDialog(this)
            .setTitle(getString(R.string.schedule_round, m.round))
            .setMessage(getString(R.string.schedule_match_score_msg,
                homeName, m.homeScore, m.awayScore, awayName))
            .setPositiveButton(R.string.btn_ok, null).show()
    }

    private fun showMyMatchDialog(m: Match, managerTeamId: String, homeName: String, awayName: String) {
        val opponentId = if (m.homeTeamId == managerTeamId) m.awayTeamId else m.homeTeamId
        stylizedDialog(this)
            .setTitle(getString(R.string.schedule_vs_title, homeName, awayName))
            .setMessage(R.string.dialog_pickban_question)
            .setPositiveButton(R.string.btn_do_pickban) { _, _ ->
                openPickBan(m.id, managerTeamId, opponentId, 1)
            }
            .setNegativeButton(R.string.btn_skip_simulation) { _, _ ->
                launchSimulation(m.id)
            }.show()
    }

    private fun showOtherMatchDialog(m: Match, homeName: String, awayName: String) {
        stylizedDialog(this)
            .setTitle(getString(R.string.schedule_vs_title, homeName, awayName))
            .setMessage(R.string.dialog_watch_match_message)
            .setPositiveButton(R.string.btn_watch) { _, _ -> launchSimulation(m.id) }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun openPickBan(matchId: String, playerTeamId: String, opponentId: String, mapNum: Int) {
        vm.pendingMatchId        = matchId
        vm.pendingMapNumber      = mapNum
        vm.pendingPlayerTeamId   = playerTeamId
        vm.pendingOpponentTeamId = opponentId
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra(PickBanActivity.EXTRA_PLAYER_TEAM_ID,   playerTeamId)
                putExtra(PickBanActivity.EXTRA_OPPONENT_TEAM_ID, opponentId)
                putExtra(PickBanActivity.EXTRA_MATCH_ID,         matchId)
                putExtra(PickBanActivity.EXTRA_MAP_NUMBER,       mapNum)
            },
            PickBanActivity.REQUEST_PICK_BAN
        )
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return

        val bluePicks = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_PICKS)?.toList() ?: return
        val redPicks  = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_PICKS)?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_BANS)?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_BANS)?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra(PickBanActivity.EXTRA_MAP_NUMBER, vm.pendingMapNumber)

        vm.handlePickBanResult(bluePicks, redPicks, blueBans, redBans, mapNum)
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class MatchAdapter(
        private val matches: List<Match>,
        private val currentDate: String,
        private val myTeamId: String,
        private val teamNames: Map<String, String>,
        private val onMatchClick: (Match) -> Unit
    ) : RecyclerView.Adapter<MatchAdapter.VH>() {

        private val dateFmt = DateTimeFormatter.ofPattern(DATE_PATTERN_DM)

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
            val ctx      = h.itemView.context
            val homeName = teamNames[m.homeTeamId] ?: m.homeTeamId
            val awayName = teamNames[m.awayTeamId] ?: m.awayTeamId

            h.tvRound.text = ctx.getString(R.string.schedule_round, m.round)
            h.tvDate.text  = LocalDate.parse(m.date).format(dateFmt)
            h.tvHome.text  = homeName
            h.tvAway.text  = awayName
            h.viewHomeBar.setBackgroundColor(TeamColors.forTeam(m.homeTeamId))
            h.viewAwayBar.setBackgroundColor(TeamColors.forTeam(m.awayTeamId))
            h.viewBar.setBackgroundColor(ContextCompat.getColor(ctx,
                if (m.homeTeamId == myTeamId || m.awayTeamId == myTeamId)
                    R.color.schedule_my_team_bar else R.color.schedule_other_team_bar
            ))

            val (scoreText, scoreColor) = scoreLabel(ctx, m, currentDate)
            h.tvScore.text = scoreText
            h.tvScore.setTextColor(scoreColor)

            h.itemView.setOnClickListener { onMatchClick(m) }
        }

        @ColorRes
        private fun scoreLabel(ctx: android.content.Context, m: Match, today: String): Pair<String, Int> = when {
            m.played -> ctx.getString(R.string.schedule_played_score, m.homeScore, m.awayScore) to
                ContextCompat.getColor(ctx, R.color.schedule_played_text)
            m.date == today -> ctx.getString(R.string.schedule_score_today) to
                ContextCompat.getColor(ctx, R.color.schedule_today_text)
            m.date < today  -> ctx.getString(R.string.schedule_score_missed) to
                ContextCompat.getColor(ctx, R.color.schedule_missed_text)
            else            -> ctx.getString(R.string.schedule_score_vs) to
                ContextCompat.getColor(ctx, R.color.schedule_missed_text)
        }

        companion object {
            private const val DATE_PATTERN_DM = "dd/MM"
        }
    }
}
