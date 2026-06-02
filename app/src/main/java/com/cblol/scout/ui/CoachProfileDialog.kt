package com.cblol.scout.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.CoachProfile
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.LevelUpRewards
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
        bindBadges(view, profile)
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

    /**
     * Renderiza a seção de badges:
     *  - Para cada milestone em [LevelUpRewards.milestones], mostra uma linha
     *    com emoji + nome.
     *  - Desbloqueadas (badgeId em [CoachProfile.unlockedBadges]): cor normal,
     *    com descrição em cinza embaixo.
     *  - Bloqueadas: emoji esmaecido + cadeado, texto cinza, com legenda
     *    "Desbloqueia no Nv X".
     *
     * Construída dinamicamente porque o número de milestones é variável.
     */
    private fun bindBadges(view: View, profile: CoachProfile) {
        val container = view.findViewById<LinearLayout>(R.id.ll_coach_badges)
        container.removeAllViews()
        val unlocked = profile.unlockedBadges.toSet()

        LevelUpRewards.milestones.forEach { reward ->
            val isUnlocked = reward.badgeId in unlocked
            container.addView(buildBadgeRow(view, reward, isUnlocked))
        }
    }

    /** Constrói uma linha de badge (emoji + nome + descrição/legenda). */
    private fun buildBadgeRow(
        view: View,
        reward: LevelUpRewards.LevelUpReward,
        isUnlocked: Boolean
    ): View {
        val ctx = view.context
        val density = ctx.resources.displayMetrics.density
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (4 * density).toInt()
                it.bottomMargin = (4 * density).toInt()
            }
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val emoji = TextView(ctx).apply {
            text = if (isUnlocked) reward.badgeEmoji else "🔒"
            textSize = 22f
            alpha = if (isUnlocked) 1f else 0.4f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.marginEnd = (10 * density).toInt()
            }
        }

        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameTv = TextView(ctx).apply {
            text = reward.badgeName
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (isUnlocked) R.color.color_on_surface else R.color.color_on_surface_variant
                )
            )
        }
        val subTv = TextView(ctx).apply {
            text = if (isUnlocked) reward.description
                   else ctx.getString(R.string.coach_badges_locked_format, reward.level)
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.color_on_surface_variant))
            setLineSpacing(0f, 1.25f)
        }
        info.addView(nameTv)
        info.addView(subTv)

        row.addView(emoji)
        row.addView(info)
        return row
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
