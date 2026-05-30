package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.SponsorContract
import com.cblol.scout.data.SponsorOffer
import com.cblol.scout.domain.usecase.AcceptSponsorOfferUseCase
import com.cblol.scout.domain.usecase.CancelSponsorContractUseCase
import com.cblol.scout.domain.usecase.GetSponsorsStateUseCase
import com.cblol.scout.domain.usecase.RejectSponsorOfferUseCase
import com.cblol.scout.domain.usecase.SponsorsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.SponsorsActivity].
 *
 * Estado da tela (contratos ativos + ofertas + total semanal + contagem) num
 * único [LiveData]. Os 3 fluxos transacionais (aceitar oferta / recusar oferta /
 * cancelar contrato) viram métodos públicos e emitem eventos para a Activity
 * disparar os diálogos de resultado.
 */
class SponsorsViewModel(
    private val getState: GetSponsorsStateUseCase,
    private val acceptOffer: AcceptSponsorOfferUseCase,
    private val rejectOffer: RejectSponsorOfferUseCase,
    private val cancelContract: CancelSponsorContractUseCase
) : ViewModel() {

    private val _state = MutableLiveData<SponsorsUiState>()
    val state: LiveData<SponsorsUiState> = _state

    private val _events = MutableLiveData<Event<SponsorsEvent>>()
    val events: LiveData<Event<SponsorsEvent>> = _events

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun accept(offer: SponsorOffer) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { acceptOffer(offer.sponsor.id) }
            _events.value = Event(SponsorsEvent.AcceptDone(offer, result))
            if (result is AcceptSponsorOfferUseCase.Result.Ok) refresh()
        }
    }

    fun reject(offer: SponsorOffer) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { rejectOffer(offer.sponsor.id) }
            refresh()
        }
    }

    fun cancel(contract: SponsorContract) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { cancelContract(contract.sponsor.id) }
            _events.value = Event(SponsorsEvent.CancelDone(contract, result))
            refresh()
        }
    }
}

sealed class SponsorsEvent {
    data class AcceptDone(val offer: SponsorOffer, val result: AcceptSponsorOfferUseCase.Result) : SponsorsEvent()
    data class CancelDone(val contract: SponsorContract, val result: CancelSponsorContractUseCase.Result) : SponsorsEvent()
}
