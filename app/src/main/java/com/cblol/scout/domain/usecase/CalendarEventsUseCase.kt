package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Match
import com.cblol.scout.data.SponsorContract
import com.cblol.scout.data.TransferWindow
import com.cblol.scout.game.GameRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Modelo unificado de "evento que cabe num calendário" para a [com.cblol.scout.ui.ScheduleActivity].
 *
 * Diferente do log/feed de notícias, que registram coisas que JÁ aconteceram,
 * o calendário projeta o que **vai acontecer** num determinado dia. As fontes
 * são heterogêneas (partidas, janelas, pagamentos, prazos de oferta), mas a
 * UI só precisa saber 3 coisas por evento: que dia é, que categoria visual usar
 * e o que mostrar como título/subtítulo.
 *
 * **SOLID/OCP**: novos sistemas (ex: torneios, draft de free agents) entram
 * como uma nova subclasse aqui + um novo bloco em [CalendarEventsAggregator];
 * a UI continua tratando tudo via [category].
 */
sealed class CalendarEvent {
    /** Data ISO yyyy-MM-dd em que o evento acontece. */
    abstract val date: String
    /** Categoria visual (cor do ponto/chip + ícone). */
    abstract val category: CalendarEventCategory
    /** Texto principal exibido na lista do dia. */
    abstract val title: String
    /** Texto curto secundário. Null = sem subtítulo. */
    abstract val subtitle: String?

    /** Partida do time do gerente. Carrega o id da partida para deep-link. */
    data class ManagerMatch(
        override val date: String,
        val matchId: String,
        val round: Int,
        val opponentName: String,
        val isHome: Boolean,
        val played: Boolean,
        val homeScore: Int,
        val awayScore: Int
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.MATCH_MANAGER
        override val title = if (isHome) "Meu time vs $opponentName" else "$opponentName vs Meu time"
        override val subtitle: String? =
            if (played) "Encerrada · $homeScore-$awayScore" else "Rodada $round · BO3"
    }

    /** Partida entre dois outros times — útil para acompanhar a tabela. */
    data class OtherMatch(
        override val date: String,
        val matchId: String,
        val round: Int,
        val homeName: String,
        val awayName: String,
        val played: Boolean,
        val homeScore: Int,
        val awayScore: Int
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.MATCH_OTHER
        override val title = "$homeName vs $awayName"
        override val subtitle: String? =
            if (played) "Encerrada · $homeScore-$awayScore" else "Rodada $round"
    }

    /** Início de janela de transferências. */
    data class TransferWindowOpens(
        override val date: String,
        val windowLabel: String
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.TRANSFER
        override val title = "Mercado abre: $windowLabel"
        override val subtitle: String? = "Compras, vendas e propostas liberadas"
    }

    /** Fim de janela de transferências. */
    data class TransferWindowCloses(
        override val date: String,
        val windowLabel: String
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.TRANSFER
        override val title = "Mercado fecha: $windowLabel"
        override val subtitle: String? = "Última chance de movimentar o elenco"
    }

    /** Oferta de compra recebida que vai expirar. */
    data class IncomingOfferExpires(
        override val date: String,
        val playerName: String,
        val fromTeamName: String,
        val amountBrl: Long
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.TRANSFER
        override val title = "Proposta por $playerName expira"
        override val subtitle: String? =
            "Oferta de $fromTeamName · R\$ ${"%,d".format(amountBrl)}"
    }

    /** Oferta de patrocínio disponível que vai expirar. */
    data class SponsorOfferExpires(
        override val date: String,
        val sponsorName: String,
        val weeklyAmount: Long
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.SPONSOR
        override val title = "Oferta $sponsorName expira"
        override val subtitle: String? = "R\$ ${"%,d".format(weeklyAmount)}/sem"
    }

    /** Contrato de patrocínio ativo chegando ao fim. */
    data class SponsorContractEnds(
        override val date: String,
        val sponsorName: String
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.SPONSOR
        override val title = "Contrato com $sponsorName termina"
        override val subtitle: String? = "Última cobrança semanal"
    }

    /** Pagamento semanal de patrocínio (todo domingo). Inclui contratos ativos. */
    data class WeeklySponsorPayout(
        override val date: String,
        val expectedAmount: Long
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.FINANCE
        override val title = "Patrocínio semanal"
        override val subtitle: String? =
            "Receita esperada: R\$ ${"%,d".format(expectedAmount)}"
    }

    /** Folha salarial mensal (dia 1 de cada mês). */
    data class Payroll(
        override val date: String,
        val totalAmount: Long
    ) : CalendarEvent() {
        override val category = CalendarEventCategory.FINANCE
        override val title = "Folha salarial"
        override val subtitle: String? =
            "Despesa: R\$ ${"%,d".format(totalAmount)}"
    }

    /** Marco do split — início. */
    data class SplitStart(override val date: String) : CalendarEvent() {
        override val category = CalendarEventCategory.SPLIT
        override val title = "Início do split"
        override val subtitle: String? = "Primeira rodada do campeonato"
    }

    /** Marco do split — encerramento. */
    data class SplitEnd(override val date: String) : CalendarEvent() {
        override val category = CalendarEventCategory.SPLIT
        override val title = "Fim do split"
        override val subtitle: String? = "Última rodada da fase regular"
    }
}

/** Categoria visual de um [CalendarEvent] — define cor do ponto + ícone. */
enum class CalendarEventCategory(val emoji: String, val label: String) {
    MATCH_MANAGER("🏆", "Meu jogo"),
    MATCH_OTHER("🎮", "Outro jogo"),
    TRANSFER("🔄", "Mercado"),
    SPONSOR("💼", "Patrocínio"),
    FINANCE("💰", "Finanças"),
    SPLIT("🎯", "Split")
}

// ─────────────────────────────────────────────────────────────────────────────
// AGREGADOR
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estado completo do calendário mensal: a grade de células do mês e os eventos
 * agrupados por dia. Tudo já pronto para a UI consumir sem cálculos extras.
 *
 * @property displayedMonth o mês exibido (YearMonth)
 * @property cells 42 ou 35 células (5-6 linhas × 7 colunas) representando o
 *   mês com bordas dos meses adjacentes para preencher o quadrado, padrão de
 *   calendários mobile.
 * @property eventsByDate map para lookup O(1) da lista de eventos por dia
 *   (chave = data ISO yyyy-MM-dd, ordenada por categoria)
 */
data class CalendarMonthState(
    val displayedMonth: YearMonth,
    val cells: List<CalendarDayCell>,
    val eventsByDate: Map<String, List<CalendarEvent>>
)

/**
 * Uma célula da grade do mês.
 *
 * @property date data representada
 * @property dayOfMonth número do dia (1-31), pronto para exibir
 * @property inDisplayedMonth se o dia pertence ao mês exibido (false = dia
 *   "cinza" do mês adjacente, mostrado para fechar o quadrado da grade)
 * @property isToday se é a data atual do jogo
 * @property isWithinSplit se está entre `splitStartDate` e `splitEndDate`
 *   (destaque sutil de fundo)
 * @property eventCount quantos eventos têm neste dia (badge na célula)
 * @property categories conjunto de categorias presentes (pontos coloridos)
 */
data class CalendarDayCell(
    val date: LocalDate,
    val dayOfMonth: Int,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean,
    val isWithinSplit: Boolean,
    val eventCount: Int,
    val categories: Set<CalendarEventCategory>
)

/**
 * Constrói o [CalendarMonthState] para um mês, agregando eventos de TODOS os
 * subsistemas do save: partidas, janelas, ofertas recebidas, patrocínios
 * (ofertas e contratos), recorrências (folha + patrocínio semanal) e marcos
 * do split.
 *
 * **Por que não usar uma view direta no Realm?** Os eventos têm origens muito
 * heterogêneas (alguns vêm de listas, outros são derivados — folha do dia 1,
 * patrocínio do domingo). Centralizar em um UseCase mantém o cálculo em um
 * lugar testável e desacopla a UI da forma exata como cada subsistema persiste.
 *
 * **SOLID:**
 *  - **SRP**: agrega; não joga, não simula, não persiste.
 *  - **OCP**: novos eventos = nova função `collect*` + chamada no [invoke].
 *  - **DIP**: depende de [GameRepository] (estado em memória) e contexto Android.
 */
class CalendarEventsAggregator(private val context: Context) {

    /** Constrói o estado para [month]. Não-bloqueante, leve. */
    operator fun invoke(month: YearMonth): CalendarMonthState {
        val gs = GameRepository.current()
        val teams = GameRepository.teamsForCurrentDivision(context)
            .associate { it.id to it.nome }

        // Limites: 1 mês ANTES e 1 mês DEPOIS do exibido para popular células
        // dos meses adjacentes (a grade mostra 6 semanas; o último dia do mês
        // anterior e os primeiros do próximo entram na borda).
        val cellWindow = monthGridRange(month)

        val events = collectAllEvents(gs, teams, cellWindow)
        val byDate = events.groupBy { it.date }
            .mapValues { (_, list) -> list.sortedBy { it.category.ordinal } }
        val cells = buildCells(month, gs.currentDate, gs.splitStartDate, gs.splitEndDate, byDate)

        return CalendarMonthState(month, cells, byDate)
    }

    /** Eventos de UM dia específico, ordenados por categoria. */
    fun eventsOn(date: LocalDate): List<CalendarEvent> {
        val month = YearMonth.from(date)
        return invoke(month).eventsByDate[date.toString()].orEmpty()
    }

    // ── Coleta por categoria ─────────────────────────────────────────────

    private fun collectAllEvents(
        gs: com.cblol.scout.data.GameState,
        teams: Map<String, String>,
        window: ClosedRange<LocalDate>
    ): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()

        collectMatches(gs, teams, window, out)
        collectTransferWindows(gs.transferWindows, window, out)
        collectIncomingOffers(gs.incomingOffers, window, out)
        collectSponsorEvents(gs.activeSponsors, gs.availableSponsorOffers, window, out)
        collectRecurring(gs, window, out)
        collectSplitMarkers(gs.splitStartDate, gs.splitEndDate, window, out)

        return out
    }

    private fun collectMatches(
        gs: com.cblol.scout.data.GameState,
        teams: Map<String, String>,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        gs.matches.forEach { m ->
            val day = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@forEach
            if (day !in window) return@forEach
            if (m.homeTeamId == gs.managerTeamId || m.awayTeamId == gs.managerTeamId) {
                out += buildManagerMatch(m, gs.managerTeamId, teams)
            } else {
                out += CalendarEvent.OtherMatch(
                    date = m.date,
                    matchId = m.id,
                    round = m.round,
                    homeName = teams[m.homeTeamId] ?: m.homeTeamId,
                    awayName = teams[m.awayTeamId] ?: m.awayTeamId,
                    played = m.played,
                    homeScore = m.homeScore,
                    awayScore = m.awayScore
                )
            }
        }
    }

    private fun buildManagerMatch(
        m: Match,
        managerTeamId: String,
        teams: Map<String, String>
    ): CalendarEvent.ManagerMatch {
        val isHome = m.homeTeamId == managerTeamId
        val opponentId = if (isHome) m.awayTeamId else m.homeTeamId
        return CalendarEvent.ManagerMatch(
            date = m.date,
            matchId = m.id,
            round = m.round,
            opponentName = teams[opponentId] ?: opponentId,
            isHome = isHome,
            played = m.played,
            homeScore = m.homeScore,
            awayScore = m.awayScore
        )
    }

    private fun collectTransferWindows(
        windows: List<TransferWindow>?,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        windows?.forEach { tw ->
            val start = runCatching { LocalDate.parse(tw.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(tw.endDate) }.getOrNull()
            if (start != null && start in window) {
                out += CalendarEvent.TransferWindowOpens(tw.startDate, tw.kind.label)
            }
            if (end != null && end in window) {
                out += CalendarEvent.TransferWindowCloses(tw.endDate, tw.kind.label)
            }
        }
    }

    private fun collectIncomingOffers(
        offers: List<com.cblol.scout.data.IncomingTransferOffer>?,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        // Apenas propostas PENDENTES geram marcador no calendário — propostas
        // já resolvidas (aceitas/recusadas/expiradas) vão para o histórico,
        // mas o prazo delas não é mais acionável então não polui o calendário.
        offers?.forEach { offer ->
            if (!offer.isPending) return@forEach
            val day = runCatching { LocalDate.parse(offer.expiresOn) }.getOrNull() ?: return@forEach
            if (day !in window) return@forEach
            out += CalendarEvent.IncomingOfferExpires(
                date = offer.expiresOn,
                playerName = offer.playerName,
                fromTeamName = offer.fromTeamName,
                amountBrl = offer.amountBrl
            )
        }
    }

    private fun collectSponsorEvents(
        activeSponsors: List<SponsorContract>?,
        availableOffers: List<com.cblol.scout.data.SponsorOffer>?,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        availableOffers?.forEach { offer ->
            val day = runCatching { LocalDate.parse(offer.expiresOn) }.getOrNull() ?: return@forEach
            if (day in window) {
                out += CalendarEvent.SponsorOfferExpires(
                    date = offer.expiresOn,
                    sponsorName = offer.sponsor.name,
                    weeklyAmount = offer.sponsor.weeklyAmount
                )
            }
        }
        activeSponsors?.forEach { contract ->
            val day = runCatching { LocalDate.parse(contract.endDate) }.getOrNull() ?: return@forEach
            if (day in window) {
                out += CalendarEvent.SponsorContractEnds(
                    date = contract.endDate,
                    sponsorName = contract.sponsor.name
                )
            }
        }
    }

    /**
     * Coleta eventos **recorrentes**: patrocínio semanal (todo domingo) e
     * folha salarial (dia 1 de cada mês). Como não vivem em listas do estado,
     * são derivados iterando o intervalo do calendário.
     *
     * Só inclui datas FUTURAS ou hoje — eventos recorrentes passados poluiriam
     * o calendário com pontos sem valor informativo (o jogador não vai planejar
     * o passado).
     */
    private fun collectRecurring(
        gs: com.cblol.scout.data.GameState,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        val today = runCatching { LocalDate.parse(gs.currentDate) }.getOrNull() ?: return
        val payroll = runCatching {
            com.cblol.scout.game.GameEngine.totalMonthlyPayroll(context)
        }.getOrDefault(0L)

        var day = window.start
        while (day <= window.endInclusive) {
            if (!day.isBefore(today)) {
                if (day.dayOfMonth == 1) {
                    out += CalendarEvent.Payroll(day.toString(), payroll)
                }
                if (day.dayOfWeek == DayOfWeek.SUNDAY) {
                    out += CalendarEvent.WeeklySponsorPayout(
                        day.toString(),
                        gs.sponsorshipPerWeek + sponsorWeeklyOnDate(gs.activeSponsors, day)
                    )
                }
            }
            day = day.plusDays(1)
        }
    }

    /** Soma receita semanal dos patrocinadores ATIVOS na data dada. */
    private fun sponsorWeeklyOnDate(
        contracts: List<SponsorContract>?,
        day: LocalDate
    ): Long {
        if (contracts.isNullOrEmpty()) return 0L
        return contracts.sumOf { c ->
            val start = runCatching { LocalDate.parse(c.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(c.endDate) }.getOrNull()
            if (start != null && end != null && !day.isBefore(start) && !day.isAfter(end))
                c.sponsor.weeklyAmount
            else 0L
        }
    }

    private fun collectSplitMarkers(
        splitStart: String,
        splitEnd: String,
        window: ClosedRange<LocalDate>,
        out: MutableList<CalendarEvent>
    ) {
        runCatching { LocalDate.parse(splitStart) }.getOrNull()?.let { d ->
            if (d in window) out += CalendarEvent.SplitStart(splitStart)
        }
        runCatching { LocalDate.parse(splitEnd) }.getOrNull()?.let { d ->
            if (d in window) out += CalendarEvent.SplitEnd(splitEnd)
        }
    }

    // ── Construção da grade ──────────────────────────────────────────────

    /**
     * Intervalo de dias da grade do mês: primeira coluna alinha com domingo
     * da semana que contém o dia 1, e a última coluna com o sábado da semana
     * do último dia. Tipicamente 35-42 dias.
     */
    private fun monthGridRange(month: YearMonth): ClosedRange<LocalDate> {
        val firstOfMonth = month.atDay(1)
        val lastOfMonth = month.atEndOfMonth()
        // Em PT-BR o calendário começa no domingo. DayOfWeek.SUNDAY = 7;
        // queremos "voltar X dias até a semana começar".
        // dayOfWeek do Java é 1=segunda..7=domingo; ajustamos para
        // 1=domingo..7=sábado para casar com nossa primeira coluna.
        val offsetFromSunday = (firstOfMonth.dayOfWeek.value % 7)  // dom=0, seg=1, ..., sab=6
        val gridStart = firstOfMonth.minusDays(offsetFromSunday.toLong())
        val tailOffset = 6 - (lastOfMonth.dayOfWeek.value % 7)
        val gridEnd = lastOfMonth.plusDays(tailOffset.toLong())
        return gridStart..gridEnd
    }

    private fun buildCells(
        month: YearMonth,
        currentDateIso: String,
        splitStartIso: String,
        splitEndIso: String,
        byDate: Map<String, List<CalendarEvent>>
    ): List<CalendarDayCell> {
        val today = runCatching { LocalDate.parse(currentDateIso) }.getOrNull()
        val splitStart = runCatching { LocalDate.parse(splitStartIso) }.getOrNull()
        val splitEnd = runCatching { LocalDate.parse(splitEndIso) }.getOrNull()
        val window = monthGridRange(month)

        val cells = mutableListOf<CalendarDayCell>()
        var day = window.start
        while (day <= window.endInclusive) {
            val events = byDate[day.toString()].orEmpty()
            val withinSplit = splitStart != null && splitEnd != null &&
                !day.isBefore(splitStart) && !day.isAfter(splitEnd)
            cells += CalendarDayCell(
                date = day,
                dayOfMonth = day.dayOfMonth,
                inDisplayedMonth = YearMonth.from(day) == month,
                isToday = today != null && day == today,
                isWithinSplit = withinSplit,
                eventCount = events.size,
                categories = events.map { it.category }.toSet()
            )
            day = day.plusDays(1)
        }
        return cells
    }
}
