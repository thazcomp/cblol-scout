package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Player
import com.cblol.scout.data.Standing
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.MatchSimulator
import com.cblol.scout.game.PromoteResult
import com.cblol.scout.game.SquadManager

class GetRosterUseCase(private val context: Context) {
    operator fun invoke(): List<Player> {
        val gs = GameRepository.current()
        return GameRepository.rosterOf(context, gs.managerTeamId)
    }
}

class GetStartersUseCase(private val context: Context) {
    operator fun invoke(): List<Player> =
        GetRosterUseCase(context)().filter { it.titular }
            .sortedBy { roleOrder(it.role) }

    private fun roleOrder(role: String) = when (role) {
        "TOP" -> 1; "JNG" -> 2; "MID" -> 3; "ADC" -> 4; "SUP" -> 5; else -> 6
    }
}

class GetReservesUseCase(private val context: Context) {
    operator fun invoke(): List<Player> =
        GetRosterUseCase(context)().filter { !it.titular }
            .sortedByDescending { it.overallRating() }
}

class SwapStartersUseCase(private val context: Context) {
    operator fun invoke(starterId: String, replacementId: String): Boolean =
        SquadManager.swapStarters(context, starterId, replacementId)
}

class PromoteFromBenchUseCase(private val context: Context) {
    operator fun invoke(reserveId: String): PromoteResult =
        SquadManager.promoteFromBench(context, reserveId)
}

class GetStandingsUseCase(private val context: Context) {
    operator fun invoke(): List<Standing> =
        MatchSimulator.computeStandings(context)
}
