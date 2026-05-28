package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository

class GetHubStateUseCase(private val context: Context) {
    operator fun invoke(): HubState {
        GameRepository.load(context)  // Ensure GameState is loaded
        val gs   = GameRepository.current()
        // Times do gerente podem estar no snapshot (1ª div) ou em
        // gs.secondDivisionTeams (2ª div). Centralizamos via
        // teamsForCurrentDivision para não quebrar em carreira do CD.
        val teams = GameRepository.teamsForCurrentDivision(context)
        val team  = teams.find { it.id == gs.managerTeamId }
            ?: error("Time do gerente não encontrado: ${gs.managerTeamId}")
        val next = GameEngine.nextMatchForManager()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)

        val nextMatchDisplay = next?.let {
            val home = teams.find { t -> t.id == it.homeTeamId }?.nome ?: it.homeTeamId
            val away = teams.find { t -> t.id == it.awayTeamId }?.nome ?: it.awayTeamId
            NextMatchDisplay(
                matchId    = it.id,
                label      = "$home  vs  $away",
                date       = it.date,
                round      = it.round,
                opponentId = if (it.homeTeamId == gs.managerTeamId) it.awayTeamId else it.homeTeamId
            )
        }

        return HubState(
            managerName    = gs.managerName,
            teamName       = team.nome,
            teamId         = team.id,
            currentDate    = gs.currentDate,
            budget         = gs.budget,
            monthlyPayroll = GameEngine.totalMonthlyPayroll(context),
            starterCount   = roster.count { it.titular },
            rosterSize     = roster.size,
            log            = gs.gameLog.take(30),
            nextMatch      = nextMatchDisplay
        )
    }
}

data class HubState(
    val managerName: String,
    val teamName: String,
    val teamId: String,
    val currentDate: String,
    val budget: Long,
    val monthlyPayroll: Long,
    val starterCount: Int,
    val rosterSize: Int,
    val log: List<com.cblol.scout.data.LogEntry>,
    val nextMatch: NextMatchDisplay?
)

data class NextMatchDisplay(
    val matchId: String,
    val label: String,
    val date: String,
    val round: Int,
    val opponentId: String
)
