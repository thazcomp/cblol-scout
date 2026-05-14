package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Match
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.SeriesState
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository

class GetNextMatchUseCase(private val context: Context) {
    operator fun invoke(): Match? {
        val gs = GameRepository.current()
        return gs.matches
            .filter { !it.played }
            .filter { it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId }
            .filter { it.date >= gs.currentDate }
            .minByOrNull { it.date }
    }
}

class GetAllMatchesUseCase(private val context: Context) {
    operator fun invoke(): List<Match> =
        GameRepository.current().matches.sortedWith(compareBy({ it.round }, { it.date }))
}

class SavePickBanPlanUseCase(private val context: Context) {
    operator fun invoke(matchId: String, plan: PickBanPlan) {
        val gs = GameRepository.current()
        gs.matches.find { it.id == matchId }?.pickBanPlan = plan
    }
}

class SimulateMapWithPicksUseCase(private val context: Context) {
    operator fun invoke(
        playerTeamId: String,
        opponentTeamId: String,
        playerPicks: List<String>,
        opponentPicks: List<String>,
        playerIsBlue: Boolean
    ): String {
        fun avgOverall(teamId: String): Double {
            val roster = GameRepository.rosterOf(context, teamId).filter { it.titular }
            return if (roster.isEmpty()) 75.0
            else roster.map { it.overallRating().toDouble() }.average()
        }
        val playerStr = avgOverall(playerTeamId) +
            playerPicks.size.coerceAtMost(5) +
            if (playerIsBlue) 2.0 else 0.0
        val opponentStr = avgOverall(opponentTeamId) +
            opponentPicks.size.coerceAtMost(5) +
            if (!playerIsBlue) 2.0 else 0.0
        return if (playerStr + (-8..8).random() > opponentStr) playerTeamId else opponentTeamId
    }
}

class UpdateSeriesStateUseCase(private val context: Context) {
    operator fun invoke(matchId: String, playerWon: Boolean): SeriesState {
        val gs = GameRepository.current()
        val prev = gs.seriesState[matchId] ?: SeriesState()
        val updated = prev.recordMap(playerWon)
        gs.seriesState[matchId] = updated
        return updated
    }
}

/**
 * Dados completos do resultado de uma série, usados pela MatchResultActivity.
 */
data class MatchResultData(
    val homeTeamId: String,
    val awayTeamId: String,
    val homeName: String,
    val awayName: String,
    val homeScore: Int,
    val awayScore: Int,
    val winnerId: String,
    val managerId: String,
    val prize: Long,
    // stats acumuladas na série
    val homeKills: Int   = 0,
    val awayKills: Int   = 0,
    val homeTowers: Int  = 0,
    val awayTowers: Int  = 0,
    val homeDragons: Int = 0,
    val awayDragons: Int = 0,
    val homeBarons: Int  = 0,
    val awayBarons: Int  = 0
)

class FinalizeMatchUseCase(private val context: Context) {
    /**
     * Grava o resultado, aplica prêmio e devolve um [MatchResultData] pronto
     * para ser passado à MatchResultActivity.
     *
     * @param seriesStats estatísticas acumuladas da série (opcional — zeros se não informado)
     */
    operator fun invoke(
        matchId: String,
        playerTeamId: String,
        playerScore: Int,
        opponentScore: Int,
        seriesStats: SeriesStats = SeriesStats()
    ): MatchResultData {
        val gs    = GameRepository.current()
        val match = gs.matches.find { it.id == matchId }
            ?: error("Partida não encontrada: $matchId")

        val playerIsHome = match.homeTeamId == playerTeamId
        match.homeScore  = if (playerIsHome) playerScore   else opponentScore
        match.awayScore  = if (playerIsHome) opponentScore else playerScore
        match.played     = true
        gs.seriesState.remove(matchId)

        val snap         = GameRepository.snapshot(context)
        val homeName     = snap.times.find { it.id == match.homeTeamId }?.nome ?: match.homeTeamId
        val awayName     = snap.times.find { it.id == match.awayTeamId }?.nome ?: match.awayTeamId
        val winnerId     = if (match.homeScore > match.awayScore) match.homeTeamId else match.awayTeamId

        // Calcula prêmio
        var prize = 0L
        if (winnerId == playerTeamId) {
            val mapsWon = maxOf(playerScore, opponentScore)
            prize = GameEngine.PRIZE_PER_SERIES_WIN + GameEngine.PRIZE_PER_MAP_WIN * mapsWon
        } else {
            prize = GameEngine.PRIZE_PER_MAP_WIN * playerScore
        }
        gs.budget += prize

        GameRepository.log(
            "MATCH",
            "Rodada ${match.round}: $homeName ${match.homeScore}-${match.awayScore} $awayName"
        )
        GameRepository.save(context)

        return MatchResultData(
            homeTeamId  = match.homeTeamId,
            awayTeamId  = match.awayTeamId,
            homeName    = homeName,
            awayName    = awayName,
            homeScore   = match.homeScore,
            awayScore   = match.awayScore,
            winnerId    = winnerId,
            managerId   = playerTeamId,
            prize       = prize,
            homeKills   = if (playerIsHome) seriesStats.playerKills   else seriesStats.opponentKills,
            awayKills   = if (playerIsHome) seriesStats.opponentKills  else seriesStats.playerKills,
            homeTowers  = if (playerIsHome) seriesStats.playerTowers   else seriesStats.opponentTowers,
            awayTowers  = if (playerIsHome) seriesStats.opponentTowers  else seriesStats.playerTowers,
            homeDragons = if (playerIsHome) seriesStats.playerDragons  else seriesStats.opponentDragons,
            awayDragons = if (playerIsHome) seriesStats.opponentDragons else seriesStats.playerDragons,
            homeBarons  = if (playerIsHome) seriesStats.playerBarons   else seriesStats.opponentBarons,
            awayBarons  = if (playerIsHome) seriesStats.opponentBarons  else seriesStats.playerBarons
        )
    }
}

/** Estatísticas acumuladas de uma série inteira (BO3). */
data class SeriesStats(
    val playerKills:    Int = 0,
    val opponentKills:  Int = 0,
    val playerTowers:   Int = 0,
    val opponentTowers: Int = 0,
    val playerDragons:  Int = 0,
    val opponentDragons:Int = 0,
    val playerBarons:   Int = 0,
    val opponentBarons: Int = 0
)
