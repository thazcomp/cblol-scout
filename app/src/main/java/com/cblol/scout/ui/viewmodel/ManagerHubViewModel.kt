package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.domain.usecase.HubState
import com.cblol.scout.domain.usecase.GetHubStateUseCase
import com.cblol.scout.domain.usecase.ClearCareerUseCase
import com.cblol.scout.domain.usecase.LoadCareerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagerHubViewModel(
    private val loadCareer: LoadCareerUseCase,
    private val getHubState: GetHubStateUseCase,
    private val clearCareer: ClearCareerUseCase
) : ViewModel() {

    private val _hubState = MutableLiveData<HubState>()
    val hubState: LiveData<HubState> = _hubState

    private val _sessionReady = MutableLiveData<Boolean>()
    val sessionReady: LiveData<Boolean> = _sessionReady

    fun init() {
        viewModelScope.launch {
            val gs = withContext(Dispatchers.IO) { loadCareer() }
            _sessionReady.value = (gs != null)
            if (gs != null) refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { getHubState() }
            _hubState.value = state
        }
    }

    fun clearCareer() {
        viewModelScope.launch(Dispatchers.IO) { clearCareer.invoke() }
    }
}
