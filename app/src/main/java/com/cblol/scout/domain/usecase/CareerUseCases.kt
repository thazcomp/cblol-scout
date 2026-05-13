package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository

class StartNewCareerUseCase(private val context: Context) {
    operator fun invoke(managerName: String, teamId: String): GameState =
        GameEngine.startNewCareer(context, managerName, teamId)
}

class LoadCareerUseCase(private val context: Context) {
    operator fun invoke(): GameState? = GameRepository.load(context)
}

class HasSaveUseCase(private val context: Context) {
    operator fun invoke(): Boolean = GameRepository.hasSave(context)
}

class ClearCareerUseCase(private val context: Context) {
    operator fun invoke() = GameRepository.clear(context)
}

class ValidateRosterUseCase(private val context: Context) {
    operator fun invoke() = com.cblol.scout.game.SquadManager.validateAndFixRoster(context)
}

class IsMissingStarterUseCase(private val context: Context) {
    operator fun invoke(): Boolean = com.cblol.scout.game.SquadManager.isMissingStarter(context)
}

class StarterCountUseCase(private val context: Context) {
    operator fun invoke(): Int = com.cblol.scout.game.SquadManager.starterCount(context)
}

class CanSellPlayerUseCase(private val context: Context) {
    operator fun invoke(playerId: String): Boolean =
        com.cblol.scout.game.SquadManager.canSellPlayer(context, playerId)
}
