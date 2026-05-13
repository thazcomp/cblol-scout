package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.Typeface
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
import com.cblol.scout.ui.viewmodel.StandingsViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel

class StandingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStandingsBinding
    private val vm: StandingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStandingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        GameRepository.load(applicationContext)
        val myTeamId = GameRepository.current().managerTeamId

        vm.standings.observe(this) { standings ->
            binding.recycler.layoutManager = LinearLayoutManager(this)
            binding.recycler.adapter = StandingsAdapter(standings, myTeamId)
        }

        vm.load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private class StandingsAdapter(
        private val items: List<Standing>,
        private val myTeamId: String
    ) : RecyclerView.Adapter<StandingsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvPos: TextView  = v.findViewById(R.id.tv_st_pos)
            val viewBar: View    = v.findViewById(R.id.view_st_color)
            val tvName: TextView = v.findViewById(R.id.tv_st_team)
            val tvW: TextView    = v.findViewById(R.id.tv_st_w)
            val tvL: TextView    = v.findViewById(R.id.tv_st_l)
            val tvDiff: TextView = v.findViewById(R.id.tv_st_diff)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_standing, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val s = items[i]
            h.tvPos.text  = (i + 1).toString()
            h.tvName.text = s.teamName
            h.tvW.text    = s.wins.toString()
            h.tvL.text    = s.losses.toString()
            h.tvDiff.text = (if (s.mapDiff >= 0) "+" else "") + s.mapDiff.toString()
            h.viewBar.setBackgroundColor(TeamColors.forTeam(s.teamId))

            if (s.teamId == myTeamId) {
                h.itemView.setBackgroundColor(Color.parseColor("#1E2D40"))
                h.tvName.setTypeface(null, Typeface.BOLD)
                h.tvName.setTextColor(Color.parseColor("#C89B3C"))
            } else {
                h.itemView.setBackgroundColor(Color.TRANSPARENT)
                h.tvName.setTypeface(null, Typeface.NORMAL)
                h.tvName.setTextColor(Color.parseColor("#F0E6D2"))
            }

            h.tvPos.setTextColor(
                if (i < 6) Color.parseColor("#C89B3C") else Color.parseColor("#A09B8C")
            )
        }
    }
}
