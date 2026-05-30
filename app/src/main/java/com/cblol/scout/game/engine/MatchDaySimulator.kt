package com.cblol.scout.game.engine

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Match
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.NewsService
import com.cblol.scout.game.AdvanceReport
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.MatchSimulator
import com.cblol.scout.game.SquadManager

/**
 * Simula as partidas do calendário durante o avanço de dias.
 *
 * Dois modos:
 *  - **`simulateAllOn(iso)`**: simula TODAS as partidas do dia (inclusive a do
 *    gerente). Usado pelo [com.cblol.scout.game.GameEngine.advanceDays], que é
 *    o modo "rodar tudo automático" — útil para testes e cenários sem o
 *    jogador no comando.
 *  - **`simulateOpponentsOn(iso)`**: simula só as partidas que NÃO envolvem
 *    o gerente, deixando a dele pendente para que ele jogue manualmente. Usado
 *    pelo avanço normal de calendário ([advanceCalendarTo]).
 *
 * Extraído do [com.cblol.scout.game.GameEngine] para isolar a coordenação de
 * partidas + cobertura jornalística + premiação do orquestrador de calendário.
 */
internal object MatchDaySimulator {

    /**
     * Simula todas as partidas do dia [iso] (incluindo a do gerente, se houver).
     * Aplica prêmios e gera cobertura jornalística para partidas do gerente.
     */
    fun simulateAllOn(context: Context, gs: GameState, iso: String, report: AdvanceReport) {
        val todayMatches = gs.matches.filter { it.date == iso && !it.played }
        if (todayMatches.isEmpty()) return

        // Validação: garante que há 5 titulares antes de jogar
        SquadManager.validateAndFixRoster(context)

        todayMatches.forEach { m ->
            MatchSimulator.simulate(context, m)
            report.matchesPlayed += 1

            val isMyMatch = m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId
            if (isMyMatch) applyManagerMatchOutcome(gs, m, report)

            logMatch(context, m)

            // Cobertura jornalística: só para partidas do time do gerente.
            if (isMyMatch) publishMatchNews(context, gs, m)
        }
    }

    /**
     * Simula apenas as partidas dos OUTROS times do dia [iso], mantendo a
     * partida do gerente (se houver) intacta. Idempotente: partidas já
     * jogadas são ignoradas pelo filtro `!played`.
     */
    fun simulateOpponentsOn(context: Context, gs: GameState, iso: String, report: AdvanceReport) {
        val todayMatches = gs.matches.filter {
            it.date == iso && !it.played &&
                it.homeTeamId != gs.managerTeamId &&
                it.awayTeamId != gs.managerTeamId
        }
        todayMatches.forEach { m ->
            MatchSimulator.simulate(context, m)
            report.matchesPlayed += 1
            logMatch(context, m)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Aplica prêmio e marca W/L no relatório para a partida do gerente.
     * Mantém a regra de prêmio em UM ÚNICO LUGAR (DRY): vitória paga
     * [GameConstants.Economy.PRIZE_PER_SERIES_WIN] + por mapa vencido; derrota
     * paga só o valor por mapa que conseguiu ganhar.
     */
    private fun applyManagerMatchOutcome(gs: GameState, m: Match, report: AdvanceReport) {
        val winnerId = m.winnerTeamId()
        if (winnerId == gs.managerTeamId) {
            val prize = GameConstants.Economy.PRIZE_PER_SERIES_WIN +
                (GameConstants.Economy.PRIZE_PER_MAP_WIN * maxOf(m.homeScore, m.awayScore))
            gs.budget += prize
            report.income += prize
            report.myWin = true
        } else {
            val mapPrize = GameConstants.Economy.PRIZE_PER_MAP_WIN *
                (if (m.homeTeamId == gs.managerTeamId) m.homeScore else m.awayScore)
            gs.budget += mapPrize
            report.income += mapPrize
            report.myLoss = true
        }
    }

    private fun logMatch(context: Context, m: Match) {
        val homeName = teamName(context, m.homeTeamId)
        val awayName = teamName(context, m.awayTeamId)
        GameRepository.log(
            "MATCH",
            "Rodada ${m.round}: $homeName ${m.homeScore}-${m.awayScore} $awayName"
        )
    }

    /**
     * Publica a notícia de uma partida do time do gerente no feed, detectando
     * se o resultado foi uma "zebra" (favorito caiu) ao comparar a força dos
     * dois elencos.
     */
    private fun publishMatchNews(context: Context, gs: GameState, m: Match) {
        val managerIsHome = m.homeTeamId == gs.managerTeamId
        val opponentId = if (managerIsHome) m.awayTeamId else m.homeTeamId
        val managerMaps = if (managerIsHome) m.homeScore else m.awayScore
        val opponentMaps = if (managerIsHome) m.awayScore else m.homeScore
        val managerWon = m.winnerTeamId() == gs.managerTeamId

        val managerStrength = MatchSimulator.teamStrength(
            GameRepository.rosterOf(context, gs.managerTeamId)
        )
        val opponentStrength = MatchSimulator.teamStrength(
            GameRepository.rosterOf(context, opponentId)
        )
        // Zebra = quem tinha desvantagem clara de força (>= 5 pontos) venceu.
        val wasUpset = if (managerWon) managerStrength + 5 <= opponentStrength
                       else opponentStrength + 5 <= managerStrength

        NewsService.reportMatchResult(
            state = gs,
            managerTeamName = teamName(context, gs.managerTeamId),
            opponentName = teamName(context, opponentId),
            managerWon = managerWon,
            managerMaps = managerMaps,
            opponentMaps = opponentMaps,
            wasUpset = wasUpset
        )
    }

    /** Resolve o nome de um time pelo id consultando ambas as divisões. */
    private fun teamName(context: Context, teamId: String): String {
        GameRepository.snapshot(context).times.find { it.id == teamId }?.let { return it.nome }
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return teamId
        return gs.secondDivisionTeams.find { it.id == teamId }?.nome ?: teamId
    }
}
