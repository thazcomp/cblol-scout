package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.CompositionRepository

/**
 * Dialog de pré-simulação.
 *
 * SOLID:
 * - **SRP**: separa a coleta de dados ([loadMatchContext]), a montagem da
 *   mensagem ([buildMessage]) e a apresentação do dialog ([show]).
 * - **OCP**: novas seções na mensagem são adicionadas via funções privadas
 *   ([formatCompSection], [formatMatchupsSection], [formatStrengthSection],
 *   [formatInsightsSection]); a função-orquestradora [buildMessage] não muda.
 * - **DIP**: depende de [CompositionRepository] (utilitário) e [GameRepository]
 *   (camada de dados); todas as strings vêm de `R.string.*`.
 *
 * Reutilizado por: ScheduleActivity, PickBanRouterActivity, MatchSimulationActivity.
 */
object PreSimulationDialog {

    /** Contexto montado a partir do matchId, encapsula tudo que [show] precisa. */
    private data class MatchContext(
        val match: Match,
        val homeName: String,
        val awayName: String,
        val homeRoster: List<Player>,
        val awayRoster: List<Player>,
        val homeComp: CompositionRepository.TaggedAnalysisResult,
        val awayComp: CompositionRepository.TaggedAnalysisResult,
        val mapNumber: Int
    )

    fun show(context: Context, matchId: String, onConfirm: () -> Unit) {
        val ctx = loadMatchContext(context, matchId)
        if (ctx == null) { onConfirm(); return }

        val message = buildMessage(context, ctx)
        stylizedDialog(context)
            .setTitle(context.getString(R.string.presim_title, ctx.mapNumber, ctx.homeName, ctx.awayName))
            .setMessage(message)
            .setPositiveButton(R.string.btn_start_simulation) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    fun showThenSimulate(context: Context, matchId: String) {
        show(context, matchId) {
            context.startActivity(
                Intent(context, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId)
            )
        }
    }

    // ── Coleta de dados ──────────────────────────────────────────────────

    private fun loadMatchContext(context: Context, matchId: String): MatchContext? {
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: return null
        val snap  = GameRepository.snapshot(context)

        val homeName = snap.times.find { it.id == match.homeTeamId }?.nome ?: match.homeTeamId
        val awayName = snap.times.find { it.id == match.awayTeamId }?.nome ?: match.awayTeamId

        val homeRoster = startersOf(context, match.homeTeamId)
        val awayRoster = startersOf(context, match.awayTeamId)

        val gameNumber  = match.homeScore + match.awayScore + 1
        val plan        = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
        val bluePicks   = plan?.bluePicks ?: emptyList()
        val redPicks    = plan?.redPicks  ?: emptyList()
        val blueBans    = plan?.blueBans  ?: emptyList()
        val redBans     = plan?.redBans   ?: emptyList()

        val homeIsBlue  = (gameNumber % 2 == 1)
        val homePicks   = if (homeIsBlue) bluePicks else redPicks
        val awayPicks   = if (homeIsBlue) redPicks  else bluePicks
        val homeBansFor = if (homeIsBlue) redBans   else blueBans
        val awayBansFor = if (homeIsBlue) blueBans  else redBans

        return MatchContext(
            match      = match,
            homeName   = homeName,
            awayName   = awayName,
            homeRoster = homeRoster,
            awayRoster = awayRoster,
            homeComp   = CompositionRepository.analyzeWithTags(homePicks, awayPicks, homeBansFor),
            awayComp   = CompositionRepository.analyzeWithTags(awayPicks, homePicks, awayBansFor),
            mapNumber  = gameNumber
        )
    }

    private fun startersOf(context: Context, teamId: String): List<Player> =
        GameRepository.rosterOf(context, teamId).filter { it.titular }

    // ── Montagem da mensagem ─────────────────────────────────────────────

    private fun buildMessage(context: Context, ctx: MatchContext): String = buildString {
        append(formatCompSection(context, ctx))
        if (ctx.homeRoster.isNotEmpty() && ctx.awayRoster.isNotEmpty()) {
            append("\n\n")
            append(formatMatchupsSection(context, ctx))
            append("\n\n")
            append(formatStrengthSection(context, ctx))
        }
        val insights = formatInsightsSection(context, ctx)
        if (insights.isNotEmpty()) {
            append("\n\n")
            append(insights)
        }
    }

    private fun formatCompSection(context: Context, ctx: MatchContext): String = buildString {
        append(context.getString(R.string.presim_header_synergy))
        append("\n")
        append(formatCompSide(context, ctx.homeName, ctx.homeComp))
        append("\n")
        append(formatCompSide(context, ctx.awayName, ctx.awayComp))
    }

    private fun formatCompSide(
        context: Context,
        teamName: String,
        result: CompositionRepository.TaggedAnalysisResult
    ): String {
        return if (result.detectedComps.isEmpty()) {
            context.getString(R.string.presim_no_strong_comp, teamName, result.totalBonus)
        } else {
            val main = result.detectedComps.take(2).joinToString(" + ") {
                "${it.composition.name} (${it.percent}%)"
            }
            context.getString(R.string.presim_with_comp, teamName, main, result.totalBonus)
        }
    }

    private fun formatMatchupsSection(context: Context, ctx: MatchContext): String = buildString {
        append(context.getString(R.string.presim_header_matchups))
        for (role in LANE_ROLES) {
            val h = ctx.homeRoster.firstOrNull { it.role == role } ?: continue
            val a = ctx.awayRoster.firstOrNull { it.role == role } ?: continue
            val hOvr = h.overallRating()
            val aOvr = a.overallRating()
            val arrow = arrowFor(context, hOvr - aOvr)
            append("\n")
            append(context.getString(R.string.presim_matchup_line,
                role, h.nome_jogo, hOvr, arrow, a.nome_jogo, aOvr))
        }
    }

    private fun arrowFor(context: Context, diff: Int): String = context.getString(when {
        diff >=  GameConstants.Player.OVERALL_DIFF_HUGE -> R.string.presim_arrow_huge_home
        diff >=  GameConstants.Player.OVERALL_DIFF_MILD -> R.string.presim_arrow_home
        diff <= -GameConstants.Player.OVERALL_DIFF_HUGE -> R.string.presim_arrow_huge_away
        diff <= -GameConstants.Player.OVERALL_DIFF_MILD -> R.string.presim_arrow_away
        else                                            -> R.string.presim_arrow_equal
    })

    private fun formatStrengthSection(context: Context, ctx: MatchContext): String {
        val homeAvg = ctx.homeRoster.map { it.overallRating() }.average()
        val awayAvg = ctx.awayRoster.map { it.overallRating() }.average()
        val gap     = homeAvg - awayAvg
        val verdictRes = when {
            gap >=  STRENGTH_GAP_CLEAR -> R.string.presim_clear_advantage
            gap >=  STRENGTH_GAP_SLIGHT -> R.string.presim_slight_advantage
            gap <= -STRENGTH_GAP_CLEAR -> R.string.presim_clear_advantage
            gap <= -STRENGTH_GAP_SLIGHT -> R.string.presim_slight_advantage
            else                       -> R.string.presim_balanced
        }
        val verdictTeam = when {
            gap > 0  -> ctx.homeName
            gap < 0  -> ctx.awayName
            else     -> ""
        }
        return buildString {
            append(context.getString(R.string.presim_header_strength))
            append("\n")
            append(context.getString(R.string.presim_team_score,
                ctx.homeName, "%.1f".format(homeAvg),
                ctx.awayName, "%.1f".format(awayAvg)))
            append("\n")
            append(
                if (verdictRes == R.string.presim_balanced) context.getString(verdictRes)
                else context.getString(verdictRes, verdictTeam)
            )
        }
    }

    private fun formatInsightsSection(context: Context, ctx: MatchContext): String {
        val all = ctx.homeComp.insights.map {
            context.getString(R.string.presim_insight_prefix, ctx.homeName, it)
        } + ctx.awayComp.insights.map {
            context.getString(R.string.presim_insight_prefix, ctx.awayName, it)
        }
        if (all.isEmpty()) return ""
        return buildString {
            append(context.getString(R.string.presim_header_observations))
            all.take(MAX_INSIGHTS).forEach { append("\n"); append(it) }
        }
    }

    private val LANE_ROLES = listOf("TOP", "JNG", "MID", "ADC", "SUP")
    private const val STRENGTH_GAP_CLEAR  = 3.0
    private const val STRENGTH_GAP_SLIGHT = 1.0
    private const val MAX_INSIGHTS        = 5
}
