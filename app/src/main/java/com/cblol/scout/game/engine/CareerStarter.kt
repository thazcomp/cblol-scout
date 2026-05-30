package com.cblol.scout.game.engine

import android.content.Context
import com.cblol.scout.data.Division
import com.cblol.scout.data.GameState
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.Player
import com.cblol.scout.data.Team
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.AcademyService
import com.cblol.scout.domain.usecase.PlayerBondService
import com.cblol.scout.domain.usecase.TransferWindowService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.ScheduleGenerator
import com.cblol.scout.util.SecondDivisionTeamsGenerator

/**
 * Constrói uma carreira nova a partir dos parâmetros do jogador (nome do
 * técnico, time escolhido, divisão).
 *
 * Extraído do [com.cblol.scout.game.GameEngine] para isolar uma operação de
 * inicialização que envolve várias decisões cruzadas (divisão → economia →
 * adversários → roster), mantendo o motor focado em rodar o tempo.
 */
internal object CareerStarter {

    private const val DEFAULT_SPLIT_START = "2026-03-28"
    private const val DEFAULT_SPLIT_END   = "2026-06-06"

    /**
     * Cria uma carreira nova: gera estado inicial + calendário + janelas de
     * transferência + laços iniciais + categoria de base.
     *
     * Ver KDoc de [com.cblol.scout.game.GameEngine.startNewCareer] para a
     * semântica completa de cada parâmetro.
     */
    fun start(
        context: Context,
        managerName: String,
        teamId: String,
        division: Division = Division.FIRST,
        seed: Long = System.currentTimeMillis()
    ): GameState {
        val splitStart = DEFAULT_SPLIT_START
        val splitEnd   = DEFAULT_SPLIT_END
        val gameStart  = TransferWindowService.gameStartFor(splitStart)

        // Resolve time + economia + lista de adversários conforme a divisão.
        val setup = resolveDivisionSetup(context, division, teamId, seed)

        val gs = GameState(
            managerName = managerName,
            managerTeamId = teamId,
            splitStartDate = splitStart,
            splitEndDate = splitEnd,
            currentDate = gameStart,
            budget = setup.budget,
            sponsorshipPerWeek = setup.sponsorship
        )
        gs.division = division
        if (division == Division.SECOND) {
            gs.secondDivisionTeams = setup.teams.toMutableList()
            gs.secondDivisionPlayers = setup.players.toMutableList()
        }
        gs.matches.addAll(ScheduleGenerator.generate(setup.teamIds, splitStart))
        gs.transferWindows.addAll(
            TransferWindowService.buildWindowsForSplit(gameStart, splitStart)
        )

        // **Importante**: o GameRepository precisa do GameState salvo antes
        // de chamar rosterOf, porque o roster da 2ª divisão vive em
        // gs.secondDivisionPlayers. Sem isso, ensureBondsFor pegaria vazio.
        GameRepository.save(context, gs)

        val initialRoster = GameRepository.rosterOf(context, teamId)
        PlayerBondService.ensureBondsFor(gs, initialRoster)
        AcademyService.initializeForNewCareer(gs)

        val divLabel = if (division == Division.SECOND) " (Circuito Desafiante)" else ""
        gs.gameLog.add(
            LogEntry(
                gs.currentDate, "CAREER",
                "Pré-temporada iniciada$divLabel. Você é o novo técnico do ${setup.teamName}! Mercado de transferências aberto."
            )
        )
        GameRepository.save(context, gs)
        return gs
    }

    /**
     * Resultado da resolução dos parâmetros iniciais da carreira conforme a
     * divisão escolhida. Empacota tudo o que [start] precisa para popular o
     * [GameState] de forma uniforme.
     */
    private data class DivisionSetup(
        val budget: Long,
        val sponsorship: Long,
        val teamName: String,
        val teamIds: List<String>,
        val teams: List<Team>,
        val players: List<Player>
    )

    private fun resolveDivisionSetup(
        context: Context,
        division: Division,
        teamId: String,
        seed: Long
    ): DivisionSetup = when (division) {
        Division.FIRST -> {
            val snap = GameRepository.snapshot(context)
            val team = snap.times.find { it.id == teamId }
                ?: error("Time não encontrado na 1ª divisão: $teamId")
            val (budget, sponsorship) = budgetForTier(team.tier_orcamento)
            DivisionSetup(
                budget = budget,
                sponsorship = sponsorship,
                teamName = team.nome,
                teamIds = snap.times.map { it.id },
                teams = emptyList(),
                players = emptyList()
            )
        }
        Division.SECOND -> {
            val gen = SecondDivisionTeamsGenerator.generate(seed)
            val team = gen.teams.find { it.id == teamId }
                ?: error("Time não encontrado na 2ª divisão: $teamId (seed=$seed)")
            DivisionSetup(
                budget = GameConstants.Economy.STARTING_BUDGET_SECOND_DIV,
                sponsorship = GameConstants.Economy.WEEKLY_SPONSOR_SECOND_DIV,
                teamName = team.nome,
                teamIds = gen.teams.map { it.id },
                teams = gen.teams,
                players = gen.players
            )
        }
    }

    /** Devolve (orçamento_inicial, patrocínio_semanal) por tier do time (1ª divisão). */
    private fun budgetForTier(tier: String): Pair<Long, Long> = when (tier) {
        "S" -> GameConstants.Economy.STARTING_BUDGET_TIER_S to GameConstants.Economy.WEEKLY_SPONSOR_TIER_S
        "A" -> GameConstants.Economy.STARTING_BUDGET_TIER_A to GameConstants.Economy.WEEKLY_SPONSOR_TIER_A
        else -> GameConstants.Economy.STARTING_BUDGET_TIER_B to GameConstants.Economy.WEEKLY_SPONSOR_TIER_B
    }
}
