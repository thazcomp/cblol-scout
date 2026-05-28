package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository

class StartNewCareerUseCase(private val context: Context) {
    /**
     * @param division divisão em que começar a carreira. Default é
     *   [com.cblol.scout.data.Division.FIRST] para preservar compatibilidade
     *   com chamadas que não especificam (e é o comportamento esperado para
     *   quem não opta pelo modo "começar de baixo").
     * @param seed semente para geração determinística dos times da 2ª divisão.
     *   A TeamSelectActivity passa o mesmo seed que usou para mostrar os times
     *   na seleção, garantindo que a carreira inicie com os times escolhidos.
     */
    operator fun invoke(
        managerName: String,
        teamId: String,
        division: com.cblol.scout.data.Division = com.cblol.scout.data.Division.FIRST,
        seed: Long = System.currentTimeMillis()
    ): GameState = GameEngine.startNewCareer(context, managerName, teamId, division, seed)
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
