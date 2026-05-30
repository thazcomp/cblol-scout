package com.cblol.scout.game.engine

import com.cblol.scout.data.GameState
import com.cblol.scout.domain.usecase.TransferWindowService
import com.cblol.scout.game.GameRepository

/**
 * Detecta transições de janela de transferência entre dois dias consecutivos
 * e registra o evento no log.
 *
 * Extraído do [com.cblol.scout.game.GameEngine] para isolar uma
 * responsabilidade específica: "observar o mercado e narrar quando ele abre /
 * fecha". Mantém o motor enxuto, respeita SRP.
 */
internal object TransferWindowDetector {

    /**
     * Detecta transição de janela de transferência entre dois dias consecutivos
     * e loga a abertura/fechamento para o jogador.
     *
     * Compara o estado do mercado em [previousDate] com o de [today]:
     *  - fechado → aberto: loga "mercado aberto" (com o tipo da janela)
     *  - aberto → fechado: loga "mercado fechado"
     *
     * Delega a detecção ao [TransferWindowService] para não duplicar a regra
     * de intervalos de janela.
     */
    fun detectAndLog(gs: GameState, previousDate: String, today: String) {
        val (transition, window) = TransferWindowService.detectTransition(gs, previousDate, today)
        when (transition) {
            TransferWindowService.WindowTransition.OPENED -> {
                val label = window?.kind?.label ?: "Transferências"
                GameRepository.log(
                    "TRANSFER",
                    "🟢 Janela de transferências ABERTA ($label). O mercado está disponível."
                )
            }
            TransferWindowService.WindowTransition.CLOSED -> {
                GameRepository.log(
                    "TRANSFER",
                    "🔴 Janela de transferências FECHADA. O mercado não aceita mais movimentações por enquanto."
                )
            }
            TransferWindowService.WindowTransition.NONE -> Unit
        }
    }
}
