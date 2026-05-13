package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.Player

/**
 * Lógica do mercado de transferências.
 *
 * Cada jogador tem um "preço de mercado" calculado a partir do overall e do salário:
 *   preço = (salario_mensal × 12) × multiplicador_overall
 * Onde o multiplicador cresce com o overall — jogador 90+ vale ~2.4x salário anual,
 * jogador 50- vale ~0.6x.
 */
object TransferMarket {

    fun marketPriceOf(player: Player): Long {
        val annual = (player.contrato.salario_mensal_estimado_brl ?: 0L) * 12
        val mult = when {
            player.overallRating() >= 85 -> 2.4
            player.overallRating() >= 75 -> 1.8
            player.overallRating() >= 65 -> 1.2
            player.overallRating() >= 55 -> 0.85
            else -> 0.6
        }
        return (annual * mult).toLong()
    }

    /** Sale do meu jogador: recebe o preço, jogador vai pra um time aleatório de outro. */
    fun sellPlayer(context: Context, playerId: String): SellResult {
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(context)
        val player = GameRepository.rosterOf(context, gs.managerTeamId).find { it.id == playerId }
            ?: return SellResult.Error("Jogador não está no seu elenco")

        val price = marketPriceOf(player)
        val otherTeams = snap.times.filter { it.id != gs.managerTeamId }
        val newTeam = otherTeams.random()

        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(newTeamId = newTeam.id, transferredOn = gs.currentDate, titular = false)
        }
        gs.budget += price
        GameRepository.log(
            "TRANSFER",
            "${player.nome_jogo} vendido para ${newTeam.nome} por R$ ${"%,d".format(price)}"
        )
        GameRepository.save(context)

        // Validação automática: se era titular, promove um reserva
        SquadManager.validateAndFixRoster(context)

        return SellResult.Ok(price, newTeam.nome)
    }

    /** Compra: paga o preço, jogador vem para o meu time como reserva. */
    fun buyPlayer(context: Context, playerId: String): BuyResult {
        val gs = GameRepository.current()
        val player = GameRepository.marketRoster(context).find { it.id == playerId }
            ?: return BuyResult.Error("Jogador não disponível no mercado")

        val price = marketPriceOf(player)
        if (gs.budget < price) {
            return BuyResult.Error("Orçamento insuficiente. Falta R$ ${"%,d".format(price - gs.budget)}")
        }

        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(newTeamId = gs.managerTeamId, transferredOn = gs.currentDate, titular = false)
        }
        gs.budget -= price
        GameRepository.log(
            "TRANSFER",
            "${player.nome_jogo} contratado por R$ ${"%,d".format(price)}"
        )
        GameRepository.save(context)

        // Validação automática: se tiver vaga de titular, promove o novo jogador
        SquadManager.validateAndFixRoster(context)

        return BuyResult.Ok(price)
    }

    /** Renegocia contrato do jogador: aplica novo salário e nova data. */
    fun renegotiateContract(
        context: Context,
        playerId: String,
        newMonthlySalary: Long,
        newEndDate: String
    ): Boolean {
        val gs = GameRepository.current()
        val player = GameRepository.rosterOf(context, gs.managerTeamId)
            .find { it.id == playerId } ?: return false

        // Jogador aceita se o novo salário >= 90% do atual; senão recusa
        val current = player.contrato.salario_mensal_estimado_brl ?: 0L
        if (newMonthlySalary < (current * 0.9).toLong()) {
            GameRepository.log("CONTRACT", "${player.nome_jogo} recusou a oferta salarial.")
            GameRepository.save(context)
            return false
        }

        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(newSalary = newMonthlySalary, newContractEnd = newEndDate)
        }
        GameRepository.log(
            "CONTRACT",
            "${player.nome_jogo} renovou até $newEndDate por R$ ${"%,d".format(newMonthlySalary)}/mês"
        )
        GameRepository.save(context)
        return true
    }

    /** Promove ou rebaixa jogador (titular/reserva). */
    fun toggleStarter(context: Context, playerId: String) {
        val gs = GameRepository.current()
        val player = GameRepository.rosterOf(context, gs.managerTeamId)
            .find { it.id == playerId } ?: return
        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(titular = !player.titular)
        }
        val newStatus = if (!player.titular) "titular" else "reserva"
        GameRepository.log(
            "SQUAD",
            "${player.nome_jogo} agora é $newStatus"
        )
        GameRepository.save(context)

        // Validação automática: garante que há sempre 5 titulares
        SquadManager.validateAndFixRoster(context)
    }
}

sealed class SellResult {
    data class Ok(val price: Long, val toTeam: String) : SellResult()
    data class Error(val msg: String) : SellResult()
}

sealed class BuyResult {
    data class Ok(val price: Long) : BuyResult()
    data class Error(val msg: String) : BuyResult()
}
