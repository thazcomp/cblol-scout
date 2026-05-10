package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.util.TeamColors

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
        val teamColor = TeamColors.forTeam(player.time_id)
        val roleColor = TeamColors.roleColor(player.role)

        // Team accent bar
        holder.teamBar.setBackgroundColor(teamColor)

        // Role badge
        val roleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(roleColor)
        }
        holder.tvRole.background = roleBg
        holder.tvRole.text = player.role

        // Name & flag
        holder.tvName.text = player.nome_jogo
        holder.tvFlag.text = TeamColors.flagEmoji(player.nacionalidade)
        holder.tvTeam.text = player.time_nome

        // Overall rating circle color (raridades estilo LoL)
        val overall = player.overallRating()
        val ratingColor = when {
            overall >= 85 -> Color.parseColor("#C89B3C") // Mythic gold
            overall >= 75 -> Color.parseColor("#0AC8B9") // Legendary cyan
            overall >= 65 -> Color.parseColor("#B19CD9") // Epic purple
            else          -> Color.parseColor("#788CA0") // Rare silver
        }
        val ratingBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ratingColor)
        }
        holder.tvOverall.background = ratingBg
        holder.tvOverall.text = overall.toString()

        // Stats row
        val s = player.stats_brutas
        holder.tvGames.text  = "${s.jogos}J"
        holder.tvKda.text    = "KDA ${s.kda}"
        holder.tvCs.text     = "CS ${s.cs_min}"
        holder.tvKp.text     = "KP ${s.kp_pct.toInt()}%"

        // Attribute bars
        val a = player.atributos_derivados
        holder.pbLane.progress   = a.lane_phase;  holder.tvLaneVal.text   = a.lane_phase.toString()
        holder.pbTf.progress     = a.team_fight;  holder.tvTfVal.text     = a.team_fight.toString()
        holder.pbCria.progress   = a.criatividade; holder.tvCriaVal.text  = a.criatividade.toString()
        holder.pbCons.progress   = a.consistencia; holder.tvConsVal.text  = a.consistencia.toString()
        holder.pbClutch.progress = a.clutch;      holder.tvClutchVal.text = a.clutch.toString()

        // Salary
        val salary = player.contrato.salario_mensal_estimado_brl
        if (salary != null) {
            holder.tvSalary.text = "R$ ${"%,d".format(salary)}/mês"
            holder.tvSalarySource.text = if (player.contrato.fonte_salario == "reportado") "reportado" else "estimado"
            holder.tvSalarySource.setTextColor(
                if (player.contrato.fonte_salario == "reportado")
                    Color.parseColor("#00B894") else Color.parseColor("#C89B3C")
            )
        }

        holder.card.setOnClickListener { onItemClick(player) }
    }

    object DiffCallback : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(a: Player, b: Player) = a.id == b.id
        override fun areContentsTheSame(a: Player, b: Player) = a == b
    }
}
