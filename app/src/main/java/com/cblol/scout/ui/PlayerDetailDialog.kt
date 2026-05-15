package com.cblol.scout.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.game.TransferMarket
import com.cblol.scout.util.TeamColors
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom sheet com os dados detalhados de um jogador. Compartilhado por MainActivity
 * (browsing) e SquadActivity (gerenciamento). Quando há carreira ativa e o jogador
 * é do meu time, mostra ações de gerência (renegociar / vender).
 *
 * `onChanged` é chamado depois de uma ação que muda o estado (renegociação, venda)
 * para que a tela chamadora possa recarregar.
 */
object PlayerDetailDialog {

    fun show(activity: Activity, player: Player, onChanged: () -> Unit = {}, onBuy: ((Player) -> Unit)? = null) {
        val dialog = BottomSheetDialog(activity, R.style.ThemeOverlay_CBLOLScout_BottomSheet)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_player, null)
        dialog.setContentView(view)

        val s = player.stats_brutas
        val a = player.atributos_derivados
        val teamColor = TeamColors.forTeam(player.time_id)

        view.findViewById<View>(R.id.view_bs_team_bar).setBackgroundColor(teamColor)
        view.findViewById<TextView>(R.id.tv_bs_name).text = player.nome_jogo
        view.findViewById<TextView>(R.id.tv_bs_realname).text =
            player.nome_real ?: "Nome real não disponível"
        view.findViewById<TextView>(R.id.tv_bs_team).text = player.time_nome
        view.findViewById<TextView>(R.id.tv_bs_role).text =
            "${player.role}  ${TeamColors.flagEmoji(player.nacionalidade)}  ${player.idade?.let { "$it anos" } ?: ""}"
        view.findViewById<TextView>(R.id.tv_bs_overall).text = player.overallRating().toString()

        view.findViewById<TextView>(R.id.tv_bs_jogos).text  = s.jogos.toString()
        view.findViewById<TextView>(R.id.tv_bs_kda).text    = s.kda.toString()
        view.findViewById<TextView>(R.id.tv_bs_kp).text     = "${s.kp_pct.toInt()}%"
        view.findViewById<TextView>(R.id.tv_bs_cs).text     = s.cs_min.toString()
        view.findViewById<TextView>(R.id.tv_bs_dmg).text    = "${s.damage_share_pct}%"
        view.findViewById<TextView>(R.id.tv_bs_gd15).text   = s.gd15?.let { if (it >= 0) "+$it" else "$it" } ?: "N/A"
        view.findViewById<TextView>(R.id.tv_bs_xpd15).text  = s.xpd15?.let { if (it >= 0) "+$it" else "$it" } ?: "N/A"
        view.findViewById<TextView>(R.id.tv_bs_vis).text    = s.vision_score_min?.toString() ?: "N/A"

        view.findViewById<ProgressBar>(R.id.pb_bs_lane).progress    = a.lane_phase
        view.findViewById<ProgressBar>(R.id.pb_bs_tf).progress      = a.team_fight
        view.findViewById<ProgressBar>(R.id.pb_bs_cria).progress    = a.criatividade
        view.findViewById<ProgressBar>(R.id.pb_bs_cons).progress    = a.consistencia
        view.findViewById<ProgressBar>(R.id.pb_bs_clutch).progress  = a.clutch
        view.findViewById<TextView>(R.id.tv_bs_lane_val).text    = a.lane_phase.toString()
        view.findViewById<TextView>(R.id.tv_bs_tf_val).text      = a.team_fight.toString()
        view.findViewById<TextView>(R.id.tv_bs_cria_val).text    = a.criatividade.toString()
        view.findViewById<TextView>(R.id.tv_bs_cons_val).text    = a.consistencia.toString()
        view.findViewById<TextView>(R.id.tv_bs_clutch_val).text  = a.clutch.toString()

        val salary = player.contrato.salario_mensal_estimado_brl
        view.findViewById<TextView>(R.id.tv_bs_salary).text =
            if (salary != null) "R$ ${"%,d".format(salary)}/mês (${player.contrato.fonte_salario})"
            else "Salário não disponível"

        // Ações de gerência (somente se houver carreira e for jogador do meu time)
        val gs = GameRepository.load(activity.applicationContext)
        val actions = view.findViewById<LinearLayout>(R.id.layout_bs_actions)

        // Se há callback de compra (vindo do mercado de transferências)
        if (onBuy != null) {
            actions.visibility = View.VISIBLE
            actions.removeAllViews()

            // Adiciona divider
            val divider = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply { setMargins(0, 14, 0, 14) }
                setBackgroundColor(activity.resources.getColor(R.color.color_outline_variant, null))
            }
            actions.addView(divider)

            val btnBuy = com.google.android.material.button.MaterialButton(activity).apply {
                text = "Contratar Jogador"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextColor(activity.resources.getColor(android.R.color.white, null))
                setBackgroundColor(activity.resources.getColor(R.color.color_primary, null))
                setOnClickListener {
                    dialog.dismiss()
                    onBuy(player)
                }
            }
            actions.addView(btnBuy)
        } else if (gs != null && player.time_id == gs.managerTeamId) {
            actions.visibility = View.VISIBLE
            view.findViewById<Button>(R.id.btn_bs_toggle_starter).apply {
                text = if (player.titular) "Mover para reserva" else "Promover a titular"
                setOnClickListener {
                    TransferMarket.toggleStarter(activity.applicationContext, player.id)
                    Toast.makeText(activity, "Status atualizado", Toast.LENGTH_SHORT).show()
                    dialog.dismiss(); onChanged()
                }
            }
            view.findViewById<Button>(R.id.btn_bs_renegotiate).setOnClickListener {
                showRenegotiateDialog(activity, player) { onChanged() }
                dialog.dismiss()
            }
            view.findViewById<Button>(R.id.btn_bs_sell).setOnClickListener {
                confirmSell(activity, player) { onChanged() }
                dialog.dismiss()
            }
        } else {
            actions.visibility = View.GONE
        }

        dialog.show()
    }

    private fun confirmSell(activity: Activity, player: Player, onChanged: () -> Unit) {
        val price = TransferMarket.marketPriceOf(player)
        stylizedDialog(activity)
            .setTitle("Vender ${player.nome_jogo}?")
            .setMessage("Você receberá R$ ${"%,d".format(price)}.\nO jogador será transferido para outra organização do CBLOL.")
            .setPositiveButton("Vender") { _, _ ->
                when (val r = TransferMarket.sellPlayer(activity.applicationContext, player.id)) {
                    is SellResult.Ok -> {
                        Toast.makeText(activity,
                            "${player.nome_jogo} → ${r.toTeam} (R$ ${"%,d".format(r.price)})",
                            Toast.LENGTH_LONG).show()
                        onChanged()
                    }
                    is SellResult.Error ->
                        Toast.makeText(activity, r.msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRenegotiateDialog(activity: Activity, player: Player, onChanged: () -> Unit) {
        val current = player.contrato.salario_mensal_estimado_brl ?: 0L
        val view = activity.layoutInflater.inflate(R.layout.dialog_renegotiate, null)
        val etSalary = view.findViewById<android.widget.EditText>(R.id.et_new_salary)
        val etDate   = view.findViewById<android.widget.EditText>(R.id.et_new_date)
        etSalary.setText(current.toString())
        etDate.setText("2027-11-30")

        stylizedDialog(activity)
            .setTitle("Renegociar ${player.nome_jogo}")
            .setView(view)
            .setPositiveButton("Propor") { _, _ ->
                val newSal = etSalary.text.toString().toLongOrNull() ?: current
                val newEnd = etDate.text.toString().ifBlank { "2027-11-30" }
                val ok = TransferMarket.renegotiateContract(
                    activity.applicationContext, player.id, newSal, newEnd
                )
                Toast.makeText(activity,
                    if (ok) "Contrato renovado!" else "${player.nome_jogo} recusou a oferta.",
                    Toast.LENGTH_LONG).show()
                onChanged()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
