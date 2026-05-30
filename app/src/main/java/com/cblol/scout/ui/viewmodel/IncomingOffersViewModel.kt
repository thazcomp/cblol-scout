package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.IncomingTransferOffer
import com.cblol.scout.domain.usecase.AcceptIncomingOfferUseCase
import com.cblol.scout.domain.usecase.GetIncomingOffersStateUseCase
import com.cblol.scout.domain.usecase.IncomingOffersUiState
import com.cblol.scout.domain.usecase.RejectIncomingOfferUseCase
import com.cblol.scout.game.SellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.IncomingOffersActivity].
 *
 * Mantém a lista de ofertas + status do mercado num [LiveData] e expõe
 * eventos para o resultado de aceitar/recusar. A regra de venda
 * (override de time, multa por encerramento, cobertura jornalística) vive
 * no [com.cblol.scout.game.TransferMarket], acessado via UseCases.
 */
class IncomingOffersViewModel(
    private val getState: GetIncomingOffersStateUseCase,
    private val accept: AcceptIncomingOfferUseCase,
    private val reject: RejectIncomingOfferUseCase
) : ViewModel() {

    private val _state = MutableLiveData<IncomingOffersUiState>()
    val state: LiveData<IncomingOffersUiState> = _state

    private val _events = MutableLiveData<Event<IncomingOffersEvent>>()
    val events: LiveData<Event<IncomingOffersEvent>> = _events

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun acceptOffer(offer: IncomingTransferOffer) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { accept(offer.id) }
            _events.value = Event(IncomingOffersEvent.AcceptDone(offer, result))
            refresh()
        }
    }

    fun rejectOffer(offer: IncomingTransferOffer) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { reject(offer.id) }
            refresh()
        }
    }
}

sealed class IncomingOffersEvent {
    data class AcceptDone(val offer: IncomingTransferOffer, val result: SellResult) : IncomingOffersEvent()
}
