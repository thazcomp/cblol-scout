package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.BankService
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.domain.usecase.ScoutingService
import com.cblol.scout.domain.usecase.SponsorService
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

    /** @deprecated Use [GameConstants.Economy.PRIZE_PER_MAP_WIN]. */
    const val PRIZE_PER_MAP_WIN = GameConstants.Economy.PRIZE_PER_MAP_WIN

    /** @deprecated Use [GameConstants.Economy.PRIZE_PER_SERIES_WIN]. */
    const val PRIZE_PER_SERIES_WIN = GameConstants.Economy.PRIZE_PER_SERIES_WIN

    /** Avança a data corrente em N dias, processando todos os eventos. */
    fun advanceDays(context: Context, days: Int): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        var date = LocalDate.parse(gs.currentDate)

        repeat(days) {
            date = date.plusDays(1)
            val iso = date.toString()

            // Processa todos os ticks diários (moral, scouting, economia, janelas).
            processDailyTicks(context, gs, date, report)

            // Simula partidas do dia (apenas no avanço automático via advanceDays;
            // o avanço ao jogar manualmente NÃO simula partidas — ver advanceCalendarTo).
            simulateMatchesOn(context, gs, iso, report)
        }

        GameRepository.save(context)
        return report
    }

    /**
     * Avança o calendário do jogo até [targetDateIso] (inclusive), processando
     * os ticks diários de CADA dia intermediário — moral, scouting, pagamentos
     * semanais/mensais e janelas de transferência — SEM simular partidas.
     *
     * É usado quando o tempo avança porque o jogador jogou uma partida
     * manualmente (o salto de data acontece na [com.cblol.scout.ui.MatchSimulationActivity]).
     * Antes desta correção, a data era apenas atribuída (`currentDate = match.date`),
     * o que pulava todos os ticks — em especial o avanço do scouting, que ficava
     * congelado.
     *
     * Não simula partidas porque a partida que causou o avanço já está sendo
     * processada pela própria Activity de simulação; partidas de outros times no
     * período são resolvidas quando o jogador as joga/assiste ou ao fim do split.
     *
     * Seguro contra datas no passado/iguais: se [targetDateIso] não for posterior
     * à data atual, não faz nada.
     */
    fun advanceCalendarTo(context: Context, targetDateIso: String): AdvanceReport {
        val gs = GameRepository.current()
        val report = AdvanceReport()
        var date = LocalDate.parse(gs.currentDate)
        val target = LocalDate.parse(targetDateIso)

        while (date.isBefore(target)) {
            date = date.plusDays(1)
            processDailyTicks(context, gs, date, report)
        }

        GameRepository.save(context)
        return report
    }

    /**
     * Processa os eventos de UM dia: avança a data, detecta transição de janela,
     * aplica decay de moral, tick de scouting, pagamentos semanais e mensais, e
     * gera ofertas de patrocínio. NÃO simula partidas (isso é responsabilidade
     * de quem chama, via [simulateMatchesOn] ou da Activity de simulação).
     */
    private fun processDailyTicks(
        context: Context,
        gs: GameState,
        date: LocalDate,
        report: AdvanceReport
    ) {
        val iso = date.toString()
        val previousDate = gs.currentDate
        gs.currentDate = iso

        // Janela de transferência: detecta abertura/fechamento ao cruzar a data.
        detectTransferWindowTransition(gs, previousDate, iso)

        // 0. Decay temporal de moral + pedidos de transferência por insatisfação.
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val decayResult = MoraleService.applyDailyDecay(gs, roster)
        decayResult.transferRequests.forEach { playerId ->
            val player = roster.find { it.id == playerId } ?: return@forEach
            report.transferRequests += player.nome_jogo
            GameRepository.log(
                "MOOD",
                "${player.nome_jogo} pediu transferência por estar desmotivado."
            )
        }

        // 0.5. Tick de scouting: avança os jogadores em scouting ativo.
        val scoutTick = ScoutingService.tickDaily(gs)
        scoutTick.levelUps.forEach { up ->
            val playerName = GameRepository.snapshot(context).jogadores.find { it.id == up.playerId }?.nome_jogo
                ?: com.cblol.scout.util.SecondDivisionGenerator.generate().find { it.id == up.playerId }?.nome_jogo
                ?: up.playerId
            val msg = if (up.newLevel >= ScoutingService.MAX_LEVEL) {
                "Scouting de $playerName concluído (nível ${up.newLevel})."
            } else {
                "Scouting de $playerName avançou para nível ${up.newLevel}."
            }
            GameRepository.log("SCOUT", msg)
        }

        // 0.7. Ofertas de compra de outros times (só com mercado aberto).
        //      Expira as vencidas e sorteia novas a cada intervalo.
        processIncomingOffers(context, gs, roster, report)

        // 0.8. Evolução dos laços (química) entre os jogadores do elenco.
        //      Considera tempo de convivência, humor médio e pedidos de
        //      transferência. Marcos (parceria forte / rivalidade tóxica) são
        //      logados para o gerente acompanhar o clima do vestiário.
        processPlayerBonds(gs, roster)

        // 0.9. Categoria de base: desenvolve os prospects e recruta novos
        //      talentos periodicamente. Marcos (prospect pronto, novo recruta)
        //      vão para o relatório + log.
        processAcademy(gs, report)

        // 1. Pagamento de patrocínio (domingo = day_of_week 7)
        if (date.dayOfWeek.value == 7) {
            gs.budget += gs.sponsorshipPerWeek
            report.income += gs.sponsorshipPerWeek
            GameRepository.log("ECONOMY",
                "Patrocínio semanal recebido: R$ ${"%,d".format(gs.sponsorshipPerWeek)}")

            val sponsorResult = SponsorService.paySponsorsWeekly(gs)
            if (sponsorResult.totalPaid > 0) {
                report.income += sponsorResult.totalPaid
                GameRepository.log("ECONOMY",
                    "Patrocínios: R$ ${"%,d".format(sponsorResult.totalPaid)} recebidos (${gs.activeSponsors?.size ?: 0} ativos)")
            }
            sponsorResult.expiredContracts.forEach { contract ->
                GameRepository.log("ECONOMY",
                    "Patrocínio com ${contract.sponsor.name} expirou. Total recebido: R$ ${"%,d".format(contract.totalReceived)}")
            }

            val scoutingFee = ScoutingService.weeklyMaintenanceCost(gs)
            if (scoutingFee > 0) {
                gs.budget -= scoutingFee
                report.expense += scoutingFee
                GameRepository.log("ECONOMY",
                    "Manutenção do departamento de olheiros: R$ ${"%,d".format(scoutingFee)} (${ScoutingService.tier(gs).label})")
            }

            val academyFee = com.cblol.scout.domain.usecase.AcademyService.weeklyMaintenanceCost(gs)
            if (academyFee > 0) {
                gs.budget -= academyFee
                report.expense += academyFee
                GameRepository.log("ECONOMY",
                    "Manutenção da categoria de base: R$ ${"%,d".format(academyFee)} (${com.cblol.scout.domain.usecase.AcademyService.tier(gs).label})")
            }

            // Parcelas de empréstimos bancários (uma por semana por empréstimo).
            val loanResult = BankService.chargeWeeklyInstallments(gs)
            if (loanResult.totalCharged > 0) {
                report.expense += loanResult.totalCharged
                GameRepository.log("ECONOMY",
                    "Parcelas de empréstimo pagas: R$ ${"%,d".format(loanResult.totalCharged)}")
            }
            loanResult.loansPaidOff.forEach { loan ->
                GameRepository.log("ECONOMY",
                    "✅ Empréstimo \"${loan.label}\" quitado!")
            }
        }

        // 1.5. Gera novas ofertas de patrocínio se passou o intervalo
        SponsorService.generateOffersIfDue(gs)

        // 2. Pagamento de salários (dia 1 de cada mês)
        if (date.dayOfMonth == 1) {
            val total = totalMonthlyPayroll(context)
            gs.budget -= total
            report.expense += total
            GameRepository.log("ECONOMY",
                "Folha salarial paga: R$ ${"%,d".format(total)}")
        }

        // 3. Aviso de saúde financeira: marca o relatório se o caixa entrou em
        //    zona de atenção/crítica após os movimentos do dia, para o Hub poder
        //    alertar o gerente e sugerir o Banco.
        if (BankService.shouldWarn(gs)) {
            report.financialHealthWarning = BankService.financialHealth(gs)
        }
    }

    /**
     * Simula todas as partidas pendentes da data [iso] e aplica prêmios. Usado
     * apenas no avanço automático ([advanceDays]).
     */
    private fun simulateMatchesOn(
        context: Context,
        gs: GameState,
        iso: String,
        report: AdvanceReport
    ) {
        val todayMatches = gs.matches.filter { it.date == iso && !it.played }
        if (todayMatches.isNotEmpty()) {
            // Validação: garante que há 5 titulares antes de jogar
            SquadManager.validateAndFixRoster(context)
        }
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

    /**
     * Detecta transição de janela de transferência entre dois dias consecutivos
     * e loga a abertura/fechamento para o jogador.
     *
     * Compara o estado do mercado em [previousDate] com o de [today]:
     *  - fechado → aberto: loga "mercado aberto" (com o tipo da janela)
     *  - aberto → fechado: loga "mercado fechado"
     *
     * Delega a detecção ao [com.cblol.scout.domain.usecase.TransferWindowService]
     * para não duplicar a regra de intervalos de janela.
     */
    private fun detectTransferWindowTransition(
        gs: GameState,
        previousDate: String,
        today: String
    ) {
        val service = com.cblol.scout.domain.usecase.TransferWindowService
        val (transition, window) = service.detectTransition(gs, previousDate, today)
        when (transition) {
            com.cblol.scout.domain.usecase.TransferWindowService.WindowTransition.OPENED -> {
                val label = window?.kind?.label ?: "Transferências"
                GameRepository.log(
                    "TRANSFER",
                    "🟢 Janela de transferências ABERTA ($label). O mercado está disponível."
                )
            }
            com.cblol.scout.domain.usecase.TransferWindowService.WindowTransition.CLOSED -> {
                GameRepository.log(
                    "TRANSFER",
                    "🔴 Janela de transferências FECHADA. O mercado não aceita mais movimentações por enquanto."
                )
            }
            com.cblol.scout.domain.usecase.TransferWindowService.WindowTransition.NONE -> Unit
        }
    }

    /**
     * Processa ofertas de compra de outros times num tick diário:
     *  1. Expira ofertas vencidas (por data ou mercado fechado).
     *  2. Gera novas ofertas se for dia de gerar e o mercado estiver aberto.
     *
     * As novas ofertas vão para o [AdvanceReport] (nomes dos jogadores) para o
     * Hub poder notificar o gerente, e também são logadas.
     */
    private fun processIncomingOffers(
        context: Context,
        gs: GameState,
        roster: List<Player>,
        report: AdvanceReport
    ) {
        com.cblol.scout.domain.usecase.IncomingOfferService.expireOffers(gs)

        val snapshot = GameRepository.snapshot(context)
        val result = com.cblol.scout.domain.usecase.IncomingOfferService.generateOffersIfDue(
            state = gs,
            snapshot = snapshot,
            roster = roster,
            marketPriceOf = { TransferMarket.marketPriceOf(it) }
        )
        result.newOffers.forEach { offer ->
            report.incomingOffers += offer.playerName
            GameRepository.log(
                "TRANSFER",
                "💰 ${offer.fromTeamName} ofereceu R$ ${"%,d".format(offer.amountBrl)} por ${offer.playerName}."
            )
        }
    }

    /**
     * Processa a evolução diária dos laços entre os jogadores do elenco e loga
     * marcos relevantes (formação de parceria forte ou rivalidade tóxica).
     *
     * O [com.cblol.scout.domain.usecase.PlayerBondService.tickDaily] é
     * idempotente por data (usa [GameState.lastBondTickDate]), então é seguro
     * chamá-lo a cada dia processado: cada chamada avança exatamente 1 dia de
     * convivência.
     */
    private fun processPlayerBonds(gs: GameState, roster: List<Player>) {
        val milestones = com.cblol.scout.domain.usecase.PlayerBondService.tickDaily(gs, roster)
        milestones.forEach { m ->
            val nameA = roster.find { it.id == m.playerAId }?.nome_jogo ?: m.playerAId
            val nameB = roster.find { it.id == m.playerBId }?.nome_jogo ?: m.playerBId
            val msg = when (m.tier) {
                com.cblol.scout.data.BondTier.BONDED ->
                    "🔥 $nameA e $nameB formaram uma parceria forte dentro e fora do jogo."
                com.cblol.scout.data.BondTier.TOXIC ->
                    "☠️ A relação entre $nameA e $nameB azedou de vez — clima pesado no vestiário."
                else -> return@forEach
            }
            GameRepository.log("MOOD", msg)
        }
    }

    /**
     * Processa o desenvolvimento diário da categoria de base: evolui prospects e
     * recruta novos talentos periodicamente. Loga marcos relevantes (prospect
     * pronto para subir, novo talento recrutado).
     *
     * O [com.cblol.scout.domain.usecase.AcademyService.tickDaily] é idempotente
     * por data (usa [GameState.lastAcademyTickDate]).
     */
    private fun processAcademy(gs: GameState, report: AdvanceReport) {
        val result = com.cblol.scout.domain.usecase.AcademyService.tickDaily(gs)
        result.readyNow.forEach { p ->
            report.academyReady += p.nome
            GameRepository.log(
                "ACADEMY",
                "🌟 ${p.nome} (${p.role}) atingiu o nível para subir ao elenco principal!"
            )
        }
        result.recruited.forEach { p ->
            GameRepository.log(
                "ACADEMY",
                "🌱 Novo talento na base: ${p.nome} (${p.role}, ${p.idade} anos)."
            )
        }
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
        // Jogo começa no primeiro dia da janela de pré-temporada (janela grande):
        // splitStart menos PRE_SEASON_DURATION_DAYS. Fonte única no TransferWindowService.
        val gameStart  = com.cblol.scout.domain.usecase.TransferWindowService.gameStartFor(splitStart)

        val gs = GameState(
            managerName = managerName,
            managerTeamId = teamId,
            splitStartDate = splitStart,
            splitEndDate = splitEnd,
            currentDate = gameStart, // começa na pré-temporada (mercado aberto)
            budget = budget,
            sponsorshipPerWeek = sponsorship
        )
        gs.matches.addAll(ScheduleGenerator.generate(snap.times.map { it.id }, splitStart))

        // Janelas de transferência: pré-temporada (agora) + inter-temporada (meio do split).
        // O jogo começa dentro da janela de pré-temporada — mercado aberto desde o início.
        gs.transferWindows.addAll(
            com.cblol.scout.domain.usecase.TransferWindowService
                .buildWindowsForSplit(gameStart, splitStart)
        )

        // Laços: inicializa a química (neutra) entre todos os pares do elenco
        // inicial. A partir daqui ela evolui com o tempo de convivência, humor,
        // resultados e eventos fora de partida.
        val initialRoster = GameRepository.rosterOf(context, teamId)
        com.cblol.scout.domain.usecase.PlayerBondService.ensureBondsFor(gs, initialRoster)

        // Categoria de base: cria a academia BASIC e recruta a leva inicial de
        // prospects, para o gerente já começar com talentos para desenvolver.
        com.cblol.scout.domain.usecase.AcademyService.initializeForNewCareer(gs)

        gs.gameLog.add(
            com.cblol.scout.data.LogEntry(
                gs.currentDate, "CAREER",
                "Pré-temporada iniciada. Você é o novo técnico do ${team.nome}! Mercado de transferências aberto."
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
