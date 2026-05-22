package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.CoachProgressionService
import com.cblol.scout.domain.usecase.ContractService
import com.cblol.scout.domain.usecase.MoraleService

/**
 * Lógica do mercado de transferências.
 *
 * Cada jogador tem um "preço de mercado" calculado a partir do overall e do salário:
 *   preço = (salario_mensal × 12) × multiplicador_overall
 * Onde o multiplicador cresce com o overall — jogador 90+ vale ~2.4x salário anual,
 * jogador 50- vale ~0.6x.
 */
object TransferMarket {

    fun marketPriceOf(player: Player): Long {
        val annual = (player.contrato.salario_mensal_estimado_brl ?: 0L) * 12
        val mult = when {
            player.overallRating() >= 85 -> 2.4
            player.overallRating() >= 75 -> 1.8
            player.overallRating() >= 65 -> 1.2
            player.overallRating() >= 55 -> 0.85
            else -> 0.6
        }
        return (annual * mult).toLong()
    }

    /**
     * Sale do meu jogador: recebe o preço, jogador vai pra um time aleatório de outro.
     *
     * Política de venda:
     *  - Jogador **reserva** → pode vender sem restrição (libera roster de excedente)
     *  - Jogador **titular COM** reserva da mesma role no roster → vende; o
     *    SquadManager promove a reserva automaticamente
     *  - Jogador **titular SEM** reserva da role → venda permitida com
     *    [SellResult.WarningRequired]; o jogador precisa confirmar de novo. O time
     *    fica desfalcado até contratar substituto.
     *
     * Para confirmar a venda mesmo sem reserva, chame [sellPlayer] com
     * `force = true`.
     */
    fun sellPlayer(
        context: Context,
        playerId: String,
        force: Boolean = false
    ): SellResult {
        val gs = GameRepository.current()
        val snap = GameRepository.snapshot(context)

        // Mercado fechado fora das janelas de transferência: bloqueia a venda.
        if (!com.cblol.scout.domain.usecase.TransferWindowService.isMarketOpen(gs)) {
            return SellResult.Error(marketClosedMessage(gs))
        }

        val roster = GameRepository.rosterOf(context, gs.managerTeamId)
        val player = roster.find { it.id == playerId }
            ?: return SellResult.Error("Jogador não está no seu elenco")

        // Avisa se vai deixar o roster sem titular daquela role e não é confirmação forçada
        if (player.titular && !force) {
            val reserve = roster
                .filter { !it.titular && it.role == player.role }
                .maxByOrNull { it.overallRating() }

            if (reserve == null) {
                return SellResult.WarningRequired(
                    "${player.nome_jogo} é o único titular de ${player.role} e não há reserva. " +
                    "Ao vender, o time ficará desfalcado até contratar substituto. Confirma?"
                )
            }
        }

        // Encerramento antecipado: paga multa se o contrato ainda está ativo
        val terminationCost = ContractService.earlyTerminationCost(gs, player)
        val price = marketPriceOf(player)

        val otherTeams = snap.times.filter { it.id != gs.managerTeamId }
        val newTeam = otherTeams.random()

        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(newTeamId = newTeam.id, transferredOn = gs.currentDate, titular = false)
        }
        // Recebe o preço mas paga eventual encerramento antecipado pendente
        gs.budget += price
        if (terminationCost > 0) {
            gs.budget -= terminationCost
            GameRepository.log(
                "CONTRACT",
                "Multa por encerramento antecipado: R$ ${"%,d".format(terminationCost)}"
            )
        }
        CoachProgressionService.recordSell(gs.coachProfile, price)
        MoraleService.recordPlayerSold(gs, playerId)
        MoraleService.clearTransferRequest(gs, playerId)
        GameRepository.log(
            "TRANSFER",
            "${player.nome_jogo} vendido para ${newTeam.nome} por R$ ${"%,d".format(price)}"
        )
        GameRepository.save(context)

        // Validação automática: promove reserva se necessário
        SquadManager.validateAndFixRoster(context)

        return SellResult.Ok(price, newTeam.nome, terminationCost)
    }

    /** Compra: paga o preço, jogador vem para o meu time como reserva. */
    fun buyPlayer(context: Context, playerId: String): BuyResult {
        val gs = GameRepository.current()

        // Mercado fechado fora das janelas de transferência: bloqueia a compra.
        if (!com.cblol.scout.domain.usecase.TransferWindowService.isMarketOpen(gs)) {
            return BuyResult.Error(marketClosedMessage(gs))
        }

        val player = GameRepository.marketRoster(context).find { it.id == playerId }
            ?: return BuyResult.Error("Jogador não disponível no mercado")

        val price = marketPriceOf(player)
        if (gs.budget < price) {
            return BuyResult.Error("Orçamento insuficiente. Falta R$ ${"%,d".format(price - gs.budget)}")
        }

        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(newTeamId = gs.managerTeamId, transferredOn = gs.currentDate, titular = false)
        }
        gs.budget -= price
        CoachProgressionService.recordHire(gs.coachProfile, price)
        MoraleService.recordPlayerHired(gs, playerId)
        GameRepository.log(
            "TRANSFER",
            "${player.nome_jogo} contratado por R$ ${"%,d".format(price)}"
        )
        GameRepository.save(context)

        // Validação automática: se tiver vaga de titular, promove o novo jogador
        SquadManager.validateAndFixRoster(context)

        return BuyResult.Ok(price)
    }

    /**
     * Renegocia contrato do jogador: delega ao [ContractService] que avalia
     * a proposta considerando moral, idade, overall e contexto do contrato.
     *
     * Diferente da implementação anterior (que tinha uma regra simples
     * "95% do salário atual"), agora chama [ContractService.evaluateOffer] que
     * faz uma avaliação realista — jovens prospects podem recusar mesmo
     * propostas acima do atual, e veteranos podem aceitar reduções.
     *
     * Retorna true se aceitou; mensagem detalhada vai para o log.
     */
    fun renegotiateContract(
        context: Context,
        playerId: String,
        newMonthlySalary: Long,
        newEndDate: String,
        signingBonus: Long = 0L,
        durationMonths: Int = 12
    ): Boolean {
        val gs = GameRepository.current()
        val player = GameRepository.rosterOf(context, gs.managerTeamId)
            .find { it.id == playerId } ?: return false

        val result = ContractService.evaluateOffer(
            state = gs,
            player = player,
            offeredMonthlySalary = newMonthlySalary,
            offeredDurationMonths = durationMonths,
            signingBonus = signingBonus
        )

        return when (result) {
            is ContractService.OfferResult.Accepted -> {
                ContractService.applyAcceptedOffer(gs, player, newMonthlySalary, newEndDate, signingBonus)
                CoachProgressionService.recordRenew(gs.coachProfile)
                MoraleService.recordContractRenewed(gs, playerId)
                MoraleService.clearTransferRequest(gs, playerId)
                GameRepository.log("CONTRACT", result.message)
                GameRepository.save(context)
                true
            }
            is ContractService.OfferResult.Rejected -> {
                GameRepository.log("CONTRACT", result.reason)
                GameRepository.save(context)
                false
            }
        }
    }

    /** Promove ou rebaixa jogador (titular/reserva). */
    fun toggleStarter(context: Context, playerId: String) {
        val gs = GameRepository.current()
        val player = GameRepository.rosterOf(context, gs.managerTeamId)
            .find { it.id == playerId } ?: return
        GameRepository.updateOverride(playerId) { ov ->
            ov.copy(titular = !player.titular)
        }
        val newStatus = if (!player.titular) "titular" else "reserva"
        // Moral: subir como titular motiva; descer pra reserva desmotiva
        if (!player.titular) MoraleService.recordBecameStarter(gs, playerId)
        else                 MoraleService.recordBecameReserve(gs, playerId)
        GameRepository.log(
            "SQUAD",
            "${player.nome_jogo} agora é $newStatus"
        )
        GameRepository.save(context)

        // Validação automática: garante que há sempre 5 titulares
        SquadManager.validateAndFixRoster(context)
    }

    /**
     * Monta a mensagem de "mercado fechado" mostrada quando o jogador tenta
     * comprar/vender fora de uma janela de transferência. Inclui quando a
     * próxima janela abre, para orientar o jogador.
     */
    private fun marketClosedMessage(gs: com.cblol.scout.data.GameState): String {
        val status = com.cblol.scout.domain.usecase.TransferWindowService.statusMessage(gs)
        return "O mercado de transferências está fechado no momento.\n\n$status"
    }
}

sealed class SellResult {
    data class Ok(val price: Long, val toTeam: String, val terminationFee: Long = 0L) : SellResult()
    /** Venda válida mas com risco — UI deve confirmar com o usuário antes de chamar com force=true. */
    data class WarningRequired(val msg: String) : SellResult()
    data class Error(val msg: String) : SellResult()
}

sealed class BuyResult {
    data class Ok(val price: Long) : BuyResult()
    data class Error(val msg: String) : BuyResult()
}
