package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.data.TrainingType
import com.cblol.scout.domain.usecase.GetTrainingStateUseCase
import com.cblol.scout.domain.usecase.RunTrainingUseCase
import com.cblol.scout.domain.usecase.TrainingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.TrainingActivity].
 *
 * Estado contém orçamento atual + mapa de disponibilidade por tipo (Available
 * / OnCooldown / InsufficientFunds) + histórico das sessões realizadas.
 *
 * O método [runTraining] avança o calendário (rodando partidas e eventos do
 * GameEngine no caminho) e aplica os efeitos do treino — toda essa coreografia
 * vive no [RunTrainingUseCase], a Activity só observa o evento de conclusão.
 *
 * Inclui um [LiveData] de "executando" para a UI bloquear/desbloquear o
 * recycler durante a operação (era feito direto com `alpha`/`isClickable` na
 * Activity antes).
 */
class TrainingViewModel(
    private val getState: GetTrainingStateUseCase,
    private val runTraining: RunTrainingUseCase
) : ViewModel() {

    private val _state = MutableLiveData<TrainingUiState>()
    val state: LiveData<TrainingUiState> = _state

    private val _running = MutableLiveData(false)
    val running: LiveData<Boolean> = _running

    private val _events = MutableLiveData<Event<TrainingEvent>>()
    val events: LiveData<Event<TrainingEvent>> = _events

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun startTraining(type: TrainingType) {
        viewModelScope.launch {
            _running.value = true
            val session = withContext(Dispatchers.IO) { runTraining(type) }
            _running.value = false
            if (session != null) {
                _events.value = Event(TrainingEvent.TrainingDone(session))
            }
            refresh()
        }
    }
}

sealed class TrainingEvent {
    data class TrainingDone(val session: TrainingSession) : TrainingEvent()
}
