package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.Player
import com.cblol.scout.data.ScoutingDepartmentTier
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.SponsorContract
import com.cblol.scout.data.SponsorOffer
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.data.TrainingType
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.SecondDivisionGenerator

/**
 * UseCases das telas Patrocínios, Olheiros e Treinos.
 *
 * Mesmo padrão dos [MoreUseCases]: encapsulam a sequência
 * "regra → persistir → resultado tipado" para os ViewModels chamarem sem
 * conhecer detalhes de persistência.
 */

// ────────────────────────────────────────────────────────────────────────────
// PATROCÍNIOS
// ────────────────────────────────────────────────────────────────────────────

data class SponsorsUiState(
    val activeContracts: List<SponsorContract>,
    val availableOffers: List<SponsorOffer>,
    val totalWeeklyIncome: Long,
    val activeCount: Int,
    val maxActive: Int
)

class GetSponsorsStateUseCase(private val ctx: Context) {
    operator fun invoke(): SponsorsUiState {
        val gs = GameRepository.current()
        // Tenta gerar novas ofertas se for o dia (idempotente).
        SponsorService.generateOffersIfDue(gs)
        GameRepository.save(ctx)
        return SponsorsUiState(
            activeContracts = gs.activeSponsors ?: emptyList(),
            availableOffers = gs.availableSponsorOffers ?: emptyList(),
            totalWeeklyIncome = SponsorService.totalWeeklyIncomeFromSponsors(gs),
            activeCount = gs.activeSponsors?.size ?: 0,
            maxActive = SponsorService.MAX_ACTIVE_SPONSORS
        )
    }
}

class AcceptSponsorOfferUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val sponsor: Sponsor) : Result()
        object LimitReached : Result()
        object NotAvailable : Result()
    }
    operator fun invoke(sponsorId: String): Result {
        val gs = GameRepository.current()
        val offer = gs.availableSponsorOffers?.find { it.sponsor.id == sponsorId }
            ?: return Result.NotAvailable
        return when (SponsorService.acceptOffer(gs, sponsorId)) {
            SponsorService.AcceptResult.OK -> {
                GameRepository.save(ctx)
                Result.Ok(offer.sponsor)
            }
            SponsorService.AcceptResult.LIMIT_REACHED -> Result.LimitReached
            else -> Result.NotAvailable
        }
    }
}

class RejectSponsorOfferUseCase(private val ctx: Context) {
    operator fun invoke(sponsorId: String) {
        SponsorService.rejectOffer(GameRepository.current(), sponsorId)
        GameRepository.save(ctx)
    }
}

class CancelSponsorContractUseCase(private val ctx: Context) {
    data class Result(val penaltyPaid: Long)
    operator fun invoke(sponsorId: String): Result {
        val penalty = SponsorService.cancelContract(GameRepository.current(), sponsorId)
        GameRepository.save(ctx)
        return Result(penalty)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// OLHEIROS
// ────────────────────────────────────────────────────────────────────────────

data class ScoutingUiState(
    val tier: ScoutingDepartmentTier,
    val activeScouts: List<Player>,
    val maxConcurrent: Int,
    val budget: Long,
    val reputation: Int
)

class GetScoutingStateUseCase(private val ctx: Context) {
    operator fun invoke(): ScoutingUiState {
        val gs = GameRepository.current()
        val tier = ScoutingService.tier(gs)
        val ids = ScoutingService.activeScouts(gs)
        val players = ids.mapNotNull { resolve(it) }
        return ScoutingUiState(
            tier = tier,
            activeScouts = players,
            maxConcurrent = tier.maxConcurrentScouts,
            budget = gs.budget,
            reputation = gs.coachProfile.reputation
        )
    }

    /**
     * Resolve um playerId em qualquer fonte (snapshot da 1ª divisão OU
     * gerador procedural da 2ª divisão). Mesma lógica que vivia na Activity
     * antes — agora num único lugar.
     */
    private fun resolve(playerId: String): Player? {
        val fromSnap = GameRepository.snapshot(ctx).jogadores.find { it.id == playerId }
        if (fromSnap != null) return fromSnap
        return SecondDivisionGenerator.generate().find { it.id == playerId }
    }
}

class CancelScoutingUseCase(private val ctx: Context) {
    operator fun invoke(playerId: String) {
        ScoutingService.cancelScouting(GameRepository.current(), playerId)
        GameRepository.save(ctx)
    }
}

class UpgradeScoutingUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val newTier: ScoutingDepartmentTier) : Result()
        object AlreadyMax : Result()
        data class LowReputation(val required: Int, val current: Int) : Result()
        data class InsufficientFunds(val cost: Long, val budget: Long) : Result()
    }
    operator fun invoke(): Result {
        val gs = GameRepository.current()
        return when (ScoutingService.upgradeDepartment(gs)) {
            ScoutingService.UpgradeResult.OK -> {
                GameRepository.save(ctx)
                Result.Ok(ScoutingService.tier(gs))
            }
            ScoutingService.UpgradeResult.ALREADY_MAX -> Result.AlreadyMax
            ScoutingService.UpgradeResult.LOW_REPUTATION -> {
                val next = nextTierAfter(ScoutingService.tier(gs))
                Result.LowReputation(next?.minReputation ?: 0, gs.coachProfile.reputation)
            }
            ScoutingService.UpgradeResult.INSUFFICIENT_FUNDS -> {
                val next = nextTierAfter(ScoutingService.tier(gs))
                Result.InsufficientFunds(next?.upgradeCost ?: 0, gs.budget)
            }
        }
    }
    private fun nextTierAfter(current: ScoutingDepartmentTier): ScoutingDepartmentTier? =
        ScoutingDepartmentTier.values().getOrNull(current.ordinal + 1)
}

// ────────────────────────────────────────────────────────────────────────────
// TREINOS
// ────────────────────────────────────────────────────────────────────────────

data class TrainingUiState(
    val budget: Long,
    val availability: Map<TrainingType, TrainingService.Availability>,
    val history: List<TrainingSession>
)

class GetTrainingStateUseCase(private val ctx: Context) {
    operator fun invoke(): TrainingUiState {
        val gs = GameRepository.current()
        val avail = TrainingType.values().associateWith {
            TrainingService.checkAvailability(gs, it)
        }
        return TrainingUiState(
            budget = gs.budget,
            availability = avail,
            history = gs.trainingHistory ?: emptyList()
        )
    }
}

class RunTrainingUseCase(private val ctx: Context) {
    /**
     * Avança o calendário pelos dias do treino (rodando partidas/eventos do
     * GameEngine no caminho) e em seguida aplica os efeitos do treino no
     * roster titular. Retorna a sessão criada pelo TrainingService, ou `null`
     * se o treino não pôde ser executado.
     *
     * **Importante (correção do bug "treino sobrescreve dia de jogo"):** a
     * disponibilidade é checada ANTES de avançar qualquer dia. Se houver uma
     * partida do gerente dentro da janela do treino (ou cooldown / saldo
     * insuficiente), NÃO avançamos o calendário nem aplicamos efeitos —
     * senão o [GameEngine.advanceDays] auto-simularia a partida do gerente,
     * fazendo-o perder o pick & ban manual.
     */
    operator fun invoke(type: TrainingType): TrainingSession? {
        val gs = GameRepository.current()
        // Gate ANTES de mexer no calendário. Se não estiver disponível
        // (cooldown, sem dinheiro, ou partida na janela), aborta sem efeitos.
        if (TrainingService.checkAvailability(gs, type) !is TrainingService.Availability.Available) {
            return null
        }
        if (type.durationDays > 0) {
            GameEngine.advanceDays(ctx, type.durationDays)
        }
        val roster = GameRepository.rosterOf(ctx, gs.managerTeamId).filter { it.titular }
        TrainingService.runTraining(gs, type, roster)
        GameRepository.save(ctx)
        return gs.trainingHistory?.firstOrNull()
    }
}
