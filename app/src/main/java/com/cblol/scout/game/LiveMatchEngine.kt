package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.Player
import com.cblol.scout.data.Side
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.domain.usecase.OffMatchEventService
import com.cblol.scout.game.live.GameOutcomeCalculator
import com.cblol.scout.game.live.MapStrengthCalculator
import com.cblol.scout.game.live.PickBanGenerator
import com.cblol.scout.game.live.SideNormalizer
import com.cblol.scout.game.live.TimedEventGenerator

/**
 * Motor "ao vivo" da simulação — gera a timeline completa de um mapa
 * (pick & ban + eventos do jogo) para a [com.cblol.scout.ui.MatchSimulationActivity]
 * tocar com delays.
 *
 * Esta classe é uma fachada fina sobre 5 helpers especializados em
 * `game/live/`:
 *
 *  - **[SideNormalizer]** — traduz plano azul/vermelho ↔ home/away conforme
 *    o número do mapa
 *  - **[PickBanGenerator]** — 5 bans + 5 picks por lado, com plano humano ou
 *    via IA; devolve mapa `jogador → campeão pickado`
 *  - **[MapStrengthCalculator]** — força final (overall + moral + comp + mains
 *    + bonds − rota errada)
 *  - **[GameOutcomeCalculator]** — decisão probabilística (vencedor, kills,
 *    duração)
 *  - **[TimedEventGenerator]** — distribui kills/torres/dragões/baron/herald/
 *    buffs/inibidor ao longo dos minutos
 *
 * O motor cuida da orquestração: chamar cada helper na ordem certa e juntar
 * os eventos com os anúncios de feed (sinergia, jogadores em êxtase,
 * lesionados, rota errada).
 */
object LiveMatchEngine {

    /**
     * Gera a timeline de UM mapa e devolve o resultado.
     * Chamado uma vez por mapa pela MatchSimulationActivity, que acumula o
     * placar entre os mapas até a série terminar.
     */
    fun generateSingleMap(context: Context, match: Match, gameNumber: Int): MapResult {
        val homeRoster = startersOrTopFive(GameRepository.rosterOf(context, match.homeTeamId))
        val awayRoster = startersOrTopFive(GameRepository.rosterOf(context, match.awayTeamId))
        val plan = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
        val gs = GameRepository.current()

        // ── 1) Normaliza lados azul/vermelho → home/away ──
        val sides = SideNormalizer.normalize(match, plan, gameNumber)

        // ── 2) Pick & ban (5 bans + 5 picks por lado) ──
        val pickBan = PickBanGenerator.generate(
            gameNumber,
            homeRoster, awayRoster,
            sides.homePicks, sides.awayPicks,
            sides.homeBans, sides.awayBans,
            sides.homeAssignments, sides.awayAssignments
        )

        // ── 3) Calcula força final de cada lado (com todos os modificadores) ──
        val strength = MapStrengthCalculator.calculate(sides, homeRoster, awayRoster, gs)

        // ── 4) Decide vencedor, kills e duração ──
        val outcome = GameOutcomeCalculator.calculate(strength.homeStr, strength.awayStr)

        // ── 5) Gera os eventos do jogo distribuídos no tempo ──
        val gameEvents = TimedEventGenerator.build(
            duration = outcome.duration,
            outcome = outcome,
            homeRoster = homeRoster,
            awayRoster = awayRoster,
            playerChampions = pickBan.playerChampions
        )

        // ── 6) Junta tudo: GameStart + anúncios + pick & ban + jogo + GameEnd ──
        val events = mutableListOf<MatchEvent>()
        events.add(MatchEvent.GameStart(gameNumber))
        events.add(MatchEvent.PhaseAnnouncement("Mapa $gameNumber · Pick & Ban"))
        events.addAll(pickBan.events)
        events.add(MatchEvent.PhaseAnnouncement("Início do mapa $gameNumber"))
        events.addAll(gameEvents)

        // Anúncios de modificadores ativos no feed (em ordem reversa pra ficar no topo)
        addFeedAnnouncements(events, homeRoster, awayRoster, gs, sides, strength)

        events.add(MatchEvent.GameEnd(
            gameNumber      = gameNumber,
            winnerSide      = if (outcome.homeWon) Side.HOME else Side.AWAY,
            durationMinutes = outcome.duration,
            finalKills      = outcome.homeKills to outcome.awayKills
        ))
        return MapResult(events, outcome.homeWon)
    }

    /**
     * Adiciona anúncios de pré-jogo no INÍCIO do feed (insertAt 0): jogadores
     * em êxtase, lesionados/com efeitos ativos, sinergia de composição,
     * jogadores no main, jogadores em rota errada.
     *
     * Como inserimos em ordem reversa (cada `add(0, ...)` empurra os anteriores
     * para baixo), o primeiro item a aparecer no topo do feed é o último a ser
     * adicionado aqui — por isso a ordem dentro deste método é "do menos
     * importante para o mais importante".
     */
    private fun addFeedAnnouncements(
        events: MutableList<MatchEvent>,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        gs: GameState,
        sides: SideNormalizer.NormalizedSides,
        strength: MapStrengthCalculator.StrengthBreakdown
    ) {
        // Jogadores em êxtase (moral 95+)
        (homeRoster + awayRoster)
            .filter { MoraleService.moodOf(gs, it.id) >= MoraleService.ECSTASY_THRESHOLD }
            .forEach { player ->
                val sideLabel = if (player in homeRoster) "HOME" else "AWAY"
                events.add(0, MatchEvent.PhaseAnnouncement(
                    "⚡ [$sideLabel] ${player.nome_jogo} está em êxtase! (+5 overall)"
                ))
            }

        // Efeitos ativos de eventos fora de jogo (lesão, família, breakthrough)
        (homeRoster + awayRoster).forEach { player ->
            val offMatchMod = OffMatchEventService.activeOverallModifierFor(gs, player.id)
            if (offMatchMod != 0) {
                val reason = gs.playerOverrides[player.id]?.overallModifierReason ?: "Evento"
                val sideLabel = if (player in homeRoster) "HOME" else "AWAY"
                val sign = if (offMatchMod > 0) "+" else ""
                val icon = if (offMatchMod > 0) "✨" else "🩹"
                events.add(0, MatchEvent.PhaseAnnouncement(
                    "$icon [$sideLabel] ${player.nome_jogo}: $reason ($sign$offMatchMod overall)"
                ))
            }
        }

        // Sinergia de composição + insights (HOME)
        if (strength.homeComp.base.detected != null || strength.homeComp.insights.isNotEmpty()) {
            val label = buildString {
                if (strength.homeComp.base.detected != null) append("⚡ [HOME] ${strength.homeComp.base.description}")
                if (strength.homeComp.insights.isNotEmpty()) append(" | ${strength.homeComp.insights.first()}")
            }
            events.add(0, MatchEvent.PhaseAnnouncement(label))
        }
        if (strength.awayComp.base.detected != null || strength.awayComp.insights.isNotEmpty()) {
            val label = buildString {
                if (strength.awayComp.base.detected != null) append("⚡ [AWAY] ${strength.awayComp.base.description}")
                if (strength.awayComp.insights.isNotEmpty()) append(" | ${strength.awayComp.insights.first()}")
            }
            events.add(0, MatchEvent.PhaseAnnouncement(label))
        }

        // Mains pickados
        if (strength.homeMainsCount > 0) {
            events.add(0, MatchEvent.PhaseAnnouncement(
                "🎯 [HOME] ${strength.homeMainsCount} jogador(es) no main (+${strength.homeMainBonus} força)"
            ))
        }
        if (strength.awayMainsCount > 0) {
            events.add(0, MatchEvent.PhaseAnnouncement(
                "🎯 [AWAY] ${strength.awayMainsCount} jogador(es) no main (+${strength.awayMainBonus} força)"
            ))
        }

        // Rota errada (só do lado do jogador)
        if (strength.wrongRoleCount > 0) {
            val sideLabel = if (sides.playerIsHome) "HOME" else "AWAY"
            events.add(0, MatchEvent.PhaseAnnouncement(
                "⚠️ [$sideLabel] ${strength.wrongRoleCount} jogador(es) em rota errada (−${strength.wrongRolePenalty} força)"
            ))
        }
    }

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

        while (homeMaps < GameConstants.Series.MAPS_TO_WIN && awayMaps < GameConstants.Series.MAPS_TO_WIN) {
            // Caminho simplificado para auto-simulação: força base sem composição
            // (não há plano humano), home com bônus de lado.
            val homeStr = teamStrength(homeRoster) + GameConstants.Series.HOME_SIDE_BONUS
            val awayStr = teamStrength(awayRoster)
            val outcome = GameOutcomeCalculator.calculate(homeStr, awayStr)

            val plan = if (match.pickBanPlan?.mapNumber == gameNumber) match.pickBanPlan else null
            val pickBan = PickBanGenerator.generate(
                gameNumber, homeRoster, awayRoster,
                homePicks = plan?.bluePicks.orEmpty(),
                awayPicks = plan?.redPicks.orEmpty(),
                homeBans  = plan?.blueBans.orEmpty(),
                awayBans  = plan?.redBans.orEmpty(),
                homeAssignments = plan?.roleAssignments.orEmpty(),
                awayAssignments = emptyList()
            )
            val gameEvents = TimedEventGenerator.build(
                duration = outcome.duration,
                outcome = outcome,
                homeRoster = homeRoster,
                awayRoster = awayRoster,
                playerChampions = pickBan.playerChampions
            )

            events.add(MatchEvent.GameStart(gameNumber))
            events.addAll(pickBan.events)
            events.addAll(gameEvents)
            events.add(MatchEvent.GameEnd(
                gameNumber = gameNumber,
                winnerSide = if (outcome.homeWon) Side.HOME else Side.AWAY,
                durationMinutes = outcome.duration,
                finalKills = outcome.homeKills to outcome.awayKills
            ))
            if (outcome.homeWon) homeMaps++ else awayMaps++
            gameNumber++
        }

        events.add(MatchEvent.SeriesEnd(
            winnerSide = if (homeMaps > awayMaps) Side.HOME else Side.AWAY,
            mapScore = homeMaps to awayMaps
        ))
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

    // ── Tipos públicos (compat com chamadores) ───────────────────────────

    data class MapResult(
        val events: List<MatchEvent>,
        val homeWon: Boolean
    )

    data class SeriesResult(
        val events: List<MatchEvent>,
        val homeMaps: Int,
        val awayMaps: Int
    )

    /**
     * Mantida por compatibilidade com testes/código legado. Para uso interno
     * prefira chamar diretamente o [PickBanGenerator].
     */
    internal fun generatePickBan(
        gameNumber: Int,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        plan: PickBanPlan? = null
    ): List<MatchEvent> {
        return PickBanGenerator.generate(
            gameNumber, homeRoster, awayRoster,
            homePicks       = plan?.bluePicks.orEmpty(),
            awayPicks       = plan?.redPicks.orEmpty(),
            homeBans        = plan?.blueBans.orEmpty(),
            awayBans        = plan?.redBans.orEmpty(),
            homeAssignments = plan?.roleAssignments.orEmpty(),
            awayAssignments = emptyList()
        ).events
    }
}
