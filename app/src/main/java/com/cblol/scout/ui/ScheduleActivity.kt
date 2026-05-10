package com.cblol.scout.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.databinding.ActivityScheduleBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(applicationContext)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = MatchAdapter(
            matches = gs.matches.sortedWith(compareBy({ it.round }, { it.date })),
            currentDate = gs.currentDate,
            myTeamId = gs.managerTeamId,
            teamNames = snap.times.associate { it.id to it.nome }
        )

        // Scroll inicial pra próxima partida do meu time
        val nextIdx = gs.matches
            .sortedWith(compareBy({ it.round }, { it.date }))
            .indexOfFirst { !it.played &&
                (it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId) }
        if (nextIdx >= 0) binding.recycler.scrollToPosition(nextIdx)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private class MatchAdapter(
        private val matches: List<Match>,
        private val currentDate: String,
        private val myTeamId: String,
        private val teamNames: Map<String, String>
    ) : RecyclerView.Adapter<MatchAdapter.VH>() {

        private val dateFmt = DateTimeFormatter.ofPattern("dd/MM")

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val viewBar: View         = v.findViewById(R.id.view_match_round_bar)
            val tvRound: TextView     = v.findViewById(R.id.tv_match_round)
            val tvDate: TextView      = v.findViewById(R.id.tv_match_date)
            val tvHome: TextView      = v.findViewById(R.id.tv_match_home)
            val tvAway: TextView      = v.findViewById(R.id.tv_match_away)
            val tvScore: TextView     = v.findViewById(R.id.tv_match_score)
            val viewHomeBar: View     = v.findViewById(R.id.view_match_home_color)
            val viewAwayBar: View     = v.findViewById(R.id.view_match_away_color)
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

            val isMine = m.homeTeamId == myTeamId || m.awayTeamId == myTeamId
            val accent = if (isMine) Color.parseColor("#0066CC") else Color.parseColor("#9CA3AF")
            h.viewBar.setBackgroundColor(accent)

            if (m.played) {
                h.tvScore.text = "${m.homeScore}-${m.awayScore}"
                h.tvScore.setTextColor(Color.parseColor("#1A1A1A"))
            } else if (m.date == currentDate) {
                h.tvScore.text = "HOJE"
                h.tvScore.setTextColor(Color.parseColor("#FB8C00"))
            } else if (m.date < currentDate) {
                h.tvScore.text = "—"
                h.tvScore.setTextColor(Color.parseColor("#9CA3AF"))
            } else {
                h.tvScore.text = "vs"
                h.tvScore.setTextColor(Color.parseColor("#9CA3AF"))
            }
        }
    }
}
