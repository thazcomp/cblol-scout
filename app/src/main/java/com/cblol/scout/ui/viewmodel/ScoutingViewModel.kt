package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.CancelScoutingUseCase
import com.cblol.scout.domain.usecase.GetScoutingStateUseCase
import com.cblol.scout.domain.usecase.ScoutingUiState
import com.cblol.scout.domain.usecase.UpgradeScoutingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.ScoutingActivity].
 *
 * Resolve os jogadores em scouting (que podem vir do snapshot da 1ª divisão
 * OU do gerador procedural da 2ª divisão) dentro do [GetScoutingStateUseCase],
 * para a Activity não precisar mais conhecer essa lógica de "qual fonte".
 *
 * Expõe estado + eventos de cancelamento/upgrade do departamento.
 */
class ScoutingViewModel(
    private val getState: GetScoutingStateUseCase,
    private val cancelScouting: CancelScoutingUseCase,
    private val upgradeDept: UpgradeScoutingUseCase
) : ViewModel() {

    private val _state = MutableLiveData<ScoutingUiState>()
    val state: LiveData<ScoutingUiState> = _state

    private val _events = MutableLiveData<Event<ScoutingEvent>>()
    val events: LiveData<Event<ScoutingEvent>> = _events

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun cancel(player: Player) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cancelScouting(player.id) }
            _events.value = Event(ScoutingEvent.CancelDone(player))
            refresh()
        }
    }

    fun upgrade() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { upgradeDept() }
            _events.value = Event(ScoutingEvent.UpgradeDone(result))
            if (result is UpgradeScoutingUseCase.Result.Ok) refresh()
        }
    }
}

sealed class ScoutingEvent {
    data class CancelDone(val player: Player) : ScoutingEvent()
    data class UpgradeDone(val result: UpgradeScoutingUseCase.Result) : ScoutingEvent()
}
