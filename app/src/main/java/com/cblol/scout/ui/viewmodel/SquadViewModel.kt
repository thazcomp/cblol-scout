package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.*
import com.cblol.scout.game.PromoteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SquadViewModel(
    private val getStarters: GetStartersUseCase,
    private val getReserves: GetReservesUseCase,
    private val swapStarters: SwapStartersUseCase,
    private val promoteFromBench: PromoteFromBenchUseCase
) : ViewModel() {

    private val _starters = MutableLiveData<List<Player>>()
    val starters: LiveData<List<Player>> = _starters

    private val _reserves = MutableLiveData<List<Player>>()
    val reserves: LiveData<List<Player>> = _reserves

    private val _swapResult = MutableLiveData<Boolean>()
    val swapResult: LiveData<Boolean> = _swapResult

    private val _promoteResult = MutableLiveData<PromoteResult>()
    val promoteResult: LiveData<PromoteResult> = _promoteResult

    fun load() {
        viewModelScope.launch {
            _starters.value = withContext(Dispatchers.IO) { getStarters() }
            _reserves.value = withContext(Dispatchers.IO) { getReserves() }
        }
    }

    fun swap(starterId: String, replacementId: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { swapStarters(starterId, replacementId) }
            _swapResult.value = ok
            if (ok) load()
        }
    }

    fun promote(reserveId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { promoteFromBench(reserveId) }
            _promoteResult.value = result
            load()
        }
    }
}
