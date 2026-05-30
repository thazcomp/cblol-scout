package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.BankLoan
import com.cblol.scout.data.LoanOffer
import com.cblol.scout.domain.usecase.BankUiState
import com.cblol.scout.domain.usecase.GetBankStateUseCase
import com.cblol.scout.domain.usecase.PayOffLoanUseCase
import com.cblol.scout.domain.usecase.TakeLoanUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.BankActivity].
 *
 * Expõe o estado bancário (saúde, crédito, ofertas, dívidas) num único
 * [LiveData] e eventos para os dois fluxos transacionais (contratar / quitar).
 * A Activity só observa e dispara diálogos — todas as regras de juros,
 * parcelas, limite de crédito e validações estão nos UseCases.
 */
class BankViewModel(
    private val getState: GetBankStateUseCase,
    private val takeLoan: TakeLoanUseCase,
    private val payOff: PayOffLoanUseCase
) : ViewModel() {

    private val _state = MutableLiveData<BankUiState>()
    val state: LiveData<BankUiState> = _state

    private val _events = MutableLiveData<Event<BankEvent>>()
    val events: LiveData<Event<BankEvent>> = _events

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }

    fun contractLoan(offer: LoanOffer) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { takeLoan(offer) }
            _events.value = Event(BankEvent.TakeLoanDone(offer, result))
            if (result is TakeLoanUseCase.Result.Ok) refresh()
        }
    }

    fun payOffLoan(loan: BankLoan) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { payOff(loan.id) }
            _events.value = Event(BankEvent.PayOffDone(loan, result))
            if (result is PayOffLoanUseCase.Result.Ok) refresh()
        }
    }
}

sealed class BankEvent {
    data class TakeLoanDone(val offer: LoanOffer, val result: TakeLoanUseCase.Result) : BankEvent()
    data class PayOffDone(val loan: BankLoan, val result: PayOffLoanUseCase.Result) : BankEvent()
}
