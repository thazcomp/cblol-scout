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

    /** Validação automática: garante que há sempre 5 titulares (1 por role). */
    fun validateAndFixRoster(context: Context): List<String> {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val logs = mutableListOf<String>()

        // Identifica roles com problema
        val startersByRole = roster.filter { it.titular }.groupBy { it.role }
        val roles = listOf("TOP", "JNG", "MID", "ADC", "SUP")

        for (role in roles) {
            val starters = startersByRole[role] ?: emptyList()

            when (starters.size) {
                0 -> {
                    // Sem titular nessa role — promove melhor reserva
                    val bestReserve = roster
                        .filter { !it.titular && it.role == role }
                        .maxByOrNull { it.overallRating() }

                    if (bestReserve != null) {
                        GameRepository.updateOverride(bestReserve.id) { it.copy(titular = true) }
                        logs.add("AUTO: ${bestReserve.nome_jogo} promovido a titular de $role")
                    } else {
                        logs.add("ERRO: Sem reserva disponível para $role")
                    }
                }
                in 2..5 -> {
                    // Mais de 1 titular nessa role — rebaixa os piores
                    val sorted = starters.sortedByDescending { it.overallRating() }
                    for (i in 1 until sorted.size) {
                        GameRepository.updateOverride(sorted[i].id) { it.copy(titular = false) }
                        logs.add("AUTO: ${sorted[i].nome_jogo} rebaixado a reserva de $role")
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            for (log in logs) {
                GameRepository.log("SQUAD_AUTO", log.replace("AUTO: ", ""))
            }
            GameRepository.save(context)
        }

        return logs
    }

    /** Verifica se o elenco está com um jogador faltando em alguma role. */
    fun isMissingStarter(context: Context): Boolean {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val startersByRole = roster.filter { it.titular }.groupBy { it.role }
        val roles = listOf("TOP", "JNG", "MID", "ADC", "SUP")
        return roles.any { startersByRole[it]?.size != 1 }
    }

    /** Retorna o número de titulares no elenco. */
    fun starterCount(context: Context): Int {
        val gs = GameRepository.current()
        return GameRepository.rosterOf(context, gs.managerTeamId).count { it.titular }
    }

    /** Inicializa um elenco válido para um novo jogo (garante 5 titulares no primeiro carregamento). */
    fun initializeRoster(context: Context) {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)

        // Agrupa jogadores por role
        val playersByRole = roster.groupBy { it.role }
        val roles = listOf("TOP", "JNG", "MID", "ADC", "SUP")

        // Para cada role, define o melhor jogador como titular e os outros como reservas
        for (role in roles) {
            val playersInRole = playersByRole[role] ?: continue
            val sorted = playersInRole.sortedByDescending { it.overallRating() }

            for ((index, player) in sorted.withIndex()) {
                GameRepository.updateOverride(player.id) { ov ->
                    ov.copy(titular = index == 0)
                }
            }
        }

        GameRepository.log("SQUAD_INIT", "Elenco inicializado com 5 titulares")
        GameRepository.save(context)
    }

    /** Verifica se é seguro vender um jogador (se é reserva ou há substituto disponível). */
    fun canSellPlayer(context: Context, playerId: String): Boolean {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val player = roster.find { it.id == playerId } ?: return false

        // Se for reserva, sempre pode vender
        if (!player.titular) return true

        // Se for titular, precisa haver reserva para substituir
        val hasReserve = roster
            .filter { !it.titular && it.role == player.role }
            .isNotEmpty()

        return hasReserve
    }
}

sealed class PromoteResult {
    data object NotFound : PromoteResult()
    data object AlreadyStarter : PromoteResult()
    data object Promoted : PromoteResult()
    data class Swapped(val replaced: Player) : PromoteResult()
}
