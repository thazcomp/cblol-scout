package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Player
import com.cblol.scout.game.BuyResult
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.TransferMarket

class GetMarketRosterUseCase(private val context: Context) {
    operator fun invoke(roleFilter: String? = null): List<Player> {
        var market = GameRepository.marketRoster(context)
        if (roleFilter != null && roleFilter != "ALL") {
            market = market.filter { it.role == roleFilter }
        }
        return market.sortedByDescending { it.overallRating() }
    }
}

class BuyPlayerUseCase(private val context: Context) {
    operator fun invoke(playerId: String): BuyResult =
        TransferMarket.buyPlayer(context, playerId)
}

class GetMarketPriceUseCase {
    operator fun invoke(player: Player): Long =
        TransferMarket.marketPriceOf(player)
}
