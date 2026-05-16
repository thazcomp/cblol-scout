package com.cblol.scout.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.util.TeamColors

/**
 * Adapter da lista de jogadores em [MainActivity].
 *
 * SOLID:
 * - **SRP**: cada ViewHolder binda um único Player; helpers ([overallColorRes],
 *   [salarySourceColorRes]) isolam decisões de cor.
 * - **OCP**: novas faixas de overall se adicionam em [overallColorRes] sem mudar onBind.
 *
 * Strings via `R.string`, cores via `R.color`. Sem `Color.parseColor` inline.
 */
class PlayerAdapter(
    private val onItemClick: (Player) -> Unit
) : ListAdapter<Player, PlayerAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView         = view.findViewById(R.id.card_player)
        val teamBar: View          = view.findViewById(R.id.view_team_bar)
        val tvRole: TextView       = view.findViewById(R.id.tv_role)
        val tvName: TextView       = view.findViewById(R.id.tv_player_name)
        val tvFlag: TextView       = view.findViewById(R.id.tv_flag)
        val tvTeam: TextView       = view.findViewById(R.id.tv_team_name)
        val tvOverall: TextView    = view.findViewById(R.id.tv_overall)
        val tvGames: TextView      = view.findViewById(R.id.tv_stat_games)
        val tvKda: TextView        = view.findViewById(R.id.tv_stat_kda)
        val tvCs: TextView         = view.findViewById(R.id.tv_stat_cs)
        val tvKp: TextView         = view.findViewById(R.id.tv_stat_kp)
        val pbLane: ProgressBar    = view.findViewById(R.id.pb_lane)
        val pbTf: ProgressBar      = view.findViewById(R.id.pb_tf)
        val pbCria: ProgressBar    = view.findViewById(R.id.pb_cria)
        val pbCons: ProgressBar    = view.findViewById(R.id.pb_cons)
        val pbClutch: ProgressBar  = view.findViewById(R.id.pb_clutch)
        val tvLaneVal: TextView    = view.findViewById(R.id.tv_lane_val)
        val tvTfVal: TextView      = view.findViewById(R.id.tv_tf_val)
        val tvCriaVal: TextView    = view.findViewById(R.id.tv_cria_val)
        val tvConsVal: TextView    = view.findViewById(R.id.tv_cons_val)
        val tvClutchVal: TextView  = view.findViewById(R.id.tv_clutch_val)
        val tvSalary: TextView     = view.findViewById(R.id.tv_salary)
        val tvSalarySource: TextView = view.findViewById(R.id.tv_salary_source)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = getItem(position)
        val ctx    = holder.itemView.context

        bindHeader(holder, player)
        bindOverall(holder, player, ctx)
        bindStats(holder, player, ctx)
        bindAttributes(holder, player)
        bindSalary(holder, player, ctx)

        holder.card.setOnClickListener { onItemClick(player) }
    }

    private fun bindHeader(holder: ViewHolder, player: Player) {
        holder.teamBar.setBackgroundColor(TeamColors.forTeam(player.time_id))
        holder.tvRole.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ROLE_BG_CORNER
            setColor(TeamColors.roleColor(player.role))
        }
        holder.tvRole.text = player.role
        holder.tvName.text = player.nome_jogo
        holder.tvFlag.text = TeamColors.flagEmoji(player.nacionalidade)
        holder.tvTeam.text = player.time_nome
    }

    private fun bindOverall(holder: ViewHolder, player: Player, ctx: android.content.Context) {
        val overall = player.overallRating()
        holder.tvOverall.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(ctx, overallColorRes(overall)))
        }
        holder.tvOverall.text = overall.toString()
    }

    private fun bindStats(holder: ViewHolder, player: Player, ctx: android.content.Context) {
        val s = player.stats_brutas
        holder.tvGames.text = ctx.getString(R.string.stat_games_format, s.jogos)
        holder.tvKda.text   = ctx.getString(R.string.stat_kda_format, s.kda.toString())
        holder.tvCs.text    = ctx.getString(R.string.stat_cs_format,  s.cs_min.toString())
        holder.tvKp.text    = ctx.getString(R.string.stat_kp_format,  s.kp_pct.toInt())
    }

    private fun bindAttributes(holder: ViewHolder, player: Player) {
        val a = player.atributos_derivados
        holder.pbLane.progress   = a.lane_phase;   holder.tvLaneVal.text   = a.lane_phase.toString()
        holder.pbTf.progress     = a.team_fight;   holder.tvTfVal.text     = a.team_fight.toString()
        holder.pbCria.progress   = a.criatividade; holder.tvCriaVal.text   = a.criatividade.toString()
        holder.pbCons.progress   = a.consistencia; holder.tvConsVal.text   = a.consistencia.toString()
        holder.pbClutch.progress = a.clutch;       holder.tvClutchVal.text = a.clutch.toString()
    }

    private fun bindSalary(holder: ViewHolder, player: Player, ctx: android.content.Context) {
        val salary = player.contrato.salario_mensal_estimado_brl ?: return
        holder.tvSalary.text       = ctx.getString(R.string.hub_payroll_format, "%,d".format(salary))
        holder.tvSalarySource.text = player.contrato.fonte_salario
        holder.tvSalarySource.setTextColor(
            ContextCompat.getColor(ctx, salarySourceColorRes(player.contrato.fonte_salario))
        )
    }

    @ColorRes
    private fun overallColorRes(ovr: Int): Int = when {
        ovr >= GameConstants.Economy.OVERALL_BRACKET_MYTHIC    -> R.color.rarity_mythic
        ovr >= GameConstants.Economy.OVERALL_BRACKET_LEGENDARY -> R.color.rarity_legendary
        ovr >= GameConstants.Economy.OVERALL_BRACKET_EPIC      -> R.color.rarity_epic
        else                                                   -> R.color.rarity_rare
    }

    @ColorRes
    private fun salarySourceColorRes(source: String): Int =
        if (source == SALARY_SOURCE_REPORTED) R.color.salary_reported else R.color.salary_estimated

    object DiffCallback : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(a: Player, b: Player) = a.id == b.id
        override fun areContentsTheSame(a: Player, b: Player) = a == b
    }

    companion object {
        private const val ROLE_BG_CORNER = 24f
        private const val SALARY_SOURCE_REPORTED = "reportado"
    }
}
