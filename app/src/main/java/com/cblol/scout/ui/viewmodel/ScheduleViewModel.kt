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

class ScheduleViewModel(
    private val getAllMatches: GetAllMatchesUseCase,
    private val savePickBanPlan: SavePickBanPlanUseCase,
    private val simulateMapWithPicks: SimulateMapWithPicksUseCase,
    private val updateSeriesState: UpdateSeriesStateUseCase,
    private val finalizeMatch: FinalizeMatchUseCase
) : ViewModel() {

    private val _matches = MutableLiveData<List<Match>>()
    val matches: LiveData<List<Match>> = _matches

    private val _event = MutableLiveData<ScheduleEvent>()
    val event: LiveData<ScheduleEvent> = _event

    var pendingMatchId: String = ""
    var pendingMapNumber: Int = 1
    var pendingPlayerTeamId: String = ""
    var pendingOpponentTeamId: String = ""

    fun loadMatches() {
        viewModelScope.launch {
            _matches.value = withContext(Dispatchers.IO) { getAllMatches() }
        }
    }

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

    fun refreshMatches() {
        viewModelScope.launch {
            _matches.value = withContext(Dispatchers.IO) { getAllMatches() }
        }
    }
}
