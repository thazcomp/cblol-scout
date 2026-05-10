package com.cblol.scout.game

import com.cblol.scout.data.Match
import java.time.LocalDate
import java.util.UUID

/**
 * Gera o calendário do CBLOL como round-robin duplo (cada time joga contra cada um, ida e volta).
 * 8 times → 14 rodadas → 56 partidas. Cada rodada acontece a cada 4 dias começando em
 * `splitStart`, então o split inteiro dura ~56 dias (≈ 8 semanas).
 *
 * Algoritmo: round-robin clássico (primeiro fica fixo, demais rotacionam).
 */
object ScheduleGenerator {

    private const val DAYS_BETWEEN_ROUNDS = 4

    fun generate(teamIds: List<String>, splitStart: String): List<Match> {
        require(teamIds.size == 8) { "Esperado 8 times, recebeu ${teamIds.size}" }
        val startDate = LocalDate.parse(splitStart)

        val firstHalf = roundRobin(teamIds)
        val secondHalf = firstHalf.map { round ->
            // ida & volta: inverte mando de campo
            round.map { (h, a) -> a to h }
        }
        val allRounds = firstHalf + secondHalf

        val matches = mutableListOf<Match>()
        allRounds.forEachIndexed { roundIndex, pairs ->
            val date = startDate.plusDays((roundIndex * DAYS_BETWEEN_ROUNDS).toLong())
            pairs.forEach { (home, away) ->
                matches.add(
                    Match(
                        id = UUID.randomUUID().toString().take(8),
                        date = date.toString(),
                        round = roundIndex + 1,
                        homeTeamId = home,
                        awayTeamId = away
                    )
                )
            }
        }
        return matches
    }

    /** Retorna lista de rodadas (cada rodada = 4 pares de times). */
    private fun roundRobin(teamIds: List<String>): List<List<Pair<String, String>>> {
        val n = teamIds.size
        val rotated = teamIds.toMutableList()
        val rounds = mutableListOf<List<Pair<String, String>>>()
        repeat(n - 1) {
            val pairs = mutableListOf<Pair<String, String>>()
            for (i in 0 until n / 2) {
                pairs.add(rotated[i] to rotated[n - 1 - i])
            }
            rounds.add(pairs)
            // rotaciona mantendo o primeiro fixo
            val last = rotated.removeAt(n - 1)
            rotated.add(1, last)
        }
        return rounds
    }
}
