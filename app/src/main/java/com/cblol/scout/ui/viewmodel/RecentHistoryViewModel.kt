package com.cblol.scout.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cblol.scout.domain.usecase.RecentHistoryAggregator
import com.cblol.scout.domain.usecase.RecentHistoryCategory
import com.cblol.scout.domain.usecase.RecentHistoryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel do [com.cblol.scout.ui.RecentHistoryDialog].
 *
 * Mantém:
 *  - [events]: lista filtrada para exibição.
 *  - [activeFilters]: conjunto de categorias ligadas. Vazio = todas.
 *  - [counts]: contagem por categoria (texto pequeno em cada chip).
 *
 * O VM é simples porque o agregador é puro. A única responsabilidade extra é
 * gerenciar o conjunto de filtros e recalcular `events` a cada toggle.
 *
 * **SOLID:**
 *  - **SRP**: orquestra estado da UI; não conhece persistência ou regras.
 *  - **DIP**: depende do agregador (UseCase) — substituível em testes.
 */
class RecentHistoryViewModel(
    private val aggregator: RecentHistoryAggregator
) : ViewModel() {

    private val _events = MutableLiveData<List<RecentHistoryEvent>>(emptyList())
    val events: LiveData<List<RecentHistoryEvent>> = _events

    private val _activeFilters = MutableLiveData<Set<RecentHistoryCategory>>(emptySet())
    val activeFilters: LiveData<Set<RecentHistoryCategory>> = _activeFilters

    private val _counts = MutableLiveData<Map<RecentHistoryCategory, Int>>(emptyMap())
    val counts: LiveData<Map<RecentHistoryCategory, Int>> = _counts

    /** Carrega/recarrega o histórico com os filtros atuais. */
    fun load() {
        val filters = _activeFilters.value.orEmpty()
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { aggregator() }  // sem filtro pra contagem total
            _counts.value = all.groupingBy { it.category }.eachCount()
            _events.value = if (filters.isEmpty()) all
                            else all.filter { it.category in filters }
        }
    }

    /**
     * Alterna um filtro. Se já estava ligado, desliga; se não estava, liga.
     * Conjunto vazio = "mostrar tudo" (sem filtro).
     */
    fun toggleFilter(category: RecentHistoryCategory) {
        val current = _activeFilters.value.orEmpty()
        _activeFilters.value =
            if (category in current) current - category
            else current + category
        load()
    }

    /** Limpa todos os filtros — volta a mostrar tudo. */
    fun clearFilters() {
        _activeFilters.value = emptySet()
        load()
    }
}
