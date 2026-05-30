package com.cblol.scout.game.engine

import android.content.Context
import com.cblol.scout.data.BondTier
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.AcademyService
import com.cblol.scout.domain.usecase.IncomingOfferService
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.domain.usecase.NewsService
import com.cblol.scout.domain.usecase.PlayerBondService
import com.cblol.scout.domain.usecase.ScoutingService
import com.cblol.scout.game.AdvanceReport
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.TransferMarket
import com.cblol.scout.util.SecondDivisionGenerator
import java.time.LocalDate

/**
 * Processa o pacote de eventos diários que NÃO são partidas nem financeiros:
 *  - Detecção de transição de janela de transferência
 *  - Decay de moral + pedidos de transferência por insatisfação
 *  - Tick de scouting (avanço dos jogadores em observação)
 *  - Geração/expiração de ofertas de outros times
 *  - Evolução dos laços (química) entre os jogadores do elenco
 *  - Desenvolvimento da categoria de base
 *
 * Os efeitos financeiros (patrocínio, manutenções, folha) ficam no
 * [EconomyProcessor]. As partidas ficam no [MatchDaySimulator]. Esta classe
 * orquestra os ticks "humanos" do dia — moral, química, evolução dos atletas.
 *
 * Extraído do [com.cblol.scout.game.GameEngine] para isolar a coordenação dos
 * subsistemas de jogador/RH do orquestrador principal de calendário.
 */
internal object DailyTicksProcessor {

    /**
     * Executa todos os ticks "humanos" do dia [date], atualizando [gs] e
     * anotando os marcos relevantes em [report].
     *
     * **Ordem importa**: detectamos transição de janela ANTES do scouting/
     * ofertas porque alguns serviços leem o estado do mercado para decidir o
     * que fazer (gerar oferta só se aberto, por exemplo).
     */
    fun process(context: Context, gs: GameState, date: LocalDate, report: AdvanceReport) {
        val iso = date.toString()
        val previousDate = gs.currentDate
        gs.currentDate = iso

        // Janela de transferência
        TransferWindowDetector.detectAndLog(gs, previousDate, iso)

        // Lê o roster uma vez e passa para os subsistemas que precisam
        val roster = GameRepository.rosterOf(context, gs.managerTeamId)

        processMoraleAndTransferRequests(context, gs, roster, report)
        processScouting(context, gs)
        processIncomingOffers(context, gs, roster, report)
        processPlayerBonds(gs, roster)
        processAcademy(gs, report)
    }

    // ── Moral + pedidos de transferência ─────────────────────────────────

    private fun processMoraleAndTransferRequests(
        context: Context,
        gs: GameState,
        roster: List<Player>,
        report: AdvanceReport
    ) {
        val decayResult = MoraleService.applyDailyDecay(gs, roster)
        decayResult.transferRequests.forEach { playerId ->
            val player = roster.find { it.id == playerId } ?: return@forEach
            report.transferRequests += player.nome_jogo
            GameRepository.log(
                "MOOD",
                "${player.nome_jogo} pediu transferência por estar desmotivado."
            )
            // Cobertura de bastidores: pedido de transferência vira notícia.
            NewsService.reportTransferRequest(
                gs, player.nome_jogo, teamName(context, gs.managerTeamId)
            )
        }
    }

    // ── Scouting ─────────────────────────────────────────────────────────

    private fun processScouting(context: Context, gs: GameState) {
        val scoutTick = ScoutingService.tickDaily(gs)
        scoutTick.levelUps.forEach { up ->
            val playerName = resolveScoutedPlayerName(context, gs, up.playerId)
            val msg = if (up.newLevel >= ScoutingService.MAX_LEVEL) {
                "Scouting de $playerName concluído (nível ${up.newLevel})."
            } else {
                "Scouting de $playerName avançou para nível ${up.newLevel}."
            }
            GameRepository.log("SCOUT", msg)
        }
    }

    /**
     * Lookup do nome de jogador em scouting em todas as fontes possíveis:
     * snapshot (1ª div), free agents do CD, jogadores dos times da 2ª div
     * (modo começar de baixo) e promovidos da academia. Sem cobrir todas,
     * jogadores da 2ª div apareceriam como "id cru" no log.
     */
    private fun resolveScoutedPlayerName(context: Context, gs: GameState, playerId: String): String {
        return GameRepository.snapshot(context).jogadores.find { it.id == playerId }?.nome_jogo
            ?: SecondDivisionGenerator.generate().find { it.id == playerId }?.nome_jogo
            ?: gs.secondDivisionPlayers.find { it.id == playerId }?.nome_jogo
            ?: gs.promotedPlayers?.find { it.id == playerId }?.nome_jogo
            ?: playerId
    }

    // ── Ofertas recebidas ────────────────────────────────────────────────

    /**
     * Processa ofertas de compra de outros times num tick diário:
     *  1. Expira ofertas vencidas (por data ou mercado fechado).
     *  2. Gera novas ofertas se for dia de gerar e o mercado estiver aberto.
     */
    private fun processIncomingOffers(
        context: Context,
        gs: GameState,
        roster: List<Player>,
        report: AdvanceReport
    ) {
        IncomingOfferService.expireOffers(gs)

        val snapshot = GameRepository.snapshot(context)
        // Em carreira na 2ª divisão, as ofertas devem vir dos OUTROS times da
        // 2ª div (não de LOUD/paiN/etc, que ofereceriam valores absurdos para o
        // orçamento do CD). teamsForCurrentDivision resolve isso.
        val rivals = GameRepository.teamsForCurrentDivision(context)
        val result = IncomingOfferService.generateOffersIfDue(
            state = gs,
            snapshot = snapshot,
            roster = roster,
            marketPriceOf = { TransferMarket.marketPriceOf(it) },
            rivalTeams = rivals
        )
        result.newOffers.forEach { offer ->
            report.incomingOffers += offer.playerName
            GameRepository.log(
                "TRANSFER",
                "💰 ${offer.fromTeamName} ofereceu R$ ${"%,d".format(offer.amountBrl)} por ${offer.playerName}."
            )
        }
    }

    // ── Laços (química) ──────────────────────────────────────────────────

    /**
     * Evolui os laços entre os jogadores e loga marcos relevantes (parceria
     * forte / rivalidade tóxica). Idempotente por data via
     * [GameState.lastBondTickDate].
     */
    private fun processPlayerBonds(gs: GameState, roster: List<Player>) {
        val milestones = PlayerBondService.tickDaily(gs, roster)
        milestones.forEach { m ->
            val nameA = roster.find { it.id == m.playerAId }?.nome_jogo ?: m.playerAId
            val nameB = roster.find { it.id == m.playerBId }?.nome_jogo ?: m.playerBId
            when (m.tier) {
                BondTier.BONDED -> {
                    GameRepository.log(
                        "MOOD",
                        "🔥 $nameA e $nameB formaram uma parceria forte dentro e fora do jogo."
                    )
                    NewsService.reportStrongBond(gs, nameA, nameB)
                }
                BondTier.TOXIC -> {
                    GameRepository.log(
                        "MOOD",
                        "☠️ A relação entre $nameA e $nameB azedou de vez — clima pesado no vestiário."
                    )
                    NewsService.reportLockerRoomCrisis(gs, nameA, nameB)
                }
                else -> return@forEach
            }
        }
    }

    // ── Categoria de base ────────────────────────────────────────────────

    /**
     * Desenvolve prospects e recruta novos talentos periodicamente.
     * Idempotente por data via [GameState.lastAcademyTickDate].
     */
    private fun processAcademy(gs: GameState, report: AdvanceReport) {
        val result = AcademyService.tickDaily(gs)
        result.readyNow.forEach { p ->
            report.academyReady += p.nome
            GameRepository.log(
                "ACADEMY",
                "🌟 ${p.nome} (${p.role}) atingiu o nível para subir ao elenco principal!"
            )
        }
        result.recruited.forEach { p ->
            GameRepository.log(
                "ACADEMY",
                "🌱 Novo talento na base: ${p.nome} (${p.role}, ${p.idade} anos)."
            )
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Resolve o nome de um time pelo id consultando ambas as divisões. */
    private fun teamName(context: Context, teamId: String): String {
        GameRepository.snapshot(context).times.find { it.id == teamId }?.let { return it.nome }
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return teamId
        return gs.secondDivisionTeams.find { it.id == teamId }?.nome ?: teamId
    }
}
