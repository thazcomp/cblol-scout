package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.Match
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.SeriesState
import com.cblol.scout.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ScheduleEvent {
    data class ShowResult(val playerName: String, val opponentName: String, val pw: Int, val ow: Int) : ScheduleEvent()
    data class NextMap(val mapNum: Int, val playerWon: Boolean, val pw: Int, val ow: Int) : ScheduleEvent()
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

    // Estado da série em andamento
    var pendingMatchId: String = ""
    var pendingMapNumber: Int = 1
    var pendingPlayerTeamId: String = ""
    var pendingOpponentTeamId: String = ""

    fun loadMatches() {
        viewModelScope.launch {
            _matches.value = withContext(Dispatchers.IO) { getAllMatches() }
        }
    }

    fun handlePickBanResult(
        bluePicks: List<String>, redPicks: List<String>,
        blueBans: List<String>, redBans: List<String>,
        mapNum: Int,
        playerName: String, opponentName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            savePickBanPlan(pendingMatchId, PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans))

            val playerIsBlue  = (mapNum % 2 == 1)
            val playerPicks   = if (playerIsBlue) bluePicks else redPicks
            val opponentPicks = if (playerIsBlue) redPicks  else bluePicks

            val winner = simulateMapWithPicks(
                pendingPlayerTeamId, pendingOpponentTeamId,
                playerPicks, opponentPicks, playerIsBlue
            )
            val updated = updateSeriesState(pendingMatchId, winner == pendingPlayerTeamId)
            val pw = updated.playerWins
            val ow = updated.opponentWins

            withContext(Dispatchers.Main) {
                if (updated.isFinished) {
                    finalizeMatch(pendingMatchId, pendingPlayerTeamId, pw, ow)
                    _matches.value = getAllMatches()
                    _event.value = ScheduleEvent.ShowResult(playerName, opponentName, pw, ow)
                } else {
                    _event.value = ScheduleEvent.NextMap(mapNum, winner == pendingPlayerTeamId, pw, ow)
                }
            }
        }
    }
}
