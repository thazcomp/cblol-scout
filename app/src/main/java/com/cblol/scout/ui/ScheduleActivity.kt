package com.cblol.scout.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.domain.usecase.CalendarDayCell
import com.cblol.scout.domain.usecase.CalendarEvent
import com.cblol.scout.domain.usecase.CalendarEventCategory
import com.cblol.scout.domain.usecase.CalendarMonthState
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.ScheduleEvent
import com.cblol.scout.ui.viewmodel.ScheduleViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * **Calendário mensal** do split — substitui a antiga lista plana de partidas.
 *
 * Mostra uma grade real estilo Google Calendar: navegação por mês, células
 * clicáveis e painel inferior com TODOS os eventos do dia selecionado:
 * partidas (do gerente e dos outros times), janelas de transferência,
 * propostas recebidas expirando, ofertas de patrocínio expirando, contratos
 * que terminam, recorrências (folha + patrocínio semanal) e marcos do split.
 *
 * **Arquitetura:**
 *  - `ScheduleViewModel.monthState` → grade do mês + eventos por data.
 *  - `ScheduleViewModel.selectedDayEvents` → eventos do dia clicado.
 *  - Dois `RecyclerView`s: um GRID (7 colunas) para a grade, e uma lista
 *    vertical para os eventos do dia.
 *  - A interação de **pick & ban / simular partida** é mantida pelo fluxo
 *    legado quando o jogador toca em uma partida do próprio time na lista de
 *    eventos do dia.
 *
 * **SOLID:**
 *  - **SRP**: a Activity coordena UI; agregação vive em
 *    [com.cblol.scout.domain.usecase.CalendarEventsAggregator].
 *  - **OCP**: novos tipos de evento entram no aggregator + `categoryColor()`
 *    aqui; o renderizador da lista usa polimorfismo via [CalendarEvent.title].
 *  - **DIP**: depende do VM (Koin) e UseCases — não toca `Service` direto.
 */
class ScheduleActivity : AppCompatActivity() {

    private val vm: ScheduleViewModel by viewModel()

    private lateinit var tvMonthLabel: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerGrid: RecyclerView
    private lateinit var recyclerEvents: RecyclerView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private val gridAdapter = MonthGridAdapter(::onDayClicked)
    private val eventsAdapter = DayEventsAdapter(::onEventClicked)

    private val monthLabelFmt = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",
        java.util.Locale("pt", "BR"))

    /**
     * Data atual da carreira (do `GameState.currentDate`). Única referência
     * para decidir se uma partida pode ser jogada/assistida — partidas de
     * outros dias só podem ser visualizadas (informativo).
     *
     * Recalculada em `onCreate` e em `onResume` porque o calendário pode ter
     * avançado em outra tela (Hub, MatchSimulation) enquanto a Schedule
     * estava em background.
     */
    private var today: LocalDate = LocalDate.now()

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        setupToolbar()
        bindViews()
        setupRecyclers()
        setupNavigation()

        GameRepository.load(applicationContext)

        observeViewModel()

        today = currentDateFromState()
        eventsAdapter.setToday(today)
        vm.initCalendar(today)
    }

    override fun onResume() {
        super.onResume()
        // O calendário do jogo pode ter avançado em outra tela — reler hoje.
        today = currentDateFromState()
        eventsAdapter.setToday(today)
        vm.refreshMatches()
    }

    /** Resolve a data atual da carreira; fallback para hoje real se algo deu errado. */
    private fun currentDateFromState(): LocalDate =
        runCatching { LocalDate.parse(GameRepository.current().currentDate) }
            .getOrDefault(LocalDate.now())

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun bindViews() {
        tvMonthLabel   = findViewById(R.id.tv_month_label)
        tvSelectedDate = findViewById(R.id.tv_selected_date)
        tvEmpty        = findViewById(R.id.tv_empty)
        recyclerGrid   = findViewById(R.id.recycler_grid)
        recyclerEvents = findViewById(R.id.recycler_events)
        btnPrev        = findViewById(R.id.btn_prev_month)
        btnNext        = findViewById(R.id.btn_next_month)
    }

    private fun setupRecyclers() {
        // Grade do mês: 7 colunas fixas (dom..sab).
        recyclerGrid.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        recyclerGrid.adapter = gridAdapter

        // Lista vertical de eventos do dia selecionado.
        recyclerEvents.layoutManager = LinearLayoutManager(this)
        recyclerEvents.adapter = eventsAdapter
    }

    private fun setupNavigation() {
        btnPrev.setOnClickListener { vm.previousMonth() }
        btnNext.setOnClickListener { vm.nextMonth() }
        // Tap no nome do mês volta para o mês atual da carreira (pula direto
        // para "hoje" — atalho útil quando o usuário navegou para meses distantes).
        tvMonthLabel.setOnClickListener {
            val today = runCatching { LocalDate.parse(GameRepository.current().currentDate) }
                .getOrNull() ?: return@setOnClickListener
            vm.showMonth(YearMonth.from(today))
            vm.selectDay(today)
        }
    }

    // ── Observers ────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.monthState.observe(this) { renderMonth(it) }
        vm.selectedDate.observe(this) { date ->
            gridAdapter.setSelected(date)
            renderSelectedDateLabel(date, vm.selectedDayEvents.value.orEmpty().size)
        }
        vm.selectedDayEvents.observe(this) { events ->
            renderDayEvents(events)
            // Recarrega o label porque a contagem mudou ao trocar de dia.
            vm.selectedDate.value?.let { renderSelectedDateLabel(it, events.size) }
        }
        vm.event.observe(this) { event ->
            when (event) {
                is ScheduleEvent.LaunchSimulation -> launchSimulation(event.matchId)
                is ScheduleEvent.ShowResult       -> startActivity(event.result.toResultIntent(this))
            }
        }
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun renderMonth(state: CalendarMonthState) {
        tvMonthLabel.text = getString(
            R.string.schedule_month_year_format,
            monthName(state.displayedMonth),
            state.displayedMonth.year
        )
        gridAdapter.submit(state.cells)
        // Mantém a seleção visível mesmo trocando de mês.
        vm.selectedDate.value?.let { gridAdapter.setSelected(it) }
    }

    private fun renderSelectedDateLabel(date: LocalDate, eventCount: Int) {
        val human = date.format(monthLabelFmt)
        tvSelectedDate.text = if (eventCount > 0)
            getString(R.string.schedule_selected_date_format, human, eventCount)
        else
            getString(R.string.schedule_selected_date_empty, human)
    }

    private fun renderDayEvents(events: List<CalendarEvent>) {
        if (events.isEmpty()) {
            recyclerEvents.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            recyclerEvents.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            eventsAdapter.submit(events)
        }
    }

    // ── Ações ────────────────────────────────────────────────────────────

    private fun onDayClicked(cell: CalendarDayCell) {
        // Se o usuário clicou num dia "cinza" (do mês adjacente), navegamos
        // para esse mês — comportamento padrão de calendários mobile.
        val cellMonth = YearMonth.from(cell.date)
        val displayed = vm.monthState.value?.displayedMonth
        if (displayed != null && cellMonth != displayed) {
            vm.showMonth(cellMonth)
        }
        vm.selectDay(cell.date)
    }

    private fun onEventClicked(event: CalendarEvent) {
        when (event) {
            is CalendarEvent.ManagerMatch -> handleManagerMatchClick(event)
            is CalendarEvent.OtherMatch   -> handleOtherMatchClick(event)
            else -> showEventInfoDialog(event)
        }
    }

    /**
     * Clique numa partida do gerente:
     *  - Já jogada → mostra o placar.
     *  - Pendente E HOJE → abre o fluxo de pick & ban / simulação direta.
     *  - Pendente em OUTRO DIA → informativo (não dá pra jogar partida que
     *    não é hoje, e também não dá pra rejogar partida passada).
     */
    private fun handleManagerMatchClick(event: CalendarEvent.ManagerMatch) {
        if (event.played) {
            stylizedDialog(this)
                .setTitle(R.string.icon_match)
                .setMessage("${event.title}\n${event.homeScore} - ${event.awayScore}")
                .setPositiveButton(R.string.btn_ok, null).show()
            return
        }
        if (!isToday(event.date)) {
            // Partida não jogada que não é hoje (futura, ou eventualmente um
            // gap raro do passado). Só visualizar — sem fluxo interativo.
            showEventInfoDialog(event)
            return
        }
        stylizedDialog(this)
            .setTitle(event.title)
            .setMessage(R.string.dialog_pickban_question)
            .setPositiveButton(R.string.btn_do_pickban) { _, _ ->
                openPickBan(event)
            }
            .setNegativeButton(R.string.btn_skip_simulation) { _, _ ->
                launchSimulation(event.matchId)
            }.show()
    }

    /**
     * Clique numa partida de outros times:
     *  - Já jogada → placar.
     *  - Pendente E HOJE → oferece assistir (LiveMatchEngine simula).
     *  - Pendente em OUTRO DIA → informativo (sem assistir partida que ainda
     *    não aconteceu na linha do tempo do jogo, nem rever a posteriori).
     */
    private fun handleOtherMatchClick(event: CalendarEvent.OtherMatch) {
        if (event.played) {
            stylizedDialog(this)
                .setTitle(event.title)
                .setMessage("${event.homeScore} - ${event.awayScore}")
                .setPositiveButton(R.string.btn_ok, null).show()
            return
        }
        if (!isToday(event.date)) {
            showEventInfoDialog(event)
            return
        }
        stylizedDialog(this)
            .setTitle(event.title)
            .setMessage(R.string.dialog_watch_match_message)
            .setPositiveButton(R.string.btn_watch) { _, _ -> launchSimulation(event.matchId) }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    /** True se a data ISO do evento bate com [today] (data corrente da carreira). */
    private fun isToday(eventDateIso: String): Boolean =
        runCatching { LocalDate.parse(eventDateIso) == today }.getOrDefault(false)

    /** Eventos informativos (não-partida) só abrem um diálogo simples. */
    private fun showEventInfoDialog(event: CalendarEvent) {
        stylizedDialog(this)
            .setTitle(event.title)
            .setMessage(event.subtitle ?: event.category.label)
            .setPositiveButton(R.string.btn_ok, null).show()
    }

    private fun openPickBan(event: CalendarEvent.ManagerMatch) {
        val managerId = GameRepository.current().managerTeamId
        // Para encontrar o opponentId precisamos olhar a partida no estado;
        // o event guarda só o nome do oponente. Buscamos pelo matchId.
        val match = GameRepository.current().matches.find { it.id == event.matchId } ?: return
        val opponentId = if (match.homeTeamId == managerId) match.awayTeamId else match.homeTeamId

        vm.pendingMatchId        = event.matchId
        vm.pendingMapNumber      = 1
        vm.pendingPlayerTeamId   = managerId
        vm.pendingOpponentTeamId = opponentId
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(this, PickBanActivity::class.java).apply {
                putExtra(PickBanActivity.EXTRA_PLAYER_TEAM_ID,   managerId)
                putExtra(PickBanActivity.EXTRA_OPPONENT_TEAM_ID, opponentId)
                putExtra(PickBanActivity.EXTRA_MATCH_ID,         event.matchId)
                putExtra(PickBanActivity.EXTRA_MAP_NUMBER,       1)
            },
            PickBanActivity.REQUEST_PICK_BAN
        )
    }

    private fun launchSimulation(matchId: String) {
        startActivity(Intent(this, MatchSimulationActivity::class.java)
            .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId))
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return

        val bluePicks = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_PICKS)?.toList() ?: return
        val redPicks  = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_PICKS)?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_BANS)?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_BANS)?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra(PickBanActivity.EXTRA_MAP_NUMBER, vm.pendingMapNumber)

        vm.handlePickBanResult(bluePicks, redPicks, blueBans, redBans, mapNum)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Resolve nome do mês via R.string para manter sob nosso controle (não depender do locale). */
    private fun monthName(month: YearMonth): String {
        val resId = resources.getIdentifier("cal_month_${month.monthValue}", "string", packageName)
        return if (resId != 0) getString(resId) else month.month.name
    }

    companion object {
        private const val GRID_COLUMNS = 7

        /** Mapa estável categoria → cor — usado pelos dois adapters. */
        @ColorRes
        fun categoryColor(category: CalendarEventCategory): Int = when (category) {
            CalendarEventCategory.MATCH_MANAGER -> R.color.cal_cat_match_manager
            CalendarEventCategory.MATCH_OTHER   -> R.color.cal_cat_match_other
            CalendarEventCategory.TRANSFER      -> R.color.cal_cat_transfer
            CalendarEventCategory.SPONSOR       -> R.color.cal_cat_sponsor
            CalendarEventCategory.FINANCE       -> R.color.cal_cat_finance
            CalendarEventCategory.SPLIT         -> R.color.cal_cat_split
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // ADAPTERS
    // ────────────────────────────────────────────────────────────────────

    /**
     * Adapter da grade do mês. Cada item é uma [CalendarDayCell] (dia).
     *
     * Mantém o `selectedDate` separadamente para conseguir atualizar **apenas**
     * a célula antiga e a nova quando o usuário troca de dia, sem reanimar
     * a grade inteira (notifyDataSetChanged seria visualmente destrutivo).
     */
    private class MonthGridAdapter(
        private val onClick: (CalendarDayCell) -> Unit
    ) : RecyclerView.Adapter<MonthGridAdapter.VH>() {

        private var items: List<CalendarDayCell> = emptyList()
        private var selectedDate: LocalDate? = null

        fun submit(newItems: List<CalendarDayCell>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun setSelected(date: LocalDate) {
            val previous = selectedDate
            selectedDate = date
            // Notifica só as 2 posições afetadas (antes e agora) para preservar animações.
            if (previous != null) {
                val idx = items.indexOfFirst { it.date == previous }
                if (idx >= 0) notifyItemChanged(idx)
            }
            val newIdx = items.indexOfFirst { it.date == date }
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: View       = v.findViewById(R.id.cal_cell_root)
            val tvDay: TextView  = v.findViewById(R.id.tv_day_number)
            val dots: List<View> = listOf(
                v.findViewById(R.id.dot_1),
                v.findViewById(R.id.dot_2),
                v.findViewById(R.id.dot_3),
                v.findViewById(R.id.dot_4)
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val cell = items[position]
            val ctx  = h.itemView.context
            val isSelected = cell.date == selectedDate

            h.tvDay.text = cell.dayOfMonth.toString()

            // Estado do fundo via state-list (state_selected + state_activated):
            h.root.isSelected  = isSelected
            h.root.isActivated = cell.isToday && !isSelected

            // Cor do texto: selecionado → preto sobre dourado; fora do mês →
            // cinza apagado; padrão → texto principal.
            val textColorRes = when {
                isSelected             -> R.color.cal_day_text_selected
                !cell.inDisplayedMonth -> R.color.cal_day_text_out_month
                else                   -> R.color.cal_day_text_in_month
            }
            h.tvDay.setTextColor(ContextCompat.getColor(ctx, textColorRes))
            // Alpha extra pra dias fora do mês (mesmo com cor cinza, fica mais sutil).
            h.itemView.alpha = if (cell.inDisplayedMonth) 1f else 0.5f

            // Pontos por categoria (até 4)
            val categoryList = cell.categories.toList()
            h.dots.forEachIndexed { i, dot ->
                if (i < categoryList.size) {
                    dot.visibility = View.VISIBLE
                    val color = ContextCompat.getColor(ctx, categoryColor(categoryList[i]))
                    dot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                } else {
                    dot.visibility = View.GONE
                }
            }

            h.itemView.setOnClickListener { onClick(cell) }
        }
    }

    /**
     * Adapter da lista de eventos do dia selecionado.
     *
     * Conhece a data atual da carreira ([todayIso]) porque o **chevron de
     * ação** só aparece para partidas pendentes do DIA DE HOJE. Partidas
     * pendentes de outros dias (futuras ou eventuais gaps do passado) são
     * apenas informativas — não se pode jogar/assistir adiantado nem rejogar.
     */
    private class DayEventsAdapter(
        private val onClick: (CalendarEvent) -> Unit
    ) : RecyclerView.Adapter<DayEventsAdapter.VH>() {

        private var items: List<CalendarEvent> = emptyList()
        private var todayIso: String = ""

        fun submit(newItems: List<CalendarEvent>) {
            items = newItems
            notifyDataSetChanged()
        }

        /**
         * Atualiza a referência de "hoje" usada para decidir a visibilidade
         * do chevron. Quando a data corrente muda (ex: usuário avançou um dia
         * em outra tela), a Activity chama este método para que partidas
         * que ANTES eram "hoje" parem de mostrar chevron, e a do novo dia
         * passe a mostrar.
         */
        fun setToday(today: LocalDate) {
            val newIso = today.toString()
            if (newIso == todayIso) return
            todayIso = newIso
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val bar: View          = v.findViewById(R.id.view_category_bar)
            val tvEmoji: TextView  = v.findViewById(R.id.tv_event_emoji)
            val tvTitle: TextView  = v.findViewById(R.id.tv_event_title)
            val tvSub: TextView    = v.findViewById(R.id.tv_event_subtitle)
            val ivAction: ImageView = v.findViewById(R.id.iv_action_chevron)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_event, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val event = items[position]
            val ctx   = h.itemView.context

            h.tvEmoji.text = event.category.emoji
            h.tvTitle.text = event.title
            h.tvSub.text   = event.subtitle.orEmpty()
            h.tvSub.visibility = if (event.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
            h.bar.setBackgroundColor(ContextCompat.getColor(ctx, categoryColor(event.category)))

            // Chevron de "ação" só aparece para partidas NÃO JOGADAS DO DIA ATUAL.
            // Restringe o fluxo de pick&ban / assistir à janela correta da linha
            // do tempo do jogo — não adianta o próximo BO3 nem rejoga o passado.
            val actionable = when (event) {
                is CalendarEvent.ManagerMatch -> !event.played && event.date == todayIso
                is CalendarEvent.OtherMatch   -> !event.played && event.date == todayIso
                else -> false
            }
            h.ivAction.visibility = if (actionable) View.VISIBLE else View.GONE

            h.itemView.setOnClickListener { onClick(event) }
        }
    }
}
