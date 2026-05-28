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
import com.cblol.scout.domain.usecase.ScoutingService
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
        applyScoutingVisibility(view, player)
        bindTransferRequestBanner(view, player)
        setupActions(activity, view, dialog, player, onChanged, onBuy)

        dialog.show()
    }

    /**
     * Se o jogador pediu para sair (moral/insatisfação), mostra um aviso no
     * campo de nome real (que fica logo abaixo do nome de jogo) destacando a
     * situação ao gerente. Só se aplica a jogadores do próprio elenco — para
     * jogadores de outros times o campo segue mostrando o nome real.
     */
    private fun bindTransferRequestBanner(view: View, player: Player) {
        val gs = GameRepository.load(view.context.applicationContext) ?: return
        if (player.time_id != gs.managerTeamId) return
        if (!com.cblol.scout.domain.usecase.MoraleService.hasRequestedTransfer(gs, player.id)) return

        val tvRealname = view.findViewById<TextView>(R.id.tv_bs_realname)
        tvRealname.text = view.context.getString(R.string.player_requested_transfer_banner)
        tvRealname.setTextColor(
            androidx.core.content.ContextCompat.getColor(view.context, R.color.state_danger))
    }

    /**
     * Aplica a visibilidade do scouting:
     *  - Overall: pode aparecer como número exato, faixa ou "???"
     *  - Atributos derivados: visíveis em níveis 3+ (apenas lane/tf) ou 4+ (todos)
     *  - Stats brutos + champion pool: visíveis apenas em nível 5
     *
     * Quando uma seção inteira fica oculta, esconde os IDs correspondentes
     * pra não deixar célula vazia.
     */
    private fun applyScoutingVisibility(view: View, player: Player) {
        val gs  = GameRepository.load(view.context.applicationContext) ?: return
        val vis = ScoutingService.visibilityOf(gs, player)
        val ctx = view.context

        // Overall (header) — substituiu o número exato pelo que estiver liberado
        val tvOverall = view.findViewById<TextView>(R.id.tv_bs_overall)
        tvOverall.text = when {
            vis.showOverallExact -> player.overallRating().toString()
            vis.showOverallBand  -> ScoutingService.overallBand(player.overallRating())
            else                 -> "???"
        }

        // Atributos derivados: 5 barras
        //   Nível < 3: tudo oculto ("???")
        //   Nível 3:   lane_phase + team_fight visíveis, demais ocultos
        //   Nível 4+:  todos os 5 visíveis
        val maskOther = !vis.showAllAttributes
        val maskLaneTf = !vis.showLaneAndTeamfight

        fun maskBar(progressId: Int, valueId: Int, mask: Boolean, actualValue: Int) {
            val pb = view.findViewById<ProgressBar>(progressId)
            val tv = view.findViewById<TextView>(valueId)
            if (mask) {
                pb.progress = 0
                pb.alpha = 0.3f
                tv.text = "???"
                tv.alpha = 0.5f
            } else {
                pb.progress = actualValue
                pb.alpha = 1f
                tv.text = actualValue.toString()
                tv.alpha = 1f
            }
        }
        val a = player.atributos_derivados
        maskBar(R.id.pb_bs_lane,   R.id.tv_bs_lane_val,   maskLaneTf, a.lane_phase)
        maskBar(R.id.pb_bs_tf,     R.id.tv_bs_tf_val,     maskLaneTf, a.team_fight)
        maskBar(R.id.pb_bs_cria,   R.id.tv_bs_cria_val,   maskOther,  a.criatividade)
        maskBar(R.id.pb_bs_cons,   R.id.tv_bs_cons_val,   maskOther,  a.consistencia)
        maskBar(R.id.pb_bs_clutch, R.id.tv_bs_clutch_val, maskOther,  a.clutch)

        // Stats brutos (KDA, CS/min, jogos, etc) — todos ocultos se nível < 5
        if (!vis.showRawStats) {
            val placeholder = "???"
            view.findViewById<TextView>(R.id.tv_bs_jogos).text = placeholder
            view.findViewById<TextView>(R.id.tv_bs_kda).text   = placeholder
            view.findViewById<TextView>(R.id.tv_bs_kp).text    = placeholder
            view.findViewById<TextView>(R.id.tv_bs_cs).text    = placeholder
            view.findViewById<TextView>(R.id.tv_bs_dmg).text   = placeholder
            view.findViewById<TextView>(R.id.tv_bs_gd15).text  = placeholder
            view.findViewById<TextView>(R.id.tv_bs_xpd15).text = placeholder
            view.findViewById<TextView>(R.id.tv_bs_vis).text   = placeholder
        }
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
        view.findViewById<TextView>(R.id.tv_bs_kda).text    = s.kdaDisplay()
        view.findViewById<TextView>(R.id.tv_bs_kp).text     =
            ctx.getString(R.string.player_kp_format, s.kp_pct.toInt())
        view.findViewById<TextView>(R.id.tv_bs_cs).text     = s.csMinDisplay()
        view.findViewById<TextView>(R.id.tv_bs_dmg).text    =
            ctx.getString(R.string.player_dmg_share, s.damageShareDisplay())
        view.findViewById<TextView>(R.id.tv_bs_gd15).text   = formatDiff(ctx, s.gd15)
        view.findViewById<TextView>(R.id.tv_bs_xpd15).text  = formatDiff(ctx, s.xpd15)
        view.findViewById<TextView>(R.id.tv_bs_vis).text    =
            s.visionScoreDisplay() ?: ctx.getString(R.string.player_stat_not_available)
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

        // IMPORTANTE: a checagem "jogador é do meu time" tem PRECEDÊNCIA sobre o
        // modo compra. Um jogador que já é meu NUNCA deve mostrar "Observar"
        // (escotear) ou "Contratar" — não faz sentido escotear/contratar quem já
        // está no elenco. Antes o branch `onBuy != null` vinha primeiro, então um
        // jogador do próprio time aberto com onBuy (ex.: mercado listando por
        // engano um atleta já contratado) exibia as ações de mercado.
        val isMyPlayer = gs != null && player.time_id == gs.managerTeamId

        when {
            // Modo gerência (jogador do meu time) — tem prioridade máxima
            isMyPlayer ->
                setupManagementActions(activity, view, dialog, player, onChanged)

            // Modo compra (vindo do mercado): botão de comprar + escotear
            onBuy != null ->
                setupMarketActions(activity, actions, dialog, player, onBuy, onChanged)

            // Modo visualização (nenhuma ação)
            else -> actions.visibility = View.GONE
        }
    }

    /**
     * Configura o conjunto de ações para um jogador VISTO DO MERCADO.
     *
     * Layout:
     *  [ESCOTEAR (R$ X)]  — visível apenas se ainda não está totalmente revelado
     *  [CONTRATAR]
     *
     * O botão de escotear chama [ScoutingService.startScouting] e mostra um
     * dialog narrativo. Erros (sem slot, sem dinheiro, etc.) viram tambm
     * dialogs de feedback.
     */
    private fun setupMarketActions(
        activity: Activity,
        actions: LinearLayout,
        dialog: BottomSheetDialog,
        player: Player,
        onBuy: (Player) -> Unit,
        onChanged: () -> Unit
    ) {
        actions.visibility = View.VISIBLE
        actions.removeAllViews()

        // Divider entre os atributos e os botões
        val divider = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, DIVIDER_HEIGHT
            ).apply { setMargins(0, DIVIDER_MARGIN_V, 0, DIVIDER_MARGIN_V) }
            setBackgroundColor(activity.resources.getColor(R.color.color_outline_variant, null))
        }
        actions.addView(divider)

        // Botão ESCOTEAR (só se ainda não está no nível máximo)
        val gs = GameRepository.current()
        val currentLevel = com.cblol.scout.domain.usecase.ScoutingService.scoutLevelOf(gs, player.id)
        if (currentLevel < com.cblol.scout.domain.usecase.ScoutingService.MAX_LEVEL) {
            val btnScout = com.google.android.material.button.MaterialButton(
                activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = activity.getString(R.string.scouting_market_action_start,
                    "%,d".format(com.cblol.scout.domain.usecase.ScoutingService.START_SCOUT_COST))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, DIVIDER_MARGIN_V) }
                setOnClickListener {
                    startScoutingFromDialog(activity, player) {
                        dialog.dismiss()
                        onChanged()
                    }
                }
            }
            actions.addView(btnScout)
        }

        // Botão CONTRATAR
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

    /** Tenta iniciar scouting e mostra dialog apropriado para sucesso/erro. */
    private fun startScoutingFromDialog(
        activity: Activity,
        player: Player,
        onSuccess: () -> Unit
    ) {
        val gs = GameRepository.current()
        val result = com.cblol.scout.domain.usecase.ScoutingService.startScouting(gs, player)
        when (result) {
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.OK -> {
                GameRepository.save(activity.applicationContext)
                stylizedDialog(activity)
                    .setTitle(R.string.scouting_started_title)
                    .setMessage(activity.getString(R.string.scouting_started_msg, player.nome_jogo))
                    .setPositiveButton(R.string.btn_ok) { _, _ -> onSuccess() }
                    .show()
            }
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.ALREADY_MAX_LEVEL ->
                showScoutError(activity, R.string.scouting_already_max)
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.ALREADY_SCOUTING ->
                showScoutError(activity, R.string.scouting_already_scouting)
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.SLOTS_FULL ->
                showScoutError(activity, R.string.scouting_slots_full)
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.INSUFFICIENT_FUNDS ->
                showScoutError(activity, R.string.scouting_insufficient_funds)
            com.cblol.scout.domain.usecase.ScoutingService.StartResult.SAME_TEAM ->
                showScoutError(activity, R.string.scouting_same_team)
        }
    }

    private fun showScoutError(activity: Activity, msgRes: Int) {
        stylizedDialog(activity)
            .setMessage(msgRes)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
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

    private fun handleSellResult(
        activity: Activity,
        player: Player,
        onChanged: () -> Unit,
        force: Boolean = false
    ) {
        when (val r = TransferMarket.sellPlayer(activity.applicationContext, player.id, force)) {
            is SellResult.Ok -> {
                // Mostra o preço recebido e, se houve multa rescisória, informa.
                val baseMsg = activity.getString(R.string.player_sold_message,
                    player.nome_jogo, r.toTeam, "%,d".format(r.price))
                val fullMsg = if (r.terminationFee > 0) {
                    "$baseMsg\n${activity.getString(R.string.player_sold_termination_fee, "%,d".format(r.terminationFee))}"
                } else baseMsg
                Toast.makeText(activity, fullMsg, Toast.LENGTH_LONG).show()
                onChanged()
            }
            is SellResult.WarningRequired -> {
                // Venda arriscada (único titular sem reserva) — confirma de novo
                stylizedDialog(activity)
                    .setTitle(R.string.dialog_sell_warning_title)
                    .setMessage(r.msg)
                    .setPositiveButton(R.string.btn_sell_anyway) { _, _ ->
                        handleSellResult(activity, player, onChanged, force = true)
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
            is SellResult.Error ->
                Toast.makeText(activity, r.msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenegotiateDialog(activity: Activity, player: Player, onChanged: () -> Unit) {
        val current = player.contrato.salario_mensal_estimado_brl ?: 0L
        val view = activity.layoutInflater.inflate(R.layout.dialog_renegotiate, null)
        val etSalary = view.findViewById<EditText>(R.id.et_new_salary)
        val etBonus  = view.findViewById<EditText>(R.id.et_signing_bonus)
        val etDate   = view.findViewById<EditText>(R.id.et_new_date)
        val defaultEnd = activity.getString(R.string.player_default_contract_end)
        etSalary.setText(current.toString())
        etBonus.setText("0")
        etDate.setText(defaultEnd)

        // Preenche o card de info do contrato (status + multa rescisória)
        val gs = GameRepository.current()
        val status   = com.cblol.scout.domain.usecase.ContractService.statusOf(gs, player)
        val release  = com.cblol.scout.domain.usecase.ContractService.releaseClauseFor(gs, player)
        val daysLeft = com.cblol.scout.domain.usecase.ContractService.daysRemaining(gs, player)
        view.findViewById<TextView>(R.id.tv_contract_status).text = when {
            daysLeft != null -> activity.getString(R.string.renegotiate_status_format,
                status.label, daysLeft)
            else -> status.label
        }
        view.findViewById<TextView>(R.id.tv_contract_release).text =
            activity.getString(R.string.renegotiate_release_format, "%,d".format(release))

        stylizedDialog(activity)
            .setTitle(activity.getString(R.string.dialog_renegotiate_title, player.nome_jogo))
            .setView(view)
            .setPositiveButton(R.string.btn_propose) { _, _ ->
                handleRenegotiate(activity, player,
                    etSalary.text.toString().toLongOrNull() ?: current,
                    etBonus.text.toString().toLongOrNull() ?: 0L,
                    etDate.text.toString().ifBlank { defaultEnd },
                    onChanged)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun handleRenegotiate(
        activity: Activity, player: Player,
        newSal: Long, signingBonus: Long, newEnd: String, onChanged: () -> Unit
    ) {
        val ok = TransferMarket.renegotiateContract(
            activity.applicationContext, player.id, newSal, newEnd,
            signingBonus = signingBonus
        )
        val msg = if (ok) activity.getString(R.string.player_contract_renewed)
                  else activity.getString(R.string.player_contract_refused, player.nome_jogo)
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        onChanged()
    }

    private const val DIVIDER_HEIGHT   = 1
    private const val DIVIDER_MARGIN_V = 14
}
