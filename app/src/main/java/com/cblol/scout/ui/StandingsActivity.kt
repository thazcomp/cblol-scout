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
import com.cblol.scout.data.Standing
import com.cblol.scout.databinding.ActivityStandingsBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.MatchSimulator
import com.cblol.scout.util.TeamColors

class StandingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStandingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        val gs = GameRepository.current()
        val standings = MatchSimulator.computeStandings(applicationContext)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = StandingsAdapter(standings, gs.managerTeamId)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private class StandingsAdapter(
        private val items: List<Standing>,
        private val myTeamId: String
    ) : RecyclerView.Adapter<StandingsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvPos: TextView    = v.findViewById(R.id.tv_st_pos)
            val viewBar: View      = v.findViewById(R.id.view_st_color)
            val tvName: TextView   = v.findViewById(R.id.tv_st_team)
            val tvW: TextView      = v.findViewById(R.id.tv_st_w)
            val tvL: TextView      = v.findViewById(R.id.tv_st_l)
            val tvDiff: TextView   = v.findViewById(R.id.tv_st_diff)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_standing, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val s = items[i]
            h.tvPos.text = (i + 1).toString()
            h.tvName.text = s.teamName
            h.tvW.text = s.wins.toString()
            h.tvL.text = s.losses.toString()
            h.tvDiff.text = (if (s.mapDiff >= 0) "+" else "") + s.mapDiff.toString()
            h.viewBar.setBackgroundColor(TeamColors.forTeam(s.teamId))

            // Destaque pro meu time
            if (s.teamId == myTeamId) {
                h.itemView.setBackgroundColor(Color.parseColor("#E8F2FF"))
                h.tvName.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                h.itemView.setBackgroundColor(Color.TRANSPARENT)
                h.tvName.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            // Top 6 = playoffs
            h.tvPos.setTextColor(
                when {
                    i < 6 -> Color.parseColor("#43A047")
                    else -> Color.parseColor("#9CA3AF")
                }
            )
        }
    }
}
