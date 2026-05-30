package com.cblol.scout.game.live

import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Player
import com.cblol.scout.data.RoleAssignment
import com.cblol.scout.data.Side
import com.cblol.scout.game.Champions
import com.cblol.scout.util.ChampionRepository

/**
 * Gera a fase de pick & ban de um mapa: 5 bans alternados por lado, depois
 * 5 picks por lado seguindo o padrão CBLOL/LCS.
 *
 * Aceita um plano humano (picks/bans escolhidos pelo jogador na UI) ou
 * gera tudo via IA (campeões dos mains, com fallback por role). Devolve os
 * eventos para o feed e o **mapeamento `playerName → campeão pickado`**,
 * usado pelo [TimedEventGenerator] para que kills/mortes mostrem os
 * campeões corretos do draft.
 *
 * **Picks/bans/assignments chegam JÁ NORMALIZADOS** por lado HOME/AWAY (via
 * [SideNormalizer]) — esta classe não conhece o eixo azul/vermelho.
 *
 * Extraído do [com.cblol.scout.game.LiveMatchEngine] para isolar a regra
 * complexa de casar campeão↔jogador (por assignment explícito, por role
 * natural, ou por main) num único lugar.
 *
 * **SOLID:**
 *  - **SRP**: gera só a fase de pick & ban.
 *  - **OCP**: novas estratégias de pick automático (ex: counter-pick por
 *    histórico) entram aqui sem mexer no resto do motor.
 */
internal object PickBanGenerator {

    /**
     * Gera os eventos de bans + picks e o mapa jogador→campeão para uso
     * nos eventos do jogo.
     */
    fun generate(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        homePicks: List<String>,
        awayPicks: List<String>,
        homeBans: List<String>,
        awayBans: List<String>,
        homeAssignments: List<RoleAssignment>,
        awayAssignments: List<RoleAssignment>
    ): PickBanResult {
        val out  = mutableListOf<MatchEvent>()
        val used = mutableSetOf<String>()
        val playerChampions = mutableMapOf<String, String>()

        // 5 bans alternados (HOME ban 1, AWAY ban 1, HOME ban 2, AWAY ban 2, ...)
        repeat(5) { idx ->
            out += banFromPlanOrRandom(gameNumber, Side.HOME, idx, homeBans, used)
            out += banFromPlanOrRandom(gameNumber, Side.AWAY, idx, awayBans, used)
        }

        // Picks do lado HOME
        processSide(gameNumber, Side.HOME, homePicks, homeRoster, homeAssignments, used, playerChampions, out)
        // Picks do lado AWAY
        processSide(gameNumber, Side.AWAY, awayPicks, awayRoster, awayAssignments, used, playerChampions, out)

        return PickBanResult(out, playerChampions)
    }

    // ── Helpers privados ─────────────────────────────────────────────────

    /**
     * Usa o ban do plano se disponível e livre; senão sorteia um do pool por
     * role evitando colisão com picks/bans já usados.
     */
    private fun banFromPlanOrRandom(
        gameNumber: Int,
        side: Side,
        index: Int,
        planBans: List<String>,
        used: MutableSet<String>
    ): MatchEvent.Ban {
        val candidate = planBans.getOrNull(index)
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

    /**
     * Processa todos os picks de UM lado.
     *  - Com `picks` do plano: casa cada pick com o assignment correspondente
     *    (se houver) ou cai no fallback por role natural do campeão.
     *  - Sem plano: IA escolhe um champion do main de cada jogador, com
     *    fallback por role e depois por qualquer campeão livre.
     */
    private fun processSide(
        gameNumber: Int,
        side: Side,
        picks: List<String>,
        roster: List<Player>,
        assignments: List<RoleAssignment>,
        used: MutableSet<String>,
        playerChampions: MutableMap<String, String>,
        out: MutableList<MatchEvent>
    ) {
        if (picks.isNotEmpty()) {
            processPicksFromPlan(gameNumber, side, picks, roster, assignments, used, playerChampions, out)
        } else {
            processPicksFromAi(gameNumber, side, roster, used, playerChampions, out)
        }
    }

    /** Picks vindos do plano humano: casa por assignment ou por role natural. */
    private fun processPicksFromPlan(
        gameNumber: Int,
        side: Side,
        picks: List<String>,
        roster: List<Player>,
        assignments: List<RoleAssignment>,
        used: MutableSet<String>,
        playerChampions: MutableMap<String, String>,
        out: MutableList<MatchEvent>
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
                // Sem assignment: casa o pick com o titular da role natural do
                // campeão. Se essa role já foi consumida, usa o próximo livre.
                val champion = ChampionRepository.getById(championId)
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

    /** Picks gerados pela IA: campeão do main de cada jogador, com fallbacks. */
    private fun processPicksFromAi(
        gameNumber: Int,
        side: Side,
        roster: List<Player>,
        used: MutableSet<String>,
        playerChampions: MutableMap<String, String>,
        out: MutableList<MatchEvent>
    ) {
        roster.forEach { player ->
            val mainsAvailable = player.championPool.filter { it !in used }
            val champ = mainsAvailable.randomOrNull()
                ?: Champions.forRole(player.role).filter { it !in used }.randomOrNull()
                ?: Champions.ALL_FLAT.first { it !in used }
            used += champ
            playerChampions[player.nome_jogo] = champ
            out += MatchEvent.Pick(
                gameNumber = gameNumber, side = side,
                playerName = player.nome_jogo, role = player.role, champion = champ
            )
        }
    }

    /**
     * Resultado da fase de pick & ban.
     *
     * @property events lista de bans + picks para o feed da simulação
     * @property playerChampions `playerName → championId` — usado pelo
     *   [TimedEventGenerator] para sortear kills mostrando os campeões
     *   corretos do draft, em vez de campeões aleatórios por role
     */
    data class PickBanResult(
        val events: List<MatchEvent>,
        val playerChampions: Map<String, String>
    )
}
