package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Player
import com.cblol.scout.data.Side
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.util.ChampionPoolRepository
import com.cblol.scout.util.CompositionRepository
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
        val plan       = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null

        // ══ NORMALIZAÇÃO DE LADO ══
        //
        // O `PickBanPlan` armazena os picks em `bluePicks`/`redPicks` (lados azul
        // e vermelho da UI), enquanto o `match` usa `homeTeamId`/`awayTeamId`
        // (calendário). Não são sinônimos: o time do jogador alterna de lado a cada
        // mapa (azul nos mapas ímpares, vermelho nos pares).
        //
        // Aqui descobrimos qual lado (azul/vermelho) cada time (home/away) jogou
        // neste mapa, e então reordenamos os picks para que `homePicks` realmente
        // corresponda ao `homeRoster`.
        val gs = GameRepository.current()
        val playerTeamId = gs.managerTeamId
        val playerIsHome = (match.homeTeamId == playerTeamId)
        // Jogador foi azul neste mapa? Mapa 1 ímpar = sim; mapa 2 par = não.
        val playerWasBlue = (gameNumber % 2 == 1)
        // Logo, o HOME foi azul se (jogador é home AND jogador foi azul) ou
        // (jogador é away AND jogador foi vermelho).
        val homeWasBlue   = (playerIsHome == playerWasBlue)

        val homePicks  = if (homeWasBlue) plan?.bluePicks.orEmpty() else plan?.redPicks.orEmpty()
        val awayPicks  = if (homeWasBlue) plan?.redPicks.orEmpty()  else plan?.bluePicks.orEmpty()
        val homeBans   = if (homeWasBlue) plan?.blueBans.orEmpty()  else plan?.redBans.orEmpty()
        val awayBans   = if (homeWasBlue) plan?.redBans.orEmpty()   else plan?.blueBans.orEmpty()
        val allBans    = homeBans + awayBans

        // Os roleAssignments referem-se SEMPRE ao time do jogador (independente
        // do lado/calendário). Se o jogador é HOME, os assignments se aplicam ao
        // homeRoster; se é AWAY, se aplicam ao awayRoster.
        val playerSideAssignments = plan?.roleAssignments.orEmpty()
        val homeAssignments = if (playerIsHome) playerSideAssignments else emptyList()
        val awayAssignments = if (!playerIsHome) playerSideAssignments else emptyList()

        val homeComp   = CompositionRepository.analyzeWithTags(homePicks, awayPicks, awayBans)
        val awayComp   = CompositionRepository.analyzeWithTags(awayPicks, homePicks, homeBans)

        // Bônus de "champion pool": cada jogador que picka um dos seus mains
        // dá +CHAMP_POOL_MAIN_BONUS à força do time. Reflete a vantagem real de
        // jogar em campeões nos quais o atleta tem mais experiência.
        val homeMainsCount = ChampionPoolRepository.countMainsPicked(homeRoster, homePicks)
        val awayMainsCount = ChampionPoolRepository.countMainsPicked(awayRoster, awayPicks)
        val homeMainBonus  = homeMainsCount * GameConstants.Player.CHAMP_POOL_MAIN_BONUS
        val awayMainBonus  = awayMainsCount * GameConstants.Player.CHAMP_POOL_MAIN_BONUS

        // Penalidade por jogadores em rota errada (de [PickBanPlan.roleAssignments]).
        // Aplicada apenas ao lado do jogador (HOME se ele é home, AWAY se é away).
        val wrongRolePenalty = (playerSideAssignments.count { it.isWrongRole }) *
                               GameConstants.Player.WRONG_ROLE_PENALTY

        val homeStr = teamStrengthWithMood(homeRoster, gs) + GameConstants.Series.HOME_SIDE_BONUS +
                      homeComp.totalBonus + homeMainBonus -
                      (if (playerIsHome) wrongRolePenalty else 0)
        val awayStr = teamStrengthWithMood(awayRoster, gs) +
                      awayComp.totalBonus + awayMainBonus -
                      (if (!playerIsHome) wrongRolePenalty else 0)

        val (gameEvents, homeWon, finalKills, duration) =
            generateGame(
                gameNumber, homeRoster, awayRoster,
                homeStr, awayStr,
                homePicks, awayPicks, homeBans, redBans = awayBans,
                homeAssignments = homeAssignments,
                awayAssignments = awayAssignments
            )

        val events = gameEvents.toMutableList()

        // Anuncia jogadores em êxtase (moral 95+) com bônus extra de overall.
        // O `add(0, ...)` empurra o anuncio para o topo da timeline para o jogador
        // ver antes de ver os picks/bans.
        val ecstaticPlayers = (homeRoster + awayRoster).filter { p ->
            (MoraleService.moodOf(gs, p.id) >= MoraleService.ECSTASY_THRESHOLD)
        }
        ecstaticPlayers.forEach { player ->
            val isHome = player in homeRoster
            val sideLabel = if (isHome) "HOME" else "AWAY"
            events.add(0, MatchEvent.PhaseAnnouncement(
                "⚡ [$sideLabel] ${player.nome_jogo} está em êxtase! (+5 overall)"
            ))
        }

        // Anuncia sinergia e insights no feed
        if (homeComp.base.detected != null || homeComp.insights.isNotEmpty()) {
            val label = buildString {
                if (homeComp.base.detected != null) append("⚡ [HOME] ${homeComp.base.description}")
                if (homeComp.insights.isNotEmpty()) append(" | ${homeComp.insights.first()}")
            }
            events.add(0, MatchEvent.PhaseAnnouncement(label))
        }
        if (awayComp.base.detected != null || awayComp.insights.isNotEmpty()) {
            val label = buildString {
                if (awayComp.base.detected != null) append("⚡ [AWAY] ${awayComp.base.description}")
                if (awayComp.insights.isNotEmpty()) append(" | ${awayComp.insights.first()}")
            }
            events.add(0, MatchEvent.PhaseAnnouncement(label))
        }

        // Anuncia jogadores no main
        if (homeMainsCount > 0) {
            events.add(0, MatchEvent.PhaseAnnouncement(
                "🎯 [HOME] $homeMainsCount jogador(es) no main (+$homeMainBonus força)"
            ))
        }
        if (awayMainsCount > 0) {
            events.add(0, MatchEvent.PhaseAnnouncement(
                "🎯 [AWAY] $awayMainsCount jogador(es) no main (+$awayMainBonus força)"
            ))
        }

        // Anuncia jogadores em rota errada (lado correto do jogador)
        val wrongRoleCount = playerSideAssignments.count { it.isWrongRole }
        if (wrongRoleCount > 0) {
            val sideLabel = if (playerIsHome) "HOME" else "AWAY"
            events.add(0, MatchEvent.PhaseAnnouncement(
                "⚠️ [$sideLabel] $wrongRoleCount jogador(es) em rota errada (−$wrongRolePenalty força)"
            ))
        }

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
        while (homeMaps < GameConstants.Series.MAPS_TO_WIN && awayMaps < GameConstants.Series.MAPS_TO_WIN) {
            val homeStr = teamStrength(homeRoster) + GameConstants.Series.HOME_SIDE_BONUS
            val awayStr = teamStrength(awayRoster)
            // O plano salvo no match refere-se ao último mapa com pick & ban feito pelo jogador.
            // Para os demais mapas o motor gera picks automaticamente.
            //
            // Este caminho é usado em simulações automáticas (jogos sem o time do jogador),
            // então não precisamos da normalização sofisticada de lado azul/vermelho:
            // tratamos `bluePicks` como `homePicks` por simplicidade.
            val plan = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
            val (gameEvents, homeWonGame, finalKills, duration) =
                generateGame(
                    gameNumber, homeRoster, awayRoster, homeStr, awayStr,
                    homePicks       = plan?.bluePicks.orEmpty(),
                    awayPicks       = plan?.redPicks.orEmpty(),
                    homeBans        = plan?.blueBans.orEmpty(),
                    redBans         = plan?.redBans.orEmpty(),
                    homeAssignments = plan?.roleAssignments.orEmpty(),
                    awayAssignments = emptyList()
                )
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

    /**
     * Variante de [teamStrength] que considera a moral atual dos jogadores.
     *
     * Cada jogador tem seu overall efetivo ajustado por
     * [MoraleService.moodOverallModifier] antes de calcular a média:
     *  - Moral SAD (0-33): -3 no overall
     *  - Moral NEUTRAL (34-66): 0
     *  - Moral HAPPY (67-94): +2
     *  - Moral em ÊXTASE (95+): +5 (HAPPY +2 + bônus de êxtase +3)
     *
     * Resultado: um time com 5 jogadores tristes perde ~3 pontos de média,
     * um time com 5 em êxtase ganha ~5 pontos — diferença relevante na
     * fórmula de probabilidade (que usa diferença/60).
     */
    private fun teamStrengthWithMood(roster: List<Player>, gs: GameState): Int {
        if (roster.isEmpty()) return 50
        val total = roster.sumOf { player ->
            val baseOvr  = player.overallRating()
            val modifier = MoraleService.moodOverallModifier(gs, player.id)
            (baseOvr + modifier).coerceIn(1, 99)
        }
        return total / roster.size
    }

    private fun roleOrder(role: String) = when (role) {
        "TOP" -> 1; "JNG" -> 2; "MID" -> 3; "ADC" -> 4; "SUP" -> 5; else -> 6
    }

    /**
     * Gera todos os eventos de um único mapa.
     *
     * Recebe picks, bans e assignments JÁ NORMALIZADOS (alinhados com
     * `homeRoster`/`awayRoster` no calendário), não o `PickBanPlan` cru.
     * A normalização azul/vermelho → home/away foi feita em [generateSingleMap].
     *
     * Retorna: (eventos, lado vencedor, placar de kills final, duração em min)
     */
    private fun generateGame(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        homeStr: Int,
        awayStr: Int,
        homePicks: List<String>,
        awayPicks: List<String>,
        homeBans: List<String>,
        redBans: List<String>,
        homeAssignments: List<com.cblol.scout.data.RoleAssignment>,
        awayAssignments: List<com.cblol.scout.data.RoleAssignment>
    ): GameOutcome {
        val events = mutableListOf<MatchEvent>()

        events.add(MatchEvent.GameStart(gameNumber))
        events.add(MatchEvent.PhaseAnnouncement("Mapa $gameNumber · Pick & Ban"))

        // Gera os picks e bans e CAPTURA o mapa playerName → campeão pickado.
        // Esse mapa é usado em todos os eventos subsequentes (kills, anuncios)
        // para que o campeão de cada jogador combine com o que foi pickado.
        val pickBanResult = generatePickBanWithMap(
            gameNumber, homeRoster, awayRoster,
            homePicks, awayPicks, homeBans, redBans,
            homeAssignments, awayAssignments
        )
        events.addAll(pickBanResult.events)
        val playerChampions = pickBanResult.playerChampions

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

        // Distribui kills no tempo, usando o campeão REAL pickado por cada jogador
        repeat(homeKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.HOME,
                roster = homeRoster,
                victimRoster = awayRoster,
                playerChampions = playerChampions
            )
        }
        repeat(awayKills) {
            timed += kill(
                t = (3..(duration - 1)).random(),
                killerSide = Side.AWAY,
                roster = awayRoster,
                victimRoster = homeRoster,
                playerChampions = playerChampions
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

        // ══ TORRES ══
        //
        // No League of Legends, para destruir o Nexus o time vencedor precisa
        // derrubar pelo menos uma lane inteira: 3 torres + 2 torres do Nexus = 5
        // torres no mínimo. É impossível vencer um mapa com menos que isso.
        //
        // O perdedor normalmente derruba algumas torres ao longo do jogo
        // (especialmente em jogos longos), mas não chega na inibidora.
        //
        // Distribuição realista:
        //  - Vencedor: 5–9 torres (mínimo 5 — lane completa + Nexus)
        //  - Perdedor: 0–3 torres em jogos curtos, 1–4 em jogos longos
        val winnerTowers = (TOWER_WIN_MIN..TOWER_WIN_MAX).random()
        val loserTowers  = if (duration > 30) (1..4).random() else (0..3).random()
        val homeTowers   = if (homeWonGame) winnerTowers else loserTowers
        val awayTowers   = if (homeWonGame) loserTowers  else winnerTowers
        val towerLanes   = listOf("top", "mid", "bot")
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

    /**
     * Mantida por compatibilidade com testes/código legado.
     * Para uso interno prefira [generatePickBanWithMap] que também
     * devolve o mapa de campeões pickados por jogador.
     */
    internal fun generatePickBan(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        plan: PickBanPlan? = null
    ): List<MatchEvent> {
        // Para o caminho legado, assume mapa 1 (home = blue)
        val homeAssignments = plan?.roleAssignments.orEmpty()
        return generatePickBanWithMap(
            gameNumber, homeRoster, awayRoster,
            homePicks       = plan?.bluePicks.orEmpty(),
            awayPicks       = plan?.redPicks.orEmpty(),
            homeBans        = plan?.blueBans.orEmpty(),
            awayBans        = plan?.redBans.orEmpty(),
            homeAssignments = homeAssignments,
            awayAssignments = emptyList()
        ).events
    }

    /**
     * Resultado interno do pick & ban: eventos para o feed + mapa de
     * `playerName → campeão pickado` para uso nos eventos do jogo (kills, etc).
     */
    private data class PickBanResult(
        val events: List<MatchEvent>,
        val playerChampions: Map<String, String>
    )

    /**
     * Pick & Ban: 5 bans por lado intercalados, depois 5 picks alternados (snake).
     * Devolve também o mapa de campeão pickado por cada jogador para que eventos
     * subsequentes (kills, mortes) usem os campeões corretos do draft, e não
     * sorteios aleatórios baseados em role.
     *
     * **Picks/bans/assignments já chegam normalizados por lado HOME/AWAY**
     * (alinhados com `homeRoster`/`awayRoster`). A conversão azul→home foi
     * feita em [generateSingleMap]. Então aqui basta casar `homePicks[i]`
     * com a role primaria do campeão ou com o assignment explícito.
     */
    private fun generatePickBanWithMap(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        homePicks: List<String>,
        awayPicks: List<String>,
        homeBans: List<String>,
        awayBans: List<String>,
        homeAssignments: List<com.cblol.scout.data.RoleAssignment>,
        awayAssignments: List<com.cblol.scout.data.RoleAssignment>
    ): PickBanResult {
        val out = mutableListOf<MatchEvent>()
        val used = mutableSetOf<String>()
        // playerName → campeão pickado
        val playerChampions = mutableMapOf<String, String>()

        // Bans: usa os do plano se disponíveis, senão random
        fun banFromPlanOrRandom(side: Side, index: Int): MatchEvent.Ban {
            val candidate = when (side) {
                Side.HOME -> homeBans.getOrNull(index)
                Side.AWAY -> awayBans.getOrNull(index)
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

        // Helper unificado: gera picks de um lado, respeitando assignments quando
        // disponíveis e caindo no fallback por role primaria do campeão quando não.
        fun processPicks(
            side: Side,
            picks: List<String>,
            roster: List<Player>,
            assignments: List<com.cblol.scout.data.RoleAssignment>
        ) {
            picks.forEach { championId ->
                if (championId in used) return@forEach
                used += championId

                val assignment = assignments.firstOrNull {
                    it.championId.equals(championId, ignoreCase = true)
                }
                val playerName: String
                val playerRole: String
                if (assignment != null) {
                    playerName = assignment.playerName
                    playerRole = assignment.assignedRole
                } else {
                    // Sem assignment: casa o pick com o titular da role natural do campeão.
                    // Se essa role já foi consumida, usa o próximo livre.
                    val champion = com.cblol.scout.util.ChampionRepository.getById(championId)
                    val targetRole = champion?.primaryRole
                    val candidate = roster.firstOrNull {
                        it.role == targetRole && it.nome_jogo !in playerChampions.keys
                    } ?: roster.firstOrNull { it.nome_jogo !in playerChampions.keys }
                      ?: roster.first()
                    playerName = candidate.nome_jogo
                    playerRole = candidate.role
                }
                playerChampions[playerName] = championId
                out += MatchEvent.Pick(
                    gameNumber = gameNumber,
                    side = side,
                    playerName = playerName,
                    role = playerRole,
                    champion = championId
                )
            }
        }

        // ── PICKS DO LADO HOME ──
        if (homePicks.isNotEmpty()) {
            processPicks(Side.HOME, homePicks, homeRoster, homeAssignments)
        } else {
            // Sem plano: IA gera picks por mains/role
            homeRoster.forEach { player ->
                val mainsAvailable = player.championPool.filter { it !in used }
                val champ = mainsAvailable.randomOrNull()
                    ?: Champions.forRole(player.role).filter { it !in used }.randomOrNull()
                    ?: Champions.ALL_FLAT.first { it !in used }
                used += champ
                playerChampions[player.nome_jogo] = champ
                out += MatchEvent.Pick(
                    gameNumber = gameNumber, side = Side.HOME,
                    playerName = player.nome_jogo, role = player.role, champion = champ
                )
            }
        }

        // ── PICKS DO LADO AWAY ──
        if (awayPicks.isNotEmpty()) {
            processPicks(Side.AWAY, awayPicks, awayRoster, awayAssignments)
        } else {
            awayRoster.forEach { player ->
                val mainsAvailable = player.championPool.filter { it !in used }
                val champ = mainsAvailable.randomOrNull()
                    ?: Champions.forRole(player.role).filter { it !in used }.randomOrNull()
                    ?: Champions.ALL_FLAT.first { it !in used }
                used += champ
                playerChampions[player.nome_jogo] = champ
                out += MatchEvent.Pick(
                    gameNumber = gameNumber, side = Side.AWAY,
                    playerName = player.nome_jogo, role = player.role, champion = champ
                )
            }
        }

        return PickBanResult(out, playerChampions)
    }

    private fun sideByOdds(homeProb: Double): Side =
        if (Random.nextDouble() < homeProb) Side.HOME else Side.AWAY

    // ───── helpers de timed events ─────

    private data class TimedEvent(val minute: Int, val second: Int, val event: MatchEvent)

    private fun fmt(t: Int): String = "%02d:%02d".format(t, Random.nextInt(0, 60))

    private fun kill(
        t: Int,
        killerSide: Side,
        roster: List<Player>,
        victimRoster: List<Player>,
        playerChampions: Map<String, String>
    ): TimedEvent {
        val killer = roster.random()
        val victim = victimRoster.random()
        // Usa o campeão REAL pickado por cada jogador no draft;
        // fallback para sorteio por role apenas se o mapa não tem entrada
        // (não deveria acontecer, mas blinda contra inconsistências).
        val killerChamp = playerChampions[killer.nome_jogo] ?: pickChampForRole(killer.role)
        val victimChamp = playerChampions[victim.nome_jogo] ?: pickChampForRole(victim.role)
        return TimedEvent(t, 0, MatchEvent.Kill(
            time = fmt(t),
            killerSide = killerSide,
            killerName = killer.nome_jogo,
            killerChamp = killerChamp,
            victimName = victim.nome_jogo,
            victimChamp = victimChamp
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

    // ───── constantes ─────

    /**
     * Faixa de torres derrubadas pelo time vencedor de um mapa.
     *
     * O mínimo é 5 porque no League of Legends o caminho para o Nexus passa
     * obrigatoriamente por 3 torres de uma lane + a torre inibidora + a torre
     * do Nexus (no mínimo). Vencer com menos de 5 torres é impossível pelas
     * regras do jogo, então garantimos esse piso na simulação.
     *
     * O máximo de 11 reflete jogos onde o vencedor quase zera o mapa
     * (todas as 11 torres + as 2 do Nexus = até 11 estruturas, dependendo de
     * como o código conta).
     */
    private const val TOWER_WIN_MIN = 5
    private const val TOWER_WIN_MAX = 9

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
