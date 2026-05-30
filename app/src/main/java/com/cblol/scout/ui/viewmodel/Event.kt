package com.cblol.scout.ui.viewmodel

/**
 * Wrapper para eventos one-shot expostos via [androidx.lifecycle.LiveData].
 *
 * Resolve o problema clássico de "LiveData re-entrega o último valor ao
 * observar de novo" — útil para diálogos, navegações e mensagens que devem
 * disparar UMA vez por emissão e não ser repetidos em rotação de tela.
 *
 * Uso típico no Activity:
 * ```
 * vm.events.observe(this) { event ->
 *     event.consume()?.let { showDialog(it) }
 * }
 * ```
 *
 * Extraído como utilitário próprio para reuso por todos os ViewModels novos
 * (AcademyViewModel, BankViewModel, IncomingOffersViewModel, etc.) sem
 * cada um reinventar.
 */
class Event<out T>(private val content: T) {
    private var handled = false

    /** Devolve o conteúdo na primeira chamada e null em todas as seguintes. */
    fun consume(): T? {
        if (handled) return null
        handled = true
        return content
    }

    /** Espia sem consumir (raro — útil para debug). */
    fun peek(): T = content
}
