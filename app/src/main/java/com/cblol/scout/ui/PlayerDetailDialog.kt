package com.cblol.scout.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.cblol.scout.R
import com.cblol.scout.data.Player
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.game.TransferMarket
import com.cblol.scout.util.TeamColors
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

/**
 * Bottom sheet com os dados detalhados de um jogador.
 *
 * Reutilizado por:
 *  - [MainActivity] (browsing geral)
 *  - [SquadActivity] (gerência do meu elenco)
 *  - [TransferMarketActivity] (com botão de comprar via `onBuy`)
 *
 * SOLID:
 * - **SRP**: bind do header / stats / atributos / salário / ações isolados em
 *   helpers ([bindHeader], [bindStats], [bindAttributes], [bindSalary], [setupActions]).
 * - **OCP**: novo modo (compra/gerência/visualização) só adiciona um branch em
 *   [setupActions] sem mexer no binding.
 * - **DIP**: depende de [GameRepository] e [TransferMarket]. Strings via R.string.
 *
 * `onChanged` é chamado depois de uma ação que muda estado (renegociação, venda,
 * promoção) para que a tela chamadora possa recarregar.
 * `onBuy`, se fornecido, troca as ações de gerência por um botão de "Contratar".
 */
object PlayerDetailDialog {

    fun show(
        activity: Activity,
        player: Player,
        onChanged: () -> Unit = {},
        onBuy: ((Player) -> Unit)? = null
    ) {
        val dialog = BottomSheetDialog(activity, R.style.ThemeOverlay_CBLOLScout_BottomSheet)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_player, null)
        dialog.setContentView(view)

        bindHeader(view, player)
        bindStats(view, player)
        bindAttributes(view, player)
        bindSalary(view, player, activity)
        setupActions(activity, view, dialog, player, onChanged, onBuy)

        dialog.show()
    }

    // ── Binds ────────────────────────────────────────────────────────────

    private fun bindHeader(view: View, player: Player) {
        view.findViewById<View>(R.id.view_bs_team_bar).setBackgroundColor(TeamColors.forTeam(player.time_id))
        view.findViewById<TextView>(R.id.tv_bs_name).text = player.nome_jogo
        view.findViewById<TextView>(R.id.tv_bs_realname).text =
            player.nome_real ?: view.context.getString(R.string.player_no_real_name)
        view.findViewById<TextView>(R.id.tv_bs_team).text = player.time_nome
        view.findViewById<TextView>(R.id.tv_bs_role).text = view.context.getString(
            R.string.player_role_age,
            player.role,
            TeamColors.flagEmoji(player.nacionalidade),
            player.idade?.let { view.context.getString(R.string.player_age_years, it) } ?: ""
        )
        view.findViewById<TextView>(R.id.tv_bs_overall).text = player.overallRating().toString()
    }

    private fun bindStats(view: View, player: Player) {
        val s   = player.stats_brutas
        val ctx = view.context
        view.findViewById<TextView>(R.id.tv_bs_jogos).text  = s.jogos.toString()
        view.findViewById<TextView>(R.id.tv_bs_kda).text    = s.kda.toString()
        view.findViewById<TextView>(R.id.tv_bs_kp).text     =
            ctx.getString(R.string.player_kp_format, s.kp_pct.toInt())
        view.findViewById<TextView>(R.id.tv_bs_cs).text     = s.cs_min.toString()
        view.findViewById<TextView>(R.id.tv_bs_dmg).text    =
            ctx.getString(R.string.player_dmg_share, s.damage_share_pct.toString())
        view.findViewById<TextView>(R.id.tv_bs_gd15).text   = formatDiff(ctx, s.gd15)
        view.findViewById<TextView>(R.id.tv_bs_xpd15).text  = formatDiff(ctx, s.xpd15)
        view.findViewById<TextView>(R.id.tv_bs_vis).text    =
            s.vision_score_min?.toString() ?: ctx.getString(R.string.player_stat_not_available)
    }

    private fun formatDiff(ctx: Context, value: Int?): String = when {
        value == null -> ctx.getString(R.string.player_stat_not_available)
        value >= 0    -> ctx.getString(R.string.player_diff_positive, value)
        else          -> value.toString()
    }

    private fun bindAttributes(view: View, player: Player) {
        val a = player.atributos_derivados
        view.findViewById<ProgressBar>(R.id.pb_bs_lane).progress   = a.lane_phase
        view.findViewById<ProgressBar>(R.id.pb_bs_tf).progress     = a.team_fight
        view.findViewById<ProgressBar>(R.id.pb_bs_cria).progress   = a.criatividade
        view.findViewById<ProgressBar>(R.id.pb_bs_cons).progress   = a.consistencia
        view.findViewById<ProgressBar>(R.id.pb_bs_clutch).progress = a.clutch
        view.findViewById<TextView>(R.id.tv_bs_lane_val).text   = a.lane_phase.toString()
        view.findViewById<TextView>(R.id.tv_bs_tf_val).text     = a.team_fight.toString()
        view.findViewById<TextView>(R.id.tv_bs_cria_val).text   = a.criatividade.toString()
        view.findViewById<TextView>(R.id.tv_bs_cons_val).text   = a.consistencia.toString()
        view.findViewById<TextView>(R.id.tv_bs_clutch_val).text = a.clutch.toString()
    }

    private fun bindSalary(view: View, player: Player, ctx: Context) {
        val salary = player.contrato.salario_mensal_estimado_brl
        view.findViewById<TextView>(R.id.tv_bs_salary).text =
            if (salary != null) ctx.getString(
                R.string.player_salary_format, "%,d".format(salary), player.contrato.fonte_salario
            )
            else ctx.getString(R.string.player_salary_unavailable)
    }

    // ── Ações (compra / gerência / nenhuma) ─────────────────────────────

    private fun setupActions(
        activity: Activity,
        view: View,
        dialog: BottomSheetDialog,
        player: Player,
        onChanged: () -> Unit,
        onBuy: ((Player) -> Unit)?
    ) {
        val actions = view.findViewById<LinearLayout>(R.id.layout_bs_actions)
        val gs      = GameRepository.load(activity.applicationContext)

        when {
            // Modo compra (vindo do mercado)
            onBuy != null -> setupBuyAction(activity, actions, dialog, player, onBuy)

            // Modo gerência (jogador do meu time)
            gs != null && player.time_id == gs.managerTeamId ->
                setupManagementActions(activity, view, dialog, player, onChanged)

            // Modo visualização (nenhuma ação)
            else -> actions.visibility = View.GONE
        }
    }

    private fun setupBuyAction(
        activity: Activity,
        actions: LinearLayout,
        dialog: BottomSheetDialog,
        player: Player,
        onBuy: (Player) -> Unit
    ) {
        actions.visibility = View.VISIBLE
        actions.removeAllViews()

        // Divider
        val divider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DIVIDER_HEIGHT
            ).apply { setMargins(0, DIVIDER_MARGIN_V, 0, DIVIDER_MARGIN_V) }
            setBackgroundColor(activity.resources.getColor(R.color.color_outline_variant, null))
        }
        actions.addView(divider)

        val btnBuy = MaterialButton(activity).apply {
            text = activity.getString(R.string.player_btn_hire)
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
    }

    private fun setupManagementActions(
        activity: Activity,
        view: View,
        dialog: BottomSheetDialog,
        player: Player,
        onChanged: () -> Unit
    ) {
        view.findViewById<LinearLayout>(R.id.layout_bs_actions).visibility = View.VISIBLE

        view.findViewById<Button>(R.id.btn_bs_toggle_starter).apply {
            setText(if (player.titular) R.string.player_btn_move_bench
                    else R.string.player_btn_promote_starter)
            setOnClickListener {
                TransferMarket.toggleStarter(activity.applicationContext, player.id)
                Toast.makeText(activity, R.string.squad_status_updated, Toast.LENGTH_SHORT).show()
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
    }

    // ── Dialogs de venda e renegociação ─────────────────────────────────

    private fun confirmSell(activity: Activity, player: Player, onChanged: () -> Unit) {
        val price = TransferMarket.marketPriceOf(player)
        stylizedDialog(activity)
            .setTitle(activity.getString(R.string.dialog_sell_player_title, player.nome_jogo))
            .setMessage(activity.getString(R.string.dialog_sell_player_message, "%,d".format(price)))
            .setPositiveButton(R.string.btn_sell) { _, _ ->
                handleSellResult(activity, player, onChanged)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun handleSellResult(activity: Activity, player: Player, onChanged: () -> Unit) {
        when (val r = TransferMarket.sellPlayer(activity.applicationContext, player.id)) {
            is SellResult.Ok -> {
                Toast.makeText(activity,
                    activity.getString(R.string.player_sold_message,
                        player.nome_jogo, r.toTeam, "%,d".format(r.price)),
                    Toast.LENGTH_LONG).show()
                onChanged()
            }
            is SellResult.Error ->
                Toast.makeText(activity, r.msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenegotiateDialog(activity: Activity, player: Player, onChanged: () -> Unit) {
        val current = player.contrato.salario_mensal_estimado_brl ?: 0L
        val view = activity.layoutInflater.inflate(R.layout.dialog_renegotiate, null)
        val etSalary = view.findViewById<EditText>(R.id.et_new_salary)
        val etDate   = view.findViewById<EditText>(R.id.et_new_date)
        val defaultEnd = activity.getString(R.string.player_default_contract_end)
        etSalary.setText(current.toString())
        etDate.setText(defaultEnd)

        stylizedDialog(activity)
            .setTitle(activity.getString(R.string.dialog_renegotiate_title, player.nome_jogo))
            .setView(view)
            .setPositiveButton(R.string.btn_propose) { _, _ ->
                handleRenegotiate(activity, player,
                    etSalary.text.toString().toLongOrNull() ?: current,
                    etDate.text.toString().ifBlank { defaultEnd },
                    onChanged)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun handleRenegotiate(
        activity: Activity, player: Player,
        newSal: Long, newEnd: String, onChanged: () -> Unit
    ) {
        val ok = TransferMarket.renegotiateContract(
            activity.applicationContext, player.id, newSal, newEnd
        )
        val msg = if (ok) activity.getString(R.string.player_contract_renewed)
                  else activity.getString(R.string.player_contract_refused, player.nome_jogo)
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        onChanged()
    }

    private const val DIVIDER_HEIGHT   = 1
    private const val DIVIDER_MARGIN_V = 14
}
