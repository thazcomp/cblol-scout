package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Match
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.engine.CareerStarter
import com.cblol.scout.game.engine.DailyTicksProcessor
import com.cblol.scout.game.engine.EconomyProcessor
import com.cblol.scout.game.engine.MatchDaySimulator
import java.time.LocalDate

/**
 * Fachada do motor de progressão do jogo — avança dias, simula partidas
 * pendentes, inicia carreiras.
 *
 * Esta classe é uma fachada fina: a lógica vive em helpers especializados
 * dentro de `game/engine/`, cada um com uma responsabilidade clara:
 *
 *  - **[DailyTicksProcessor]** — moral, scouting, ofertas, laços, academia
 *  - **[EconomyProcessor]** — bloco de domingo + folha mensal + aviso de saúde
 *  - **[MatchDaySimulator]** — simulação de partidas + cobertura jornalística
 *  - **[CareerStarter]** — criação de carreira nova
 *  - **[com.cblol.scout.game.engine.TransferWindowDetector]** — abertura/fecho
 *    de janelas (chamado internamente pelo DailyTicksProcessor)
 *
 * Regras econômicas chave (constantes em [GameConstants.Economy]):
 *  - Patrocínio semanal: pago todo domingo (R$ definido em
 *    [GameState.sponsorshipPerWeek])
 *  - Salários: pagos no dia 1 de cada mês
 *  - Premiação: R$ 50.000 por mapa vencido + R$ 100.000 por série vencida
 */
object GameEngine {

    /** @deprecated Use [GameConstants.Economy.PRIZE_PER_MAP_WIN]. */
    @Deprecated("Use GameConstants.Economy.PRIZE_PER_MAP_WIN")
    const val PRIZE_PER_MAP_WIN = GameConstants.Economy.PRIZE_PER_MAP_WIN

    /** @deprecated Use [GameConstants.Economy.PRIZE_PER_SERIES_WIN]. */
    @Deprecated("Use GameConstants.Economy.PRIZE_PER_SERIES_WIN")
    const val PRIZE_PER_SERIES_WIN = GameConstants.Economy.PRIZE_PER_SERIES_WIN

    // ── API pública: avanço de tempo ─────────────────────────────────────

    /**
     * Avança a data corrente em [days] dias, processando ticks E simulando
     * **todas** as partidas (inclusive a do gerente).
     *
     * Usado em modos automáticos / testes. O fluxo normal de jogo usa
     * [advanceCalendarTo], que deixa as partidas do gerente pendentes.
     */
    fun advanceDays(context: Context, days: Int): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        var date = LocalDate.parse(gs.currentDate)

        repeat(days) {
            date = date.plusDays(1)
            val iso = date.toString()
            processDailyTicks(context, gs, date, report)
            MatchDaySimulator.simulateAllOn(context, gs, iso, report)
        }

        GameRepository.save(context)
        return report
    }

    /**
     * Avança o calendário até [targetDateIso] (inclusive), processando os
     * ticks diários de CADA dia intermediário E simulando as partidas **dos
     * OUTROS times** que aconteceriam nesses dias.
     *
     * **Partidas do gerente NÃO são auto-simuladas aqui** — ele precisa
     * jogá-las manualmente. A Activity que chama esta função garante que
     * avança apenas até depois de já ter jogado/resolvido a partida dele.
     *
     * Seguro contra datas no passado/iguais: se [targetDateIso] não for
     * posterior à data atual, não faz nada.
     */
    fun advanceCalendarTo(context: Context, targetDateIso: String): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        var date = LocalDate.parse(gs.currentDate)
        val target = LocalDate.parse(targetDateIso)

        while (date.isBefore(target)) {
            date = date.plusDays(1)
            processDailyTicks(context, gs, date, report)
            MatchDaySimulator.simulateOpponentsOn(context, gs, date.toString(), report)
        }

        GameRepository.save(context)
        return report
    }

    /**
     * Versão pública da simulação de outros times para a data ATUAL do jogo.
     * Usada pela [com.cblol.scout.ui.MatchSimulationActivity] logo após o
     * jogador resolver a própria partida, garantindo que as partidas dos
     * demais times agendadas para o MESMO dia também sejam simuladas.
     */
    fun simulateOpponentMatchesToday(context: Context): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        MatchDaySimulator.simulateOpponentsOn(context, gs, gs.currentDate, report)
        GameRepository.save(context)
        return report
    }

    // ── API pública: carreira ────────────────────────────────────────────

    /**
     * Cria uma carreira nova: gera estado inicial + calendário.
     *
     * @param division divisão em que a carreira começa. Em
     *   [com.cblol.scout.data.Division.FIRST], os adversários vêm do snapshot
     *   e a economia segue o tier do time. Em
     *   [com.cblol.scout.data.Division.SECOND], geramos 8 times procedurais
     *   via [com.cblol.scout.util.SecondDivisionTeamsGenerator] e usamos
     *   orçamento/patrocínio reduzidos da 2ª divisão.
     * @param seed semente opcional para geração determinística da 2ª divisão.
     */
    fun startNewCareer(
        context: Context,
        managerName: String,
        teamId: String,
        division: com.cblol.scout.data.Division = com.cblol.scout.data.Division.FIRST,
        seed: Long = System.currentTimeMillis()
    ): GameState = CareerStarter.start(context, managerName, teamId, division, seed)

    // ── API pública: consultas e operações pontuais ─────────────────────

    /** Próxima partida do meu time (a partir da data atual, inclusive). */
    fun nextMatchForManager(): Match? {
        val gs = GameRepository.current()
        return gs.matches
            .filter { !it.played }
            .filter { it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId }
            .filter { it.date >= gs.currentDate }
            .minByOrNull { it.date }
    }

    /** Soma dos salários mensais de todo o elenco (titulares + reservas). */
    fun totalMonthlyPayroll(context: Context): Long {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        return roster.sumOf { it.contrato.salario_mensal_estimado_brl ?: 0 }
    }

    /**
     * Registra o resultado de UM mapa da série (chamado pela
     * MatchSimulationActivity quando o jogador termina cada mapa). Atualiza
     * o placar do match e, se a série acabou (alguém chegou a 2 mapas),
     * marca `played = true`.
     */
    fun recordMatchResult(context: Context, matchId: String, mapNumber: Int, winnerTeamId: String) {
        val gs = GameRepository.current()
        val match = gs.matches.find { it.id == matchId }
            ?: error("Partida não encontrada: $matchId")

        when (mapNumber) {
            1 -> if (winnerTeamId == match.homeTeamId) match.homeScore = 1 else match.awayScore = 1
            2 -> if (winnerTeamId == match.homeTeamId) match.homeScore = 2 else match.awayScore = 2
            else -> error("Número de mapa inválido: $mapNumber")
        }

        val series = gs.seriesState[matchId]
        if (series != null && series.isFinished) {
            match.played = true
            gs.seriesState.remove(matchId)
            GameRepository.save(context)
        }
    }

    // ── Coordenação interna ─────────────────────────────────────────────

    /**
     * Processa todos os eventos NÃO-partida de UM dia: avança a data, detecta
     * transição de janela, aplica decay de moral, tick de scouting, ofertas,
     * laços, academia, e pagamentos semanais/mensais. A simulação de partidas
     * é responsabilidade do [MatchDaySimulator].
     *
     * Quem chama (advanceDays / advanceCalendarTo) decide depois quais
     * partidas simular para o dia: todas ou só as dos outros times.
     */
    private fun processDailyTicks(
        context: Context,
        gs: GameState,
        date: LocalDate,
        report: AdvanceReport
    ) {
        DailyTicksProcessor.process(context, gs, date, report)
        EconomyProcessor.process(context, gs, date, report)
    }
}

/** Relatório consolidado retornado por [GameEngine.advanceDays]/[GameEngine.advanceCalendarTo]. */
data class AdvanceReport(
    var matchesPlayed: Int = 0,
    var myWin: Boolean = false,
    var myLoss: Boolean = false,
    var income: Long = 0,
    var expense: Long = 0,
    /**
     * Jogadores que pediram transferência neste avanço de dias (por insatisfação
     * persistente). Lista de nomes para o Hub mostrar em uma notificação.
     */
    val transferRequests: MutableList<String> = mutableListOf(),

    /**
     * Jogadores que receberam ofertas de compra de outros times neste avanço.
     * Lista de nomes para o Hub notificar que há propostas a responder.
     */
    val incomingOffers: MutableList<String> = mutableListOf(),

    /**
     * Prospects da categoria de base que atingiram o nível para subir ao elenco
     * principal neste avanço. Lista de nomes para o Hub notificar o gerente.
     */
    val academyReady: MutableList<String> = mutableListOf(),

    /**
     * Saúde financeira ao fim do avanço, quando NÃO está saudável (amarelo ou
     * vermelho). Null = caixa saudável, sem aviso. O Hub usa isto para exibir
     * um alerta e sugerir o Banco.
     */
    var financialHealthWarning: com.cblol.scout.data.FinancialHealth? = null
)
