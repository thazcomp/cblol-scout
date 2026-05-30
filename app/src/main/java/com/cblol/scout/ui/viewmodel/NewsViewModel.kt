package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.domain.usecase.GetNewsStateUseCase
import com.cblol.scout.domain.usecase.NewsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel da [com.cblol.scout.ui.NewsActivity].
 *
 * Apenas leitura — o feed é gerado pelos hooks do motor; a tela só lista.
 * Mesmo assim vale a separação: a Activity não precisa mais conhecer
 * `GameRepository.current()` nem `NewsService.feed`, e o estado em LiveData
 * sobrevive a rotações de tela sem reler do disco.
 */
class NewsViewModel(
    private val getState: GetNewsStateUseCase
) : ViewModel() {

    private val _state = MutableLiveData<NewsUiState>()
    val state: LiveData<NewsUiState> = _state

    fun refresh() {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { getState() }
        }
    }
}
