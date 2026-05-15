package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Player
import com.cblol.scout.data.Side
import com.cblol.scout.data.PickBanPlan
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Versão "ao vivo" do simulador: gera uma timeline completa com pick/ban + eventos do jogo
 * (kills, torres, dragões, baron, herald, buffs) que pode ser tocada com delays na UI.
 *
 * Cada partida é BO3. Para cada mapa:
 *   1. fase de banimento (5 bans por lado, alternando)
 *   2. fase de picks (5 por lado, alternando seguindo padrão CBLOL/LCS)
 *   3. eventos do jogo até alguém vencer (Nexus = última torre + GameEnd)
 *
 * O resultado de cada mapa é decidido probabilisticamente pela diferença de força
 * dos elencos (mesma fórmula do MatchSimulator anterior).
 */
object LiveMatchEngine {

    /**
     * Gera a timeline de UM Único mapa e devolve o resultado.
     * O MatchSimulationActivity chama este método uma vez por mapa,
     * acumulando o placar externamente até a série terminar.
     */
    fun generateSingleMap(context: Context, match: Match, gameNumber: Int): MapResult {
        val homeRoster = startersOrTopFive(GameRepository.rosterOf(context, match.homeTeamId))
        val awayRoster = startersOrTopFive(GameRepository.rosterOf(context, match.awayTeamId))
        val homeStr    = teamStrength(homeRoster) + 4
        val awayStr    = teamStrength(awayRoster)
        val plan       = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null

        val (gameEvents, homeWon, finalKills, duration) =
            generateGame(gameNumber, homeRoster, awayRoster, homeStr, awayStr, plan)

        val events = gameEvents.toMutableList()
        events.add(MatchEvent.GameEnd(
            gameNumber      = gameNumber,
            winnerSide      = if (homeWon) Side.HOME else Side.AWAY,
            durationMinutes = duration,
            finalKills      = finalKills
        ))
        return MapResult(events, homeWon)
    }

    data class MapResult(
        val events: List<MatchEvent>,
        val homeWon: Boolean
    )

    /**
     * Mantido para compatibilidade com testes e simulações automáticas
     * (partidas entre outros times sem pick & ban manual).
     * Simula o BO3 completo de uma vez.
     */
    fun generateSeries(context: Context, match: Match): SeriesResult {
        val homeRoster = startersOrTopFive(GameRepository.rosterOf(context, match.homeTeamId))
        val awayRoster = startersOrTopFive(GameRepository.rosterOf(context, match.awayTeamId))

        val events = mutableListOf<MatchEvent>()
        var homeMaps = 0
        var awayMaps = 0
        var gameNumber = 1

        // Usa o PickBanPlan salvo no match (se houver) para o mapa correspondente
        while (homeMaps < 2 && awayMaps < 2) {
            val homeStr = teamStrength(homeRoster) + 4 // bônus de mando do split
            val awayStr = teamStrength(awayRoster)
            // O plano salvo no match refere-se ao último mapa com pick & ban feito pelo jogador.
            // Para os demais mapas o motor gera picks automaticamente.
            val plan = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
            val (gameEvents, homeWonGame, finalKills, duration) =
                generateGame(gameNumber, homeRoster, awayRoster, homeStr, awayStr, plan)
            events.addAll(gameEvents)
            events.add(
                MatchEvent.GameEnd(
                    gameNumber = gameNumber,
                    winnerSide = if (homeWonGame) Side.HOME else Side.AWAY,
                    durationMinutes = duration,
                    finalKills = finalKills
                )
            )
            if (homeWonGame) homeMaps++ else awayMaps++
            gameNumber++
        }

        events.add(
            MatchEvent.SeriesEnd(
                winnerSide = if (homeMaps > awayMaps) Side.HOME else Side.AWAY,
                mapScore = homeMaps to awayMaps
            )
        )
        return SeriesResult(events, homeMaps, awayMaps)
    }

    /** Retorna 5 titulares; se houver menos, complementa com reservas pelo overall. */
    private fun startersOrTopFive(roster: List<Player>): List<Player> {
        val starters = roster.filter { it.titular }
        return if (starters.size >= 5) starters.sortedBy { roleOrder(it.role) }.take(5)
        else (starters + roster.filter { !it.titular }.sortedByDescending { it.overallRating() })
            .take(5).sortedBy { roleOrder(it.role) }
    }

    private fun teamStrength(roster: List<Player>): Int {
        if (roster.isEmpty()) return 50
        return roster.sumOf { it.overallRating() } / roster.size
    }

    private fun roleOrder(role: String) = when (role) {
        "TOP" -> 1; "JNG" -> 2; "MID" -> 3; "ADC" -> 4; "SUP" -> 5; else -> 6
    }

    /**
     * Gera todos os eventos de um único mapa.
     * Retorna: (eventos, lado vencedor, placar de kills final, duração em min)
     */
    private fun generateGame(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        homeStr: Int,
        awayStr: Int,
        plan: PickBanPlan? = null
    ): GameOutcome {
        val events = mutableListOf<MatchEvent>()

        events.add(MatchEvent.GameStart(gameNumber))
        events.add(MatchEvent.PhaseAnnouncement("Mapa $gameNumber · Pick & Ban"))
        events.addAll(generatePickBan(gameNumber, homeRoster, awayRoster, plan))

        events.add(MatchEvent.PhaseAnnouncement("Início do mapa $gameNumber"))

        // Decide o vencedor com peso da força
        val diff = homeStr - awayStr
        val homeWinProb = (0.5 + (diff / 60.0)).coerceIn(0.1, 0.9)
        val homeWonGame = Random.nextDouble() < homeWinProb

        // Duração: jogos mais desequilibrados terminam antes
        val duration = if (diff.absoluteValue > 12) (24..30).random() else (28..40).random()

        // Total de kills
        val totalKills = (12..28).random()
        val homeShare = if (homeWonGame) 0.55 + Random.nextDouble(0.1) else 0.30 + Random.nextDouble(0.1)
        val homeKills = (totalKills * homeShare).toInt()
        val awayKills = totalKills - homeKills

        val timed = mutableListOf<TimedEvent>()

        // Distribui kills no tempo
        repeat(homeKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.HOME,
                roster = homeRoster,
                victimRoster = awayRoster
            )
        }
        repeat(awayKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.AWAY,
                roster = awayRoster,
                victimRoster = homeRoster
            )
        }

        // Heralds (8-10 e às vezes 13-15)
        timed += herald(time = (7..10).random(), side = sideByOdds(homeWinProb))
        if (Random.nextDouble() < 0.55 && duration > 15)
            timed += herald(time = (13..15).random(), side = sideByOdds(homeWinProb))

        // Drakes a cada ~5 min começando em 5
        var dragonTime = (5..7).random()
        val dragonTypes = listOf("Infernal", "Cloud", "Mountain", "Ocean", "Hextech", "Chemtech")
            .shuffled()
        var dragonIndex = 0
        while (dragonTime < duration - 1 && dragonIndex < 5) {
            timed += dragon(
                time = dragonTime,
                side = sideByOdds(homeWinProb),
                type = dragonTypes[dragonIndex % dragonTypes.size]
            )
            dragonIndex++
            dragonTime += (4..6).random()
        }

        // Torres (6-9 total no mapa)
        val totalTowers = (5..9).random()
        val homeTowers = (totalTowers * (if (homeWonGame) 0.65 else 0.35)).toInt()
        val awayTowers = totalTowers - homeTowers
        val towerLanes = listOf("top", "mid", "bot")
        repeat(homeTowers) {
            timed += tower(
                time = (8..(duration - 2)).random(),
                side = Side.HOME,
                location = towerLanes.random()
            )
        }
        repeat(awayTowers) {
            timed += tower(
                time = (8..(duration - 2)).random(),
                side = Side.AWAY,
                location = towerLanes.random()
            )
        }

        // Baron (após 22)
        if (duration > 24) {
            timed += baron(time = (22..(duration - 2)).random(), side = sideByOdds(homeWinProb))
            if (Random.nextDouble() < 0.35 && duration > 30)
                timed += baron(time = (28..(duration - 1)).random(), side = sideByOdds(homeWinProb))
        }

        // Buffs (alguns ao longo do jogo, bem espaçados)
        repeat(3) {
            val team = if (Random.nextBoolean()) Side.HOME else Side.AWAY
            val rosterSide = if (team == Side.HOME) homeRoster else awayRoster
            val jng = rosterSide.find { it.role == "JNG" } ?: rosterSide.first()
            timed += buff(
                time = (4..(duration - 4)).random(),
                side = team,
                player = jng.nome_jogo,
                type = listOf("Red", "Blue").random()
            )
        }

        // Inibidor (perto do fim, do lado vencedor)
        if (duration > 25) {
            timed += inhibitor(
                time = duration - (1..3).random(),
                side = if (homeWonGame) Side.HOME else Side.AWAY,
                location = listOf("top", "mid", "bot").random()
            )
        }

        // Ordena por tempo
        timed.sortBy { it.minute * 60 + it.second }

        // Adiciona ticks de tempo (a cada minuto cheio) intercalados — mas mantemos os
        // eventos disparando em seus minutos. O UI atualiza o relógio quando recebe um
        // GameTick ou ao processar qualquer evento (via .time).
        for (m in 0..duration) {
            events.add(MatchEvent.GameTick(minute = m, second = 0))
            events.addAll(timed.filter { it.minute == m }.map { it.event })
        }

        return GameOutcome(events, homeWonGame, homeKills to awayKills, duration)
    }

    /** Pick & Ban: 5 bans por lado intercalados, depois 5 picks por lado intercalados (snake). */
    internal fun generatePickBan(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        plan: PickBanPlan? = null
    ): List<MatchEvent> {
        val out = mutableListOf<MatchEvent>()
        val used = mutableSetOf<String>()

        // Bans: usa os do plano se disponíveis (blueBans = home, redBans = away), senão random
        fun banFromPlanOrRandom(side: Side, index: Int): MatchEvent.Ban {
            val candidate = when (side) {
                Side.HOME -> plan?.blueBans?.getOrNull(index)
                Side.AWAY -> plan?.redBans?.getOrNull(index)
            }
            if (candidate != null && candidate !in used) {
                used += candidate
                return MatchEvent.Ban(gameNumber, side, candidate)
            }
            val pool = (Champions.MID + Champions.JNG + Champions.ADC + Champions.TOP)
                .filter { it !in used }
            val champ = pool.randomOrNull() ?: Champions.ALL_FLAT.first { it !in used }
            used += champ
            return MatchEvent.Ban(gameNumber, side, champ)
        }

        // 5 bans alternados
        repeat(5) { idx ->
            out += banFromPlanOrRandom(Side.HOME, idx)
            out += banFromPlanOrRandom(Side.AWAY, idx)
        }

        // Picks: ordem snake CBLOL: B1 R1 R2 B2 B3 R3 R4 B4 B5 R5
        val pickOrder = listOf(
            Side.HOME, Side.AWAY, Side.AWAY, Side.HOME, Side.HOME,
            Side.AWAY, Side.AWAY, Side.HOME, Side.HOME, Side.AWAY
        )
        val homePicked = mutableListOf<Player>()
        val awayPicked = mutableListOf<Player>()

        pickOrder.forEachIndexed { _, side ->
            val pickedList = if (side == Side.HOME) homePicked else awayPicked
            val rosterSide = if (side == Side.HOME) homeRoster else awayRoster
            val nextPlayer = rosterSide.firstOrNull { p -> pickedList.none { it.id == p.id } }
                ?: return@forEachIndexed

            // Usa pick do plano se disponível (bluePicks = home, redPicks = away)
            val planPick = when (side) {
                Side.HOME -> plan?.bluePicks?.getOrNull(pickedList.size)
                Side.AWAY -> plan?.redPicks?.getOrNull(pickedList.size)
            }
            val champ = if (planPick != null && planPick !in used) {
                used += planPick
                planPick
            } else {
                val pool = Champions.forRole(nextPlayer.role).filter { it !in used }
                val c = pool.randomOrNull() ?: Champions.ALL_FLAT.first { it !in used }
                used += c
                c
            }
            pickedList += nextPlayer
            out += MatchEvent.Pick(
                gameNumber = gameNumber,
                side = side,
                playerName = nextPlayer.nome_jogo,
                role = nextPlayer.role,
                champion = champ
            )
        }
        return out
    }

    private fun sideByOdds(homeProb: Double): Side =
        if (Random.nextDouble() < homeProb) Side.HOME else Side.AWAY

    // ───── helpers de timed events ─────

    private data class TimedEvent(val minute: Int, val second: Int, val event: MatchEvent)

    private fun fmt(t: Int): String = "%02d:%02d".format(t, Random.nextInt(0, 60))

    private fun kill(
        t: Int, killerSide: Side, roster: List<Player>, victimRoster: List<Player>
    ): TimedEvent {
        val killer = roster.random()
        val victim = victimRoster.random()
        return TimedEvent(t, 0, MatchEvent.Kill(
            time = fmt(t),
            killerSide = killerSide,
            killerName = killer.nome_jogo,
            killerChamp = pickChampForRole(killer.role),
            victimName = victim.nome_jogo,
            victimChamp = pickChampForRole(victim.role)
        ))
    }

    private fun tower(time: Int, side: Side, location: String) =
        TimedEvent(time, 0, MatchEvent.TowerDown(fmt(time), side, location))

    private fun inhibitor(time: Int, side: Side, location: String) =
        TimedEvent(time, 0, MatchEvent.Inhibitor(fmt(time), side, location))

    private fun dragon(time: Int, side: Side, type: String) =
        TimedEvent(time, 0, MatchEvent.Dragon(fmt(time), side, type))

    private fun baron(time: Int, side: Side) =
        TimedEvent(time, 0, MatchEvent.Baron(fmt(time), side))

    private fun herald(time: Int, side: Side) =
        TimedEvent(time, 0, MatchEvent.Herald(fmt(time), side))

    private fun buff(time: Int, side: Side, player: String, type: String) =
        TimedEvent(time, 0, MatchEvent.Buff(fmt(time), side, player, type))

    private fun pickChampForRole(role: String): String = Champions.forRole(role).random()

    // ───── tipos auxiliares ─────

    private data class GameOutcome(
        val events: List<MatchEvent>,
        val homeWon: Boolean,
        val finalKills: Pair<Int, Int>,
        val duration: Int
    )

    data class SeriesResult(
        val events: List<MatchEvent>,
        val homeMaps: Int,
        val awayMaps: Int
    )
}
