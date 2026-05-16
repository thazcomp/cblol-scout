package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import com.cblol.scout.data.Match
import com.cblol.scout.data.Player
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.CompositionRepository

/**
 * Dialog de pré-simulação.
 *
 *  * Antes de cada partida (manual ou automática), exibe um sumário com:
 *  *   - Sinergia detectada de cada lado (composições, % de montagem, bônus)
 *  *   - Insights de balanceamento (AD+AP, falta de frontline, anti-tank, etc.)
 *  *   - Comparação de overall dos titulares por lane
 *  *   - Vantagens individuais por confronto direto (TOP/JNG/MID/ADC/SUP)
 *
 * Quando o usuário toca "Iniciar simulação", abre MatchSimulationActivity.
 *
 * Reutilizado por: ScheduleActivity, PickBanRouterActivity, MatchResultActivity.
 */
object PreSimulationDialog {

    /**
     * Mostra o dialog. Quando o usuário confirmar, executa [onConfirm].
     * Se ele cancelar, nada acontece (a Activity chamadora decide o fluxo).
     */
    fun show(context: Context, matchId: String, onConfirm: () -> Unit) {
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: run { onConfirm(); return }
        val snap  = GameRepository.snapshot(context)

        val homeName = snap.times.find { it.id == match.homeTeamId }?.nome ?: match.homeTeamId
        val awayName = snap.times.find { it.id == match.awayTeamId }?.nome ?: match.awayTeamId

        val homeRoster = startersOf(context, match.homeTeamId)
        val awayRoster = startersOf(context, match.awayTeamId)

        // Determina mapa atual: 1, 2 ou 3
        val gameNumber  = match.homeScore + match.awayScore + 1
        val plan        = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
        val bluePicks   = plan?.bluePicks ?: emptyList()
        val redPicks    = plan?.redPicks  ?: emptyList()
        val blueBans    = plan?.blueBans  ?: emptyList()
        val redBans     = plan?.redBans   ?: emptyList()

        val homeIsBlue  = (gameNumber % 2 == 1)
        val homePicks   = if (homeIsBlue) bluePicks else redPicks
        val awayPicks   = if (homeIsBlue) redPicks  else bluePicks
        val homeBansFor = if (homeIsBlue) redBans   else blueBans   // bans contra o home
        val awayBansFor = if (homeIsBlue) blueBans  else redBans

        val homeComp = CompositionRepository.analyzeWithTags(homePicks, awayPicks, homeBansFor)
        val awayComp = CompositionRepository.analyzeWithTags(awayPicks, homePicks, awayBansFor)

        val message = buildMessage(
            homeName, awayName,
            homeRoster, awayRoster,
            homeComp, awayComp,
            mapNumber = gameNumber
        )

        stylizedDialog(context)
            .setTitle("⚔️ Mapa $gameNumber · $homeName vs $awayName")
            .setMessage(message)
            .setPositiveButton("Iniciar simulação") { _, _ -> onConfirm() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Wrapper que mostra o dialog e em seguida lança MatchSimulationActivity.
     * Útil para callers que sempre vão simular ao confirmar.
     */
    fun showThenSimulate(context: Context, matchId: String) {
        show(context, matchId) {
            context.startActivity(
                Intent(context, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId)
            )
        }
    }

    // ── Construção da mensagem ──────────────────────────────────────────

    private fun buildMessage(
        homeName: String,
        awayName: String,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        homeComp: CompositionRepository.TaggedAnalysisResult,
        awayComp: CompositionRepository.TaggedAnalysisResult,
        mapNumber: Int
    ): String = buildString {

        // ── Bloco 1: sinergia ────────────────────────────────────────────
        append("📊 SINERGIA DA COMPOSIÇÃO\n")
        append(formatCompSide(homeName, homeComp))
        append("\n")
        append(formatCompSide(awayName, awayComp))

        // ── Bloco 2: confrontos por lane ─────────────────────────────────
        if (homeRoster.isNotEmpty() && awayRoster.isNotEmpty()) {
            append("\n\n⚔️ CONFRONTOS POR LANE\n")
            val roles = listOf("TOP", "JNG", "MID", "ADC", "SUP")
            for (role in roles) {
                val h = homeRoster.firstOrNull { it.role == role }
                val a = awayRoster.firstOrNull { it.role == role }
                if (h == null || a == null) continue
                val hOvr = h.overallRating()
                val aOvr = a.overallRating()
                val diff = hOvr - aOvr
                val arrow = when {
                    diff >=  5 -> "◀◀"
                    diff >=  2 -> "◀"
                    diff <= -5 -> "▶▶"
                    diff <= -2 -> "▶"
                    else       -> "≈"
                }
                append("\n$role · ${h.nome_jogo} ($hOvr) $arrow ${a.nome_jogo} ($aOvr)")
            }

            // ── Bloco 3: força geral ─────────────────────────────────────
            val homeAvg = homeRoster.map { it.overallRating() }.average()
            val awayAvg = awayRoster.map { it.overallRating() }.average()
            val gap = homeAvg - awayAvg
            append("\n\n📈 FORÇA MÉDIA")
            append("\n$homeName: %.1f · $awayName: %.1f".format(homeAvg, awayAvg))
            append("\n")
            append(when {
                gap >=  3 -> "✅ Vantagem clara para $homeName"
                gap >=  1 -> "▫ Leve favoritismo de $homeName"
                gap <= -3 -> "✅ Vantagem clara para $awayName"
                gap <= -1 -> "▫ Leve favoritismo de $awayName"
                else      -> "▫ Times muito equilibrados"
            })
        }

        // ── Bloco 4: insights ────────────────────────────────────────────
        val allInsights = (homeComp.insights.map { "[$homeName] $it" } +
                          awayComp.insights.map { "[$awayName] $it" })
        if (allInsights.isNotEmpty()) {
            append("\n\n💡 OBSERVAÇÕES")
            allInsights.take(5).forEach { append("\n$it") }
        }
    }

    private fun formatCompSide(
        teamName: String,
        result: CompositionRepository.TaggedAnalysisResult
    ): String = buildString {
        append("\n$teamName: ")
        if (result.detectedComps.isEmpty()) {
            append("sem composição forte (+${result.totalBonus})")
        } else {
            val main = result.detectedComps.take(2).joinToString(" + ") {
                "${it.composition.name} (${it.percent}%)"
            }
            append("$main · +${result.totalBonus} força")
        }
    }

    private fun startersOf(context: Context, teamId: String): List<Player> =
        GameRepository.rosterOf(context, teamId).filter { it.titular }
}
