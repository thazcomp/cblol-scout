package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.Match
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Após o pick & ban manual não há mais cálculo de vitória aqui.
 * O evento LaunchSimulation instrui a Activity a abrir o MatchSimulationActivity
 * com o plano já salvo no Match — o LiveMatchEngine cuida do resto.
 */
sealed class ScheduleEvent {
    /** Série finalizada via simulação automática (sem pick & ban manual). */
    data class ShowResult(val result: MatchResultData) : ScheduleEvent()

    /** Pick & ban manual concluído — abrir MatchSimulationActivity com o plano. */
    data class LaunchSimulation(val matchId: String) : ScheduleEvent()
}

/**
 * ViewModel da [com.cblol.scout.ui.ScheduleActivity], que agora apresenta o
 * calendário mensal completo (não só a lista de partidas).
 *
 * **Estado exposto:**
 *  - [matches] — lista bruta de partidas (legada — mantida para a Activity
 *    listar próximas partidas em um header opcional ou para testes).
 *  - [monthState] — estado completo do mês exibido: grade + eventos por dia.
 *  - [selectedDate] — dia clicado pelo usuário (filtra os eventos visíveis).
 *  - [selectedDayEvents] — derivado: eventos do dia selecionado.
 *  - [event] — eventos one-shot (compatibilidade com o fluxo de pick & ban).
 *
 * O calendário vive em ciclos de mês via [showMonth]/[nextMonth]/[previousMonth].
 * A seleção de dia é independente do mês exibido — o usuário pode navegar para
 * outros meses sem perder a seleção, e o `selectedDate` é apenas um filtro
 * para o painel inferior de eventos.
 */
class ScheduleViewModel(
    private val getAllMatches: GetAllMatchesUseCase,
    private val savePickBanPlan: SavePickBanPlanUseCase,
    private val simulateMapWithPicks: SimulateMapWithPicksUseCase,
    private val updateSeriesState: UpdateSeriesStateUseCase,
    private val finalizeMatch: FinalizeMatchUseCase,
    private val calendarAggregator: CalendarEventsAggregator
) : ViewModel() {

    // ── Estado legado (compatibilidade) ──────────────────────────────────

    private val _matches = MutableLiveData<List<Match>>()
    val matches: LiveData<List<Match>> = _matches

    private val _event = MutableLiveData<ScheduleEvent>()
    val event: LiveData<ScheduleEvent> = _event

    var pendingMatchId: String = ""
    var pendingMapNumber: Int = 1
    var pendingPlayerTeamId: String = ""
    var pendingOpponentTeamId: String = ""

    // ── Estado do calendário ─────────────────────────────────────────────

    private val _monthState = MutableLiveData<CalendarMonthState>()
    val monthState: LiveData<CalendarMonthState> = _monthState

    private val _selectedDate = MutableLiveData<LocalDate>()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    private val _selectedDayEvents = MutableLiveData<List<CalendarEvent>>(emptyList())
    val selectedDayEvents: LiveData<List<CalendarEvent>> = _selectedDayEvents

    // ── Inicialização ────────────────────────────────────────────────────

    /**
     * Carrega o mês corrente (do `gs.currentDate`) e pré-seleciona o dia atual,
     * que é o caso de uso mais comum ao abrir a tela.
     */
    fun initCalendar(currentDate: LocalDate) {
        showMonth(YearMonth.from(currentDate))
        selectDay(currentDate)
    }

    fun loadMatches() {
        viewModelScope.launch {
            _matches.value = withContext(Dispatchers.IO) { getAllMatches() }
        }
    }

    fun refreshMatches() {
        loadMatches()
        // Atualiza também o calendário se já há mês exibido — eventos podem
        // ter mudado (jogos jogados, ofertas recebidas, etc.).
        _monthState.value?.let { showMonth(it.displayedMonth) }
        _selectedDate.value?.let { selectDay(it) }
    }

    // ── Navegação de mês ─────────────────────────────────────────────────

    fun showMonth(month: YearMonth) {
        viewModelScope.launch {
            _monthState.value = withContext(Dispatchers.IO) { calendarAggregator(month) }
        }
    }

    fun nextMonth() {
        val current = _monthState.value?.displayedMonth ?: return
        showMonth(current.plusMonths(1))
    }

    fun previousMonth() {
        val current = _monthState.value?.displayedMonth ?: return
        showMonth(current.minusMonths(1))
    }

    // ── Seleção de dia ───────────────────────────────────────────────────

    fun selectDay(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch {
            _selectedDayEvents.value = withContext(Dispatchers.IO) {
                calendarAggregator.eventsOn(date)
            }
        }
    }

    // ── Pick & ban (legado) ──────────────────────────────────────────────

    /**
     * Chamado quando o pick & ban manual termina.
     * Salva o plano no Match e dispara LaunchSimulation para que a Activity
     * abra o MatchSimulationActivity — que usa o LiveMatchEngine com os
     * campeões escolhidos.
     */
    fun handlePickBanResult(
        bluePicks: List<String>,
        redPicks: List<String>,
        blueBans: List<String>,
        redBans: List<String>,
        mapNum: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            savePickBanPlan(
                pendingMatchId,
                PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans)
            )
            withContext(Dispatchers.Main) {
                _event.value = ScheduleEvent.LaunchSimulation(pendingMatchId)
            }
        }
    }
}
