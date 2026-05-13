package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Match
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.SeriesState
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

class FinalizeMatchUseCase(private val context: Context) {
    operator fun invoke(
        matchId: String,
        playerTeamId: String,
        playerScore: Int,
        opponentScore: Int
    ) {
        val gs = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: return
        val playerIsHome = match.homeTeamId == playerTeamId
        match.homeScore = if (playerIsHome) playerScore else opponentScore
        match.awayScore = if (playerIsHome) opponentScore else playerScore
        match.played = true
        gs.seriesState.remove(matchId)
        GameRepository.save(context)
    }
}
