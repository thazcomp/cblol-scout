package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import java.time.LocalDate

/**
 * Lógica de progressão do jogo: avançar dia, simular partidas pendentes, aplicar
 * receitas/despesas, atualizar contratos, etc.
 *
 * Regras econômicas:
 *  - Patrocínio semanal: pago todo domingo (R$ definido em GameState.sponsorshipPerWeek)
 *  - Salários: pagos no dia 1 de cada mês (somatório dos salários do elenco titular + reserva)
 *  - Premiação: R$ 50.000 por mapa vencido + R$ 100.000 por série vencida
 */
object GameEngine {

    const val PRIZE_PER_MAP_WIN = 50_000L
    const val PRIZE_PER_SERIES_WIN = 100_000L

    /** Avança a data corrente em N dias, processando todos os eventos. */
    fun advanceDays(context: Context, days: Int): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        var date = LocalDate.parse(gs.currentDate)

        repeat(days) {
            date = date.plusDays(1)
            val iso = date.toString()
            gs.currentDate = iso

            // 1. Pagamento de patrocínio (domingo = day_of_week 7)
            if (date.dayOfWeek.value == 7) {
                gs.budget += gs.sponsorshipPerWeek
                report.income += gs.sponsorshipPerWeek
                GameRepository.log("ECONOMY",
                    "Patrocínio semanal recebido: R$ ${"%,d".format(gs.sponsorshipPerWeek)}")
            }

            // 2. Pagamento de salários (dia 1 de cada mês)
            if (date.dayOfMonth == 1) {
                val total = totalMonthlyPayroll(context)
                gs.budget -= total
                report.expense += total
                GameRepository.log("ECONOMY",
                    "Folha salarial paga: R$ ${"%,d".format(total)}")
            }

            // 3. Simula partidas do dia
            val todayMatches = gs.matches.filter { it.date == iso && !it.played }
            todayMatches.forEach { m ->
                MatchSimulator.simulate(context, m)
                report.matchesPlayed += 1

                val winnerId = m.winnerTeamId()
                val isMyMatch = m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId

                if (isMyMatch && winnerId == gs.managerTeamId) {
                    val prize = PRIZE_PER_SERIES_WIN +
                        (PRIZE_PER_MAP_WIN * maxOf(m.homeScore, m.awayScore))
                    gs.budget += prize
                    report.income += prize
                    report.myWin = true
                } else if (isMyMatch) {
                    val mapPrize = PRIZE_PER_MAP_WIN *
                        (if (m.homeTeamId == gs.managerTeamId) m.homeScore else m.awayScore)
                    gs.budget += mapPrize
                    report.income += mapPrize
                    report.myLoss = true
                }

                val homeName = teamName(context, m.homeTeamId)
                val awayName = teamName(context, m.awayTeamId)
                GameRepository.log(
                    "MATCH",
                    "Rodada ${m.round}: $homeName ${m.homeScore}-${m.awayScore} $awayName"
                )
            }
        }

        GameRepository.save(context)
        return report
    }

    /** Próxima partida do meu time (a partir da data atual, inclusive). */
    fun nextMatchForManager(): com.cblol.scout.data.Match? {
        val gs = GameRepository.current()
        return gs.matches
            .filter { !it.played }
            .filter { it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId }
            .filter { it.date >= gs.currentDate }
            .minByOrNull { it.date }
    }

    fun totalMonthlyPayroll(context: Context): Long {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        return roster.sumOf { it.contrato.salario_mensal_estimado_brl ?: 0 }
    }

    private fun teamName(context: Context, teamId: String): String =
        GameRepository.snapshot(context).times.find { it.id == teamId }?.nome ?: teamId

    /** Cria uma carreira nova: gera estado inicial + calendário. */
    fun startNewCareer(
        context: Context,
        managerName: String,
        teamId: String
    ): GameState {
        val snap = GameRepository.snapshot(context)
        val team = snap.times.find { it.id == teamId } ?: error("Time não encontrado: $teamId")

        val (budget, sponsorship) = budgetForTier(team.tier_orcamento)
        val splitStart = "2026-03-28"
        val splitEnd   = "2026-06-06"

        val gs = GameState(
            managerName = managerName,
            managerTeamId = teamId,
            splitStartDate = splitStart,
            splitEndDate = splitEnd,
            currentDate = LocalDate.parse(splitStart).minusDays(7).toString(), // 1 sem antes do split
            budget = budget,
            sponsorshipPerWeek = sponsorship
        )
        gs.matches.addAll(ScheduleGenerator.generate(snap.times.map { it.id }, splitStart))
        gs.gameLog.add(
            com.cblol.scout.data.LogEntry(
                gs.currentDate, "CAREER",
                "Pré-temporada iniciada. Você é o novo técnico do ${team.nome}!"
            )
        )
        GameRepository.save(context, gs)
        return gs
    }

    /** Devolve (orçamento_inicial, patrocínio_semanal) por tier do time. */
    private fun budgetForTier(tier: String): Pair<Long, Long> = when (tier) {
        "S" -> 5_000_000L to 600_000L
        "A" -> 3_000_000L to 350_000L
        else -> 1_500_000L to 200_000L
    }

    fun recordMatchResult(context: Context, matchId: String, mapNumber: Int, winnerTeamId: String) {
        val gs = GameRepository.current()
        val match = gs.matches.find { it.id == matchId } ?: error("Partida não encontrada: $matchId")

        if (mapNumber == 1) {
            if (winnerTeamId == match.homeTeamId) match.homeScore = 1 else match.awayScore = 1
        } else if (mapNumber == 2) {
            if (winnerTeamId == match.homeTeamId) match.homeScore = 2 else match.awayScore = 2
        } else {
            error("Número de mapa inválido: $mapNumber")
        }

        val series = gs.seriesState[matchId]
        if (series != null && series.isFinished) {
            match.played = true
            gs.seriesState.remove(matchId)
            GameRepository.save(context)
        }
    }
}

/** Relatório consolidado do `advanceDays`. */
data class AdvanceReport(
    var matchesPlayed: Int = 0,
    var myWin: Boolean = false,
    var myLoss: Boolean = false,
    var income: Long = 0,
    var expense: Long = 0
)
