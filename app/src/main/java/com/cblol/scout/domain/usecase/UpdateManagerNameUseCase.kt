package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.game.GameRepository

/**
 * Atualiza o nome do treinador no GameState e persiste a mudança.
 *
 * SOLID:
 * - **SRP**: Foca apenas na atualização do campo managerName e persistência.
 */
class UpdateManagerNameUseCase(private val context: Context) {
    operator fun invoke(newName: String) {
        val gs = GameRepository.load(context) ?: return
        
        // Atualiza no objeto em memória (se carregado) e na persistência
        val updatedGs = gs.copy(managerName = newName.trim())
        GameRepository.save(context, updatedGs)
        
        // O GameRepository.current() pode estar cacheando o estado antigo, 
        // mas o save() acima atualiza o estado interno do GameRepository se for o mesmo objeto.
        // Como o GameState é um data class e usamos copy(), o objeto é novo.
        // O GameRepository.save atualiza seu cache interno 'state'.
    }
}
