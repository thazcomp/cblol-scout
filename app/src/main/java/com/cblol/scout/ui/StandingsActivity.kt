package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.Typeface
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
import com.cblol.scout.data.Standing
import com.cblol.scout.databinding.ActivityStandingsBinding
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.StandingsViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Tabela de classificação do split.
 *
 * SOLID:
 * - **SRP**: render isolado em [renderStandings]; adapter contém apenas binding.
 * - **DIP**: depende de [StandingsViewModel] via Koin; cores via `R.color`.
 *
 * Strings em `R.string.*`, cores em `R.color.*`. Sem `Color.parseColor` inline.
 */
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

        vm.standings.observe(this) { renderStandings(it, myTeamId) }
        vm.load()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun renderStandings(standings: List<Standing>, myTeamId: String) {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = StandingsAdapter(standings, myTeamId)
    }

    // ── Adapter ──────────────────────────────────────────────────────────

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
            val s   = items[i]
            val ctx = h.itemView.context
            h.tvPos.text  = (i + 1).toString()
            h.tvName.text = s.teamName
            h.tvW.text    = s.wins.toString()
            h.tvL.text    = s.losses.toString()
            h.tvDiff.text = if (s.mapDiff >= 0)
                ctx.getString(R.string.player_diff_positive, s.mapDiff)
            else s.mapDiff.toString()
            h.viewBar.setBackgroundColor(TeamColors.forTeam(s.teamId))

            val isMine = s.teamId == myTeamId
            h.itemView.setBackgroundColor(
                if (isMine) color(ctx, R.color.color_primary_container) else Color.TRANSPARENT
            )
            h.tvName.setTypeface(null, if (isMine) Typeface.BOLD else Typeface.NORMAL)
            h.tvName.setTextColor(color(ctx,
                if (isMine) R.color.color_primary else R.color.color_on_surface))

            h.tvPos.setTextColor(color(ctx,
                if (i < TOP_PLAYOFF_POSITIONS) R.color.color_primary
                else R.color.color_on_surface_variant))
        }

        private fun color(ctx: android.content.Context, @ColorRes res: Int) =
            ContextCompat.getColor(ctx, res)

        companion object {
            /** Posições que classificam para playoffs no CBLOL (top 6). */
            private const val TOP_PLAYOFF_POSITIONS = 6
        }
    }
}
