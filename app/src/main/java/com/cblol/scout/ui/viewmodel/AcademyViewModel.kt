package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AcademyTier
import com.cblol.scout.domain.usecase.AcademyUiState
import com.cblol.scout.domain.usecase.EvaluateProspectUseCase
import com.cblol.scout.domain.usecase.GetAcademyStateUseCase
import com.cblol.scout.domain.usecase.PromoteProspectUseCase
import com.cblol.scout.domain.usecase.RecruitProspectUseCase
import com.cblol.scout.domain.usecase.ReleaseProspectUseCase
import com.cblol.scout.domain.usecase.UpgradeAcademyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.AcademyActivity].
 *
 * Mantém o estado da tela (tier + prospects + capacidade) em um único
 * [LiveData] e expõe eventos one-shot ([Event]) para a Activity disparar
 * diálogos de sucesso/erro sem que a regra de negócio precise conhecer
 * o `Context` Android.
 *
 * **Princípios:**
 *  - **SRP**: regra de RH/finanças nos UseCases; UI orquestra observação.
 *  - **DIP**: depende só de UseCases via construtor (Koin injeta).
 *  - **Lifecycle-safe**: `_events` é consumido com [Event.consume] para não
 *    re-disparar diálogos em rotação de tela.
 */
class AcademyViewModel(
    private val getState: GetAcademyStateUseCase,
    private val recruit: RecruitProspectUseCase,
    private val evaluate: EvaluateProspectUseCase,
    private val promote: PromoteProspectUseCase,
    private val release: ReleaseProspectUseCase,
    private val upgrade: UpgradeAcademyUseCase
) : ViewModel() {

    private val _state = MutableLiveData<AcademyUiState>()
    val state: LiveData<AcademyUiState> = _state

    private val _events = MutableLiveData<Event<AcademyEvent>>()
    val events: LiveData<Event<AcademyEvent>> = _events

    /** Recarrega o estado da tela a partir do GameRepository. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun recruitProspect() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { recruit() }
            _events.value = Event(AcademyEvent.RecruitDone(result))
            if (result is RecruitProspectUseCase.Result.Ok) refresh()
        }
    }

    fun evaluateProspect(prospect: AcademyProspect) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { evaluate(prospect.id) }
            _events.value = Event(AcademyEvent.EvaluateDone(prospect, result))
            if (result is EvaluateProspectUseCase.Result.Ok) refresh()
        }
    }

    fun promoteProspect(prospect: AcademyProspect) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { promote(prospect.id) }
            _events.value = Event(AcademyEvent.PromoteDone(prospect, result))
            if (result is PromoteProspectUseCase.Result.Ok) refresh()
        }
    }

    fun releaseProspect(prospect: AcademyProspect) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { release(prospect.id) }
            refresh()
        }
    }

    fun upgradeAcademy(target: AcademyTier) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { upgrade() }
            _events.value = Event(AcademyEvent.UpgradeDone(target, result))
            if (result is UpgradeAcademyUseCase.Result.Ok) refresh()
        }
    }
}

/**
 * Eventos one-shot da [AcademyViewModel]. Cada um carrega o resultado tipado
 * do UseCase correspondente para que a Activity decida qual diálogo mostrar
 * (sucesso/erro/aviso) sem consultar o estado de novo.
 */
sealed class AcademyEvent {
    data class RecruitDone(val result: RecruitProspectUseCase.Result) : AcademyEvent()
    data class EvaluateDone(val prospect: AcademyProspect, val result: EvaluateProspectUseCase.Result) : AcademyEvent()
    data class PromoteDone(val prospect: AcademyProspect, val result: PromoteProspectUseCase.Result) : AcademyEvent()
    data class UpgradeDone(val target: AcademyTier, val result: UpgradeAcademyUseCase.Result) : AcademyEvent()
}
