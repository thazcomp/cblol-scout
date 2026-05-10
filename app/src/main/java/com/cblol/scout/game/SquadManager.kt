package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.Player

/**
 * Operações atômicas sobre o elenco do meu time:
 *  - swapStarters: troca dois jogadores em uma transação (1 sai, 1 entra) — escreve um único log
 *  - promoteFromBench: promove um reserva, rebaixando automaticamente o titular atual da mesma role
 *
 * Diferente de TransferMarket.toggleStarter, que muda o status de um jogador isoladamente,
 * essas ops mantêm a regra implícita "1 titular por role" sem deixar o time com 6 ou 4 titulares.
 */
object SquadManager {

    fun swapStarters(context: Context, outId: String, inId: String): Boolean {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val out = roster.find { it.id == outId } ?: return false
        val newcomer = roster.find { it.id == inId } ?: return false

        GameRepository.updateOverride(outId)      { it.copy(titular = false) }
        GameRepository.updateOverride(inId)       { it.copy(titular = true) }
        GameRepository.log(
            "SQUAD",
            "${newcomer.nome_jogo} entra; ${out.nome_jogo} vai para o banco"
        )
        GameRepository.save(context)
        return true
    }

    /** Promove um reserva. Se já houver um titular da mesma role, faz swap; senão só promove. */
    fun promoteFromBench(context: Context, reserveId: String): PromoteResult {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val reserve = roster.find { it.id == reserveId } ?: return PromoteResult.NotFound
        if (reserve.titular) return PromoteResult.AlreadyStarter

        val currentStarter = roster.find { it.titular && it.role == reserve.role }
        return if (currentStarter != null) {
            swapStarters(context, currentStarter.id, reserveId)
            PromoteResult.Swapped(replaced = currentStarter)
        } else {
            GameRepository.updateOverride(reserveId) { it.copy(titular = true) }
            GameRepository.log("SQUAD", "${reserve.nome_jogo} promovido a titular")
            GameRepository.save(context)
            PromoteResult.Promoted
        }
    }

    /** Reservas com a mesma role do titular dado (candidatos a substituí-lo). */
    fun reservesForRoleOf(context: Context, starter: Player): List<Player> {
        val gs = GameRepository.current()
        return GameRepository.rosterOf(context, gs.managerTeamId)
            .filter { !it.titular && it.role == starter.role }
            .sortedByDescending { it.overallRating() }
    }
}

sealed class PromoteResult {
    data object NotFound : PromoteResult()
    data object AlreadyStarter : PromoteResult()
    data object Promoted : PromoteResult()
    data class Swapped(val replaced: Player) : PromoteResult()
}
