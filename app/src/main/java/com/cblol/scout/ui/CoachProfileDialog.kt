package com.cblol.scout.ui

import android.app.Activity
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.cblol.scout.R
import com.cblol.scout.data.CoachProfile
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.CoachProgressionService

/**
 * Dialog que mostra o perfil do técnico com nome, level, XP, atributos
 * derivados e estatísticas de carreira.
 *
 * O dialog é puramente informativo (sem ações). Para alterar dados do
 * técnico use os fluxos normais do jogo (vencer partidas, transferências, etc.)
 * que disparam `CoachProgressionService.record*`.
 *
 * SOLID:
 * - **SRP**: cada bind tem método próprio ([bindHeader], [bindXp],
 *   [bindAttributes], [bindStats]) — adicionar nova seção é OCP-friendly.
 * - **DIP**: depende apenas de [CoachProfile] (modelo) e [CoachProgressionService]
 *   (cálculo puro). Não acessa GameRepository diretamente; a Activity chamadora
 *   passa o profile + nome.
 *
 * Strings em `R.string.coach_*`; cores em `R.color.champion_gold` etc.
 */
object CoachProfileDialog {

    /**
     * Exibe o dialog para o `profile` informado.
     *
     * @param activity Activity hospedeira (usada para o tema do dialog)
     * @param profile dados crus do técnico (lidos do GameState)
     * @param name nome do treinador (vem do GameState.managerName)
     */
    fun show(activity: Activity, profile: CoachProfile, name: String) {
        val view  = activity.layoutInflater.inflate(R.layout.dialog_coach_profile, null)
        val stats = CoachProgressionService.compute(profile, name)

        bindHeader(view, stats)
        bindXp(view, stats)
        bindAttributes(view, stats)
        bindStats(view, stats)

        stylizedDialog(activity)
            .setTitle(R.string.coach_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ── Binds ────────────────────────────────────────────────────────────

    private fun bindHeader(view: View, stats: CoachProgressionService.CoachStats) {
        val ctx = view.context
        view.findViewById<TextView>(R.id.tv_coach_name).text  = stats.name
        view.findViewById<TextView>(R.id.tv_coach_title).text =
            ctx.getString(R.string.coach_subtitle, stats.title, stats.level)
    }

    private fun bindXp(view: View, stats: CoachProgressionService.CoachStats) {
        val ctx = view.context
        val tvXp = view.findViewById<TextView>(R.id.tv_coach_xp)
        val pbXp = view.findViewById<ProgressBar>(R.id.pb_coach_xp)

        if (stats.level >= GameConstants.Coach.MAX_LEVEL) {
            tvXp.text = ctx.getString(R.string.coach_xp_max_level, stats.currentXp)
            pbXp.progress = 100
        } else {
            tvXp.text = ctx.getString(R.string.coach_xp_format,
                stats.currentXp, stats.xpForNextLevel)
            pbXp.progress = (stats.progressToNextLevel * 100).toInt().coerceIn(0, 100)
        }
    }

    private fun bindAttributes(view: View, stats: CoachProgressionService.CoachStats) {
        val ctx = view.context
        bindAttrRow(view, R.id.attr_leadership, ctx.getString(R.string.coach_attr_leadership), stats.leadership)
        bindAttrRow(view, R.id.attr_drafting,   ctx.getString(R.string.coach_attr_drafting),   stats.drafting)
        bindAttrRow(view, R.id.attr_financial,  ctx.getString(R.string.coach_attr_financial),  stats.financialMgmt)
        bindAttrRow(view, R.id.attr_talent,     ctx.getString(R.string.coach_attr_talent),     stats.talentEye)
        bindAttrRow(view, R.id.attr_reputation, ctx.getString(R.string.coach_attr_reputation), stats.reputation)
    }

    /**
     * Preenche um único row de atributo (label + valor + ProgressBar).
     * As 5 chamadas iguais em [bindAttributes] usam este helper para evitar duplicação.
     */
    private fun bindAttrRow(root: View, containerId: Int, label: String, value: Int) {
        val container = root.findViewById<View>(containerId)
        container.findViewById<TextView>(R.id.tv_attr_label).text = label
        container.findViewById<TextView>(R.id.tv_attr_value).text = value.toString()
        container.findViewById<ProgressBar>(R.id.pb_attr_bar).progress = value
    }

    private fun bindStats(view: View, stats: CoachProgressionService.CoachStats) {
        val ctx = view.context
        view.findViewById<TextView>(R.id.tv_coach_stat_maps).text =
            ctx.getString(R.string.coach_stat_maps, stats.mapsWon, stats.mapsLost, stats.winRate)
        view.findViewById<TextView>(R.id.tv_coach_stat_series).text =
            ctx.getString(R.string.coach_stat_series, stats.seriesWon, stats.seriesLost)
        view.findViewById<TextView>(R.id.tv_coach_stat_drafts).text =
            ctx.getString(R.string.coach_stat_drafts, stats.manualPickBansDone)
        view.findViewById<TextView>(R.id.tv_coach_stat_hires).text =
            ctx.getString(R.string.coach_stat_hires, stats.playersHired)
        view.findViewById<TextView>(R.id.tv_coach_stat_sells).text =
            ctx.getString(R.string.coach_stat_sells, stats.playersSold)
    }
}
