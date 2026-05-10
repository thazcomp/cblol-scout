package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.Match
import com.cblol.scout.data.Player
import com.cblol.scout.data.Standing
import kotlin.random.Random

/**
 * Simula partidas BO3 (primeiro a 2 mapas).
 *
 * Cada time tem uma "força" = média do overall dos 5 titulares. O vencedor de cada mapa é
 * decidido por (homeForça - awayForça + bônus de mando + ruído). É simples, mas suficiente
 * pra dar variabilidade interessante e respeitar quem tem o elenco mais forte.
 */
object MatchSimulator {

    private const val HOME_BONUS = 4
    private const val NOISE_RANGE = 14   // ±14

    fun simulate(context: Context, match: Match): Match {
        val home = teamStrength(GameRepository.rosterOf(context, match.homeTeamId)) + HOME_BONUS
        val away = teamStrength(GameRepository.rosterOf(context, match.awayTeamId))

        var hWins = 0
        var aWins = 0
        while (hWins < 2 && aWins < 2) {
            val noise = Random.nextInt(-NOISE_RANGE, NOISE_RANGE + 1)
            if ((home - away) + noise >= 0) hWins++ else aWins++
        }
        match.played = true
        match.homeScore = hWins
        match.awayScore = aWins
        return match
    }

    /** Força do time: média de overall dos titulares. Se < 5 titulares, complementa com reservas. */
    fun teamStrength(roster: List<Player>): Int {
        val starters = roster.filter { it.titular }
        val pool = if (starters.size >= 5) starters
                   else (starters + roster.filter { !it.titular }.sortedByDescending { it.overallRating() })
                       .take(5)
        if (pool.isEmpty()) return 50
        return pool.sumOf { it.overallRating() } / pool.size
    }

    /** Calcula a tabela do split a partir das partidas jogadas. */
    fun computeStandings(context: Context): List<Standing> {
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(context)
        val perTeam = snap.times.associate { team ->
            team.id to mutableMapOf(
                "name" to team.nome, "wins" to 0, "losses" to 0, "mw" to 0, "ml" to 0
            )
        }.toMutableMap()

        gs.matches.filter { it.played }.forEach { m ->
            val winner = m.winnerTeamId()!!
            val loser = if (winner == m.homeTeamId) m.awayTeamId else m.homeTeamId
            perTeam[winner]?.let {
                it["wins"] = (it["wins"] as Int) + 1
                it["mw"]   = (it["mw"]   as Int) + maxOf(m.homeScore, m.awayScore)
                it["ml"]   = (it["ml"]   as Int) + minOf(m.homeScore, m.awayScore)
            }
            perTeam[loser]?.let {
                it["losses"] = (it["losses"] as Int) + 1
                it["mw"]     = (it["mw"]     as Int) + minOf(m.homeScore, m.awayScore)
                it["ml"]     = (it["ml"]     as Int) + maxOf(m.homeScore, m.awayScore)
            }
        }

        return perTeam.map { (id, m) ->
            Standing(
                teamId = id,
                teamName = m["name"] as String,
                wins = m["wins"] as Int,
                losses = m["losses"] as Int,
                mapsWon = m["mw"] as Int,
                mapsLost = m["ml"] as Int
            )
        }.sortedWith(
            compareByDescending<Standing> { it.wins }
                .thenByDescending { it.mapDiff }
                .thenByDescending { it.mapsWon }
        )
    }
}
