package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.data.Player
import com.cblol.scout.data.Standing
import com.cblol.scout.domain.usecase.*
import com.cblol.scout.game.BuyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferMarketViewModel(
    private val getMarketRoster: GetMarketRosterUseCase,
    private val buyPlayer: BuyPlayerUseCase,
    private val getMarketPrice: GetMarketPriceUseCase
) : ViewModel() {

    private val _players = MutableLiveData<List<Player>>()
    val players: LiveData<List<Player>> = _players

    private val _buyResult = MutableLiveData<BuyResult>()
    val buyResult: LiveData<BuyResult> = _buyResult

    var currentFilter: String = "ALL"

    fun load(role: String = currentFilter) {
        currentFilter = role
        viewModelScope.launch {
            _players.value = withContext(Dispatchers.IO) { getMarketRoster(role) }
        }
    }

    fun buy(playerId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { buyPlayer(playerId) }
            _buyResult.value = result
            if (result is BuyResult.Ok) load()
        }
    }

    fun priceOf(player: Player): Long = getMarketPrice(player)
}

class StandingsViewModel(
    private val getStandings: GetStandingsUseCase
) : ViewModel() {

    private val _standings = MutableLiveData<List<Standing>>()
    val standings: LiveData<List<Standing>> = _standings

    fun load() {
        viewModelScope.launch {
            _standings.value = withContext(Dispatchers.IO) { getStandings() }
        }
    }
}

class TeamSelectViewModel(
    private val hasSav: HasSaveUseCase,
    private val loadCareer: LoadCareerUseCase,
    private val startNewCareer: StartNewCareerUseCase,
    private val clearCareer: ClearCareerUseCase
) : ViewModel() {

    private val _hasSave = MutableLiveData<Boolean>()
    val hasSave: LiveData<Boolean> = _hasSave

    private val _careerStarted = MutableLiveData<Boolean>()
    val careerStarted: LiveData<Boolean> = _careerStarted

    fun checkSave() {
        _hasSave.value = hasSav.invoke()
    }

    fun startCareer(managerName: String, teamId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { startNewCareer(managerName, teamId) }
            _careerStarted.value = true
        }
    }

    fun clearAndRestart() {
        viewModelScope.launch(Dispatchers.IO) { clearCareer() }
    }
}
