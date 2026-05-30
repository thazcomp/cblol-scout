package com.cblol.scout.ui.match

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Side
import com.cblol.scout.databinding.ActivityMatchSimulationBinding

/**
 * Renderiza um [MatchEvent] na UI da simulação: chips de pick/ban,
 * atualização do scoreboard, linhas no feed e ajuste de fases (pick & ban ↔
 * in-game ↔ end).
 *
 * Extraído da [com.cblol.scout.ui.MatchSimulationActivity] para isolar os
 * 13 handlers `onKill/onTower/onDragon/onBaron/...` num lugar coeso. A
 * Activity passa a chamar `renderer.render(event)` e ponto.
 *
 * **SOLID:**
 *  - **SRP**: traduz eventos do domínio em mudanças da UI.
 *  - **OCP**: novos tipos de evento entram com um novo case em [render] e
 *    um handler privado; não tocam nada existente.
 *  - **DIP**: depende do binding (UI) + colaboradores ([MatchStatsAccumulator],
 *    [MatchFeedAdapter]) recebidos via construtor.
 *
 * Mantém um pequeno estado mutável ([currentGameNum]) que vem da Activity
 * (placar acumulado de mapas) para preencher o label "in-game #N".
 */
internal class MatchEventRenderer(
    private val activity: Activity,
    private val binding: ActivityMatchSimulationBinding,
    private val stats: MatchStatsAccumulator,
    private val feed: MatchFeedAdapter,
    private val currentGameNum: () -> Int,
    private val seriesScore: () -> Pair<Int, Int>
) {

    /** Dispatcher único — adicione um novo case aqui ao introduzir um MatchEvent. */
    fun render(event: MatchEvent) {
        when (event) {
            is MatchEvent.GameStart        -> onGameStart(event)
            is MatchEvent.PhaseAnnouncement -> binding.tvPhase.text = event.text
            is MatchEvent.Ban              -> onBan(event)
            is MatchEvent.Pick             -> onPick(event)
            is MatchEvent.GameTick         -> onGameTick(event)
            is MatchEvent.Kill             -> onKill(event)
            is MatchEvent.TowerDown        -> onTower(event)
            is MatchEvent.Inhibitor        -> onInhibitor(event)
            is MatchEvent.Dragon           -> onDragon(event)
            is MatchEvent.Baron            -> onBaron(event)
            is MatchEvent.Herald           -> onHerald(event)
            is MatchEvent.Buff             -> onBuff(event)
            is MatchEvent.GameEnd          -> onGameEnd(event)
            is MatchEvent.SeriesEnd        -> binding.tvPhase.text = activity.getString(
                R.string.sim_series_winner, sideName(event.winnerSide),
                event.mapScore.first, event.mapScore.second
            )
        }
    }

    // ── Handlers por tipo de evento ─────────────────────────────────────

    private fun onGameStart(event: MatchEvent.GameStart) {
        stats.reset()
        binding.tvGameNumber.text = activity.getString(R.string.sim_map_label, event.gameNumber)
        binding.tvPhase.text      = activity.getString(R.string.sim_phase_pickban)
        binding.tvGameTimer.text  = activity.getString(R.string.sim_timer_zero)
        binding.layoutPickBan.visibility   = View.VISIBLE
        binding.layoutGameStats.visibility = View.GONE
        binding.containerHomeBans.removeAllViews()
        binding.containerAwayBans.removeAllViews()
        binding.containerHomePicks.removeAllViews()
        binding.containerAwayPicks.removeAllViews()
        feed.clear()
    }

    private fun onBan(event: MatchEvent.Ban) {
        val container = if (event.side == Side.HOME) binding.containerHomeBans else binding.containerAwayBans
        addPickBanChip(event.champion, isBan = true, container = container)
    }

    private fun onPick(event: MatchEvent.Pick) {
        val container = if (event.side == Side.HOME) binding.containerHomePicks else binding.containerAwayPicks
        addPickBanChip(
            activity.getString(R.string.event_role_champion, event.role, event.champion),
            isBan = false, container = container
        )
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_pick),
            text = activity.getString(R.string.event_pick, event.playerName, event.role, event.champion),
            accentColor = sideAccent(event.side)
        ))
    }

    private fun onGameTick(event: MatchEvent.GameTick) {
        if (binding.layoutPickBan.visibility == View.VISIBLE) {
            binding.layoutPickBan.visibility   = View.GONE
            binding.layoutGameStats.visibility = View.VISIBLE
            binding.tvPhase.text = activity.getString(R.string.sim_phase_in_game, currentGameNum())
        }
        binding.tvGameTimer.text = TIMER_FORMAT.format(event.minute, event.second)
    }

    private fun onKill(event: MatchEvent.Kill) {
        stats.accumulate(event); stats.updateScoreboard()
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_kill),
            text = activity.getString(R.string.event_kill, event.time,
                event.killerName, event.killerChamp, event.victimName, event.victimChamp),
            accentColor = sideAccent(event.killerSide)
        ))
    }

    private fun onTower(event: MatchEvent.TowerDown) {
        stats.accumulate(event); stats.updateScoreboard()
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_tower),
            text = activity.getString(R.string.event_tower, event.time, event.location, sideName(event.side)),
            accentColor = color(R.color.sim_tower_accent)
        ))
    }

    private fun onInhibitor(event: MatchEvent.Inhibitor) {
        // Inhibitor não conta no scoreboard, só vai pro feed.
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_inhibitor),
            text = activity.getString(R.string.event_inhibitor, event.time, event.location, sideName(event.side)),
            accentColor = color(R.color.sim_inhibitor_accent)
        ))
    }

    private fun onDragon(event: MatchEvent.Dragon) {
        stats.accumulate(event); stats.updateScoreboard()
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_dragon),
            text = activity.getString(R.string.event_dragon, event.time, event.type, sideName(event.side)),
            accentColor = color(R.color.sim_dragon_accent)
        ))
    }

    private fun onBaron(event: MatchEvent.Baron) {
        stats.accumulate(event); stats.updateScoreboard()
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_baron),
            text = activity.getString(R.string.event_baron, event.time, sideName(event.side)),
            accentColor = color(R.color.sim_baron_accent)
        ))
    }

    private fun onHerald(event: MatchEvent.Herald) {
        stats.accumulate(event); stats.updateScoreboard()
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_herald),
            text = activity.getString(R.string.event_herald, event.time, sideName(event.side)),
            accentColor = color(R.color.sim_herald_accent)
        ))
    }

    private fun onBuff(event: MatchEvent.Buff) {
        val isRed = event.type == BUFF_TYPE_RED
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(if (isRed) R.string.icon_buff_red else R.string.icon_buff_blue),
            text = activity.getString(R.string.event_buff, event.time, event.playerName, event.type),
            accentColor = color(if (isRed) R.color.sim_buff_red else R.color.sim_buff_blue)
        ))
    }

    private fun onGameEnd(event: MatchEvent.GameEnd) {
        val (h, a) = seriesScore()
        binding.tvSeriesScore.text = activity.getString(R.string.result_score_format, h, a)
        binding.tvPhase.text = activity.getString(R.string.sim_map_ended,
            event.gameNumber, sideName(event.winnerSide), event.durationMinutes)
        feed.add(MatchFeedAdapter.FeedEntry(
            icon = activity.getString(R.string.icon_match_end),
            text = activity.getString(R.string.sim_map_summary, event.gameNumber,
                event.finalKills.first, event.finalKills.second, sideName(event.winnerSide)),
            accentColor = color(R.color.sim_match_end_accent)
        ))
    }

    // ── Helpers de UI ────────────────────────────────────────────────────

    /** Cria um chip visual no painel de pick/ban (banido = riscado, pick = dourado bold). */
    private fun addPickBanChip(label: String, isBan: Boolean, container: ViewGroup) {
        val tv = TextView(activity).apply {
            text = if (isBan) activity.getString(R.string.event_ban_prefix, label) else label
            textSize = activity.resources.getDimension(R.dimen.sim_chip_text_size) /
                       activity.resources.displayMetrics.scaledDensity
            val padH = activity.resources.getDimensionPixelSize(R.dimen.sim_chip_padding_horizontal)
            val padV = activity.resources.getDimensionPixelSize(R.dimen.sim_chip_padding_vertical)
            setPadding(padH, padV, padH, padV)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = activity.resources.getDimensionPixelSize(R.dimen.sim_chip_margin)
                setMargins(m, m, m, m)
            }
            if (isBan) {
                setTextColor(color(R.color.sim_pickban_chip_ban))
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(color(R.color.sim_pickban_chip_pick))
                setTypeface(null, Typeface.BOLD)
            }
            setBackgroundColor(color(R.color.sim_pickban_chip_bg))
        }
        container.addView(tv)
    }

    private fun sideName(side: Side): String =
        if (side == Side.HOME) binding.tvHomeName.text.toString()
        else binding.tvAwayName.text.toString()

    private fun sideAccent(side: Side): Int =
        if (side == Side.HOME) color(R.color.sim_home_accent) else color(R.color.sim_away_accent)

    private fun color(@ColorRes res: Int): Int = ContextCompat.getColor(activity, res)

    companion object {
        private const val BUFF_TYPE_RED = "Red"
        private const val TIMER_FORMAT  = "%02d:%02d"
    }
}
