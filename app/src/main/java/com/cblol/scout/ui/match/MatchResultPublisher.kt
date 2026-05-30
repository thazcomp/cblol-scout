package com.cblol.scout.ui.match

import android.app.Activity
import android.content.Intent
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Match
import com.cblol.scout.databinding.ActivityMatchSimulationBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.NewsService
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.MatchSimulator
import com.cblol.scout.ui.MatchResultActivity
import java.time.LocalDate

/**
 * Encapsula a finalização de uma série: persistir resultado, calcular prêmio,
 * avançar calendário, publicar notícia e lançar a [MatchResultActivity].
 *
 * Extraído da [com.cblol.scout.ui.MatchSimulationActivity] para isolar a
 * "saída" do fluxo de simulação — antes a Activity tinha ~150 linhas só
 * cuidando disso, misturando regras de prêmio, ordem de chamada de
 * GameEngine.advanceCalendarTo/simulateOpponentMatchesToday, log e cobertura
 * jornalística.
 *
 * **SOLID:**
 *  - **SRP**: única responsabilidade: encerrar mapa/série e abrir tela de
 *    resultado.
 *  - **OCP**: novas categorias de cobertura ou prêmios (ex: bônus por sweep)
 *    entram aqui sem mexer no resto.
 *  - **Idempotência**: a Activity passa um guard (`resultApplied`) — uma vez
 *    chamado, não duplica.
 */
internal class MatchResultPublisher(
    private val activity: Activity,
    private val binding: ActivityMatchSimulationBinding,
    private val match: Match,
    private val stats: MatchStatsAccumulator,
    private val seriesScore: () -> Pair<Int, Int>
) {

    /**
     * Aplica o resultado do mapa recém-encerrado no estado e abre a
     * [MatchResultActivity].
     *
     * @param homeMapsFinal placar acumulado da série do lado home
     * @param awayMapsFinal placar acumulado da série do lado away
     * @param mapWonByHome quem venceu **este** mapa (não necessariamente quem
     *   lidera a série). É essencial passar isto explicitamente, não derivar
     *   de `homeMapsFinal > awayMapsFinal`: o placar pode estar EMPATADO
     *   após um mapa (ex: 1-1) e aí a comparação não diria quem venceu o mapa
     *   atual. Bug histórico fazia a tela mostrar "VITÓRIA" para o lado away
     *   mesmo quando o home tinha vencido o mapa que levou ao empate.
     */
    fun publish(homeMapsFinal: Int, awayMapsFinal: Int, mapWonByHome: Boolean) {
        val seriesFinished = homeMapsFinal >= GameConstants.Series.MAPS_TO_WIN ||
                             awayMapsFinal >= GameConstants.Series.MAPS_TO_WIN

        match.homeScore = homeMapsFinal
        match.awayScore = awayMapsFinal
        if (seriesFinished) {
            match.played      = true
            match.pickBanPlan = null
        }

        val gs     = GameRepository.current()
        // Vencedor a reportar para a tela de resultado:
        //  - Série terminada → líder do placar (necessariamente fez 2 mapas)
        //  - Mapa intermediário → quem venceu ESTE mapa (mapWonByHome)
        val winner = if (seriesFinished) {
            if (homeMapsFinal > awayMapsFinal) match.homeTeamId else match.awayTeamId
        } else {
            if (mapWonByHome) match.homeTeamId else match.awayTeamId
        }
        val isMine = match.homeTeamId == gs.managerTeamId || match.awayTeamId == gs.managerTeamId

        val prize = if (seriesFinished && isMine)
            calculatePrize(winner, homeMapsFinal, awayMapsFinal, gs.managerTeamId)
        else 0L
        if (prize > 0L) gs.budget += prize

        // Avança o calendário até a data da partida processando TODOS os ticks
        // diários dos dias intermediários. `advanceCalendarTo` é no-op se a
        // data da partida não for posterior à data atual.
        if (seriesFinished) {
            val today    = LocalDate.parse(gs.currentDate)
            val matchDay = LocalDate.parse(match.date)
            if (today.isBefore(matchDay)) {
                GameEngine.advanceCalendarTo(activity.applicationContext, match.date)
            }
            // Garante que as partidas dos OUTROS times do MESMO dia da partida
            // do gerente também sejam simuladas — [advanceCalendarTo] só simula
            // partidas estritamente anteriores a `match.date` (loop usa
            // `isBefore`), então sem esta chamada extra a tabela mostraria os
            // jogos do dia da partida do gerente ainda pendentes.
            GameEngine.simulateOpponentMatchesToday(activity.applicationContext)
        }

        if (seriesFinished) {
            GameRepository.log("MATCH",
                "Rodada ${match.round}: ${binding.tvHomeName.text} $homeMapsFinal-$awayMapsFinal ${binding.tvAwayName.text}")
            // Cobertura jornalística: só para partidas do time do gerente.
            if (isMine) publishMatchNews(gs, winner, homeMapsFinal, awayMapsFinal)
        }
        GameRepository.save(activity.applicationContext)

        launchResultActivity(homeMapsFinal, awayMapsFinal, winner, prize, seriesFinished, gs.managerTeamId)
    }

    /**
     * Publica a notícia da série recém-encerrada no feed. Detecta zebra
     * comparando a força dos dois elencos — mesma heurística do GameEngine
     * para partidas auto-simuladas, mantendo o tom consistente.
     */
    private fun publishMatchNews(
        gs: GameState,
        winnerId: String,
        homeMapsFinal: Int,
        awayMapsFinal: Int
    ) {
        val managerIsHome = match.homeTeamId == gs.managerTeamId
        val opponentId = if (managerIsHome) match.awayTeamId else match.homeTeamId
        val managerMaps = if (managerIsHome) homeMapsFinal else awayMapsFinal
        val opponentMaps = if (managerIsHome) awayMapsFinal else homeMapsFinal
        val managerWon = winnerId == gs.managerTeamId

        val managerStrength = MatchSimulator.teamStrength(
            GameRepository.rosterOf(activity.applicationContext, gs.managerTeamId)
        )
        val opponentStrength = MatchSimulator.teamStrength(
            GameRepository.rosterOf(activity.applicationContext, opponentId)
        )
        val wasUpset = if (managerWon) managerStrength + 5 <= opponentStrength
                       else opponentStrength + 5 <= managerStrength

        NewsService.reportMatchResult(
            state = gs,
            managerTeamName = if (managerIsHome) binding.tvHomeName.text.toString()
                              else binding.tvAwayName.text.toString(),
            opponentName = if (managerIsHome) binding.tvAwayName.text.toString()
                           else binding.tvHomeName.text.toString(),
            managerWon = managerWon,
            managerMaps = managerMaps,
            opponentMaps = opponentMaps,
            wasUpset = wasUpset
        )
    }

    private fun calculatePrize(winner: String, homeMaps: Int, awayMaps: Int, managerId: String): Long {
        return if (winner == managerId) {
            val mapsWon = maxOf(homeMaps, awayMaps)
            GameConstants.Economy.PRIZE_PER_SERIES_WIN +
                GameConstants.Economy.PRIZE_PER_MAP_WIN * mapsWon
        } else {
            val myMaps = if (match.homeTeamId == managerId) homeMaps else awayMaps
            GameConstants.Economy.PRIZE_PER_MAP_WIN * myMaps
        }
    }

    private fun launchResultActivity(
        homeMapsFinal: Int, awayMapsFinal: Int, winner: String,
        prize: Long, seriesFinished: Boolean, managerId: String
    ) {
        val isPlayerHome   = match.homeTeamId == managerId
        val opponentTeamId = if (isPlayerHome) match.awayTeamId else match.homeTeamId
        val (homeMaps, awayMaps) = seriesScore()

        activity.startActivity(Intent(activity, MatchResultActivity::class.java).apply {
            putExtra(MatchResultActivity.EXTRA_HOME_NAME,        binding.tvHomeName.text.toString())
            putExtra(MatchResultActivity.EXTRA_AWAY_NAME,        binding.tvAwayName.text.toString())
            putExtra(MatchResultActivity.EXTRA_HOME_ID,          match.homeTeamId)
            putExtra(MatchResultActivity.EXTRA_AWAY_ID,          match.awayTeamId)
            putExtra(MatchResultActivity.EXTRA_HOME_SCORE,       homeMapsFinal)
            putExtra(MatchResultActivity.EXTRA_AWAY_SCORE,       awayMapsFinal)
            putExtra(MatchResultActivity.EXTRA_WINNER_ID,        winner)
            putExtra(MatchResultActivity.EXTRA_MANAGER_ID,       managerId)
            putExtra(MatchResultActivity.EXTRA_HOME_KILLS,       stats.homeKills)
            putExtra(MatchResultActivity.EXTRA_AWAY_KILLS,       stats.awayKills)
            putExtra(MatchResultActivity.EXTRA_HOME_TOWERS,      stats.homeTowers)
            putExtra(MatchResultActivity.EXTRA_AWAY_TOWERS,      stats.awayTowers)
            putExtra(MatchResultActivity.EXTRA_HOME_DRAGONS,     stats.homeDragons)
            putExtra(MatchResultActivity.EXTRA_AWAY_DRAGONS,     stats.awayDragons)
            putExtra(MatchResultActivity.EXTRA_HOME_BARONS,      stats.homeBarons)
            putExtra(MatchResultActivity.EXTRA_AWAY_BARONS,      stats.awayBarons)
            putExtra(MatchResultActivity.EXTRA_PRIZE,            prize)
            putExtra(MatchResultActivity.EXTRA_MATCH_ID,         match.id)
            putExtra(MatchResultActivity.EXTRA_MAP_NUMBER,       homeMaps + awayMaps)
            putExtra(MatchResultActivity.EXTRA_PLAYER_TEAM_ID,   managerId)
            putExtra(MatchResultActivity.EXTRA_OPPONENT_TEAM_ID, opponentTeamId)
            putExtra(MatchResultActivity.EXTRA_SERIES_FINISHED,  seriesFinished)
        })
    }
}
