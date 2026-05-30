package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AcademyTier
import com.cblol.scout.data.BankLoan
import com.cblol.scout.data.FinancialHealth
import com.cblol.scout.data.IncomingTransferOffer
import com.cblol.scout.data.LoanOffer
import com.cblol.scout.data.NewsItem
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.game.SquadManager
import com.cblol.scout.game.TransferMarket

/**
 * UseCases das telas Academia, Banco, Propostas Recebidas e Notícias.
 *
 * Cada UseCase é uma operação pequena e focada que encapsula
 * `Service + GameRepository.save + log` num único método. As Activities
 * (via ViewModels) chamam o UseCase e recebem um resultado tipado, sem
 * conhecer detalhes de persistência.
 *
 * **Por que UseCases?** Concentram em um lugar a sequência "regra → persistir
 * → logar → notícia" — fora o ViewModel, fora a Activity. Quem chama
 * `PromoteProspectUseCase` só precisa saber se deu certo; não importa quais
 * collateral effects (NewsService, log, save) precisam acontecer.
 *
 * **SOLID:**
 *  - **SRP**: um UseCase = uma operação.
 *  - **DIP**: dependem de Services puros + GameRepository. Sem Android (exceto
 *    Context para `applicationContext` em save/log).
 */

// ────────────────────────────────────────────────────────────────────────────
// ACADEMIA
// ────────────────────────────────────────────────────────────────────────────

/** Snapshot do estado da academia para a UI consumir sem reler 5 services. */
data class AcademyUiState(
    val tier: AcademyTier,
    val prospects: List<AcademyProspect>,
    val capacityLabel: String,
    val budget: Long,
    val reputation: Int
)

class GetAcademyStateUseCase(private val ctx: Context) {
    operator fun invoke(): AcademyUiState {
        val gs = GameRepository.current()
        val tier = AcademyService.tier(gs)
        val prospects = AcademyService.prospects(gs)
        return AcademyUiState(
            tier = tier,
            prospects = prospects,
            capacityLabel = "${prospects.size}/${tier.capacity}",
            budget = gs.budget,
            reputation = gs.coachProfile.reputation
        )
    }
}

class RecruitProspectUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val prospect: AcademyProspect, val cost: Long) : Result()
        object CapacityFull : Result()
        data class InsufficientFunds(val cost: Long, val budget: Long) : Result()
    }
    operator fun invoke(): Result {
        val gs = GameRepository.current()
        val (result, prospect) = AcademyService.recruitManually(gs)
        return when (result) {
            AcademyService.RecruitResult.OK -> {
                GameRepository.save(ctx)
                Result.Ok(prospect!!, AcademyService.MANUAL_RECRUIT_COST)
            }
            AcademyService.RecruitResult.CAPACITY_FULL -> Result.CapacityFull
            AcademyService.RecruitResult.INSUFFICIENT_FUNDS ->
                Result.InsufficientFunds(AcademyService.MANUAL_RECRUIT_COST, gs.budget)
        }
    }
}

class EvaluateProspectUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val prospect: AcademyProspect) : Result()
        data class InsufficientFunds(val cost: Long, val budget: Long) : Result()
        object AlreadyEvaluated : Result()
        object NotFound : Result()
    }
    operator fun invoke(prospectId: String): Result {
        val gs = GameRepository.current()
        return when (AcademyService.evaluateProspect(gs, prospectId)) {
            AcademyService.EvaluateResult.OK -> {
                GameRepository.save(ctx)
                val prospect = AcademyService.prospects(gs).find { it.id == prospectId }
                    ?: return Result.NotFound
                Result.Ok(prospect)
            }
            AcademyService.EvaluateResult.INSUFFICIENT_FUNDS ->
                Result.InsufficientFunds(AcademyService.EVALUATION_COST, gs.budget)
            AcademyService.EvaluateResult.ALREADY_EVALUATED -> Result.AlreadyEvaluated
            AcademyService.EvaluateResult.NOT_FOUND -> Result.NotFound
        }
    }
}

class PromoteProspectUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val prospectName: String) : Result()
        object NotFound : Result()
    }
    operator fun invoke(prospectId: String): Result {
        val gs = GameRepository.current()
        val promotion = AcademyService.promoteProspect(gs, prospectId) ?: return Result.NotFound
        val teamName = GameRepository.snapshot(ctx)
            .times.find { it.id == gs.managerTeamId }?.nome ?: gs.managerTeamId
        GameRepository.addPromotedProspect(
            ctx, promotion.prospect, promotion.suggestedSalary, teamName
        )
        GameRepository.log(
            "ACADEMY",
            "${promotion.prospect.nome} subiu da base para o elenco principal " +
                "(overall ${promotion.prospect.currentOverall})."
        )
        NewsService.reportAcademyPromotion(
            gs, promotion.prospect.nome, teamName, promotion.prospect.currentOverall
        )
        GameRepository.save(ctx)
        SquadManager.validateAndFixRoster(ctx)
        return Result.Ok(promotion.prospect.nome)
    }
}

class ReleaseProspectUseCase(private val ctx: Context) {
    operator fun invoke(prospectId: String) {
        AcademyService.releaseProspect(GameRepository.current(), prospectId)
        GameRepository.save(ctx)
    }
}

class UpgradeAcademyUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val newTier: AcademyTier) : Result()
        object AlreadyMax : Result()
        data class LowReputation(val required: Int, val current: Int) : Result()
        data class InsufficientFunds(val cost: Long, val budget: Long) : Result()
    }
    operator fun invoke(): Result {
        val gs = GameRepository.current()
        return when (AcademyService.upgrade(gs)) {
            AcademyService.UpgradeResult.OK -> {
                GameRepository.save(ctx)
                Result.Ok(AcademyService.tier(gs))
            }
            AcademyService.UpgradeResult.ALREADY_MAX -> Result.AlreadyMax
            AcademyService.UpgradeResult.LOW_REPUTATION -> {
                val next = nextTierAfter(AcademyService.tier(gs))
                Result.LowReputation(next?.minReputation ?: 0, gs.coachProfile.reputation)
            }
            AcademyService.UpgradeResult.INSUFFICIENT_FUNDS -> {
                val next = nextTierAfter(AcademyService.tier(gs))
                Result.InsufficientFunds(next?.upgradeCost ?: 0, gs.budget)
            }
        }
    }
    private fun nextTierAfter(current: AcademyTier): AcademyTier? =
        AcademyTier.values().getOrNull(current.ordinal + 1)
}

// ────────────────────────────────────────────────────────────────────────────
// BANCO
// ────────────────────────────────────────────────────────────────────────────

/** Snapshot do estado bancário para a UI. */
data class BankUiState(
    val budget: Long,
    val health: FinancialHealth,
    val healthAdvice: String,
    val availableCredit: Long,
    val totalDebt: Long,
    val weeklyInstallments: Long,
    val offers: List<LoanOffer>,
    val activeLoans: List<BankLoan>
)

class GetBankStateUseCase(private val ctx: Context) {
    operator fun invoke(): BankUiState {
        val gs = GameRepository.current()
        return BankUiState(
            budget = gs.budget,
            health = BankService.financialHealth(gs),
            healthAdvice = BankService.healthAdvice(gs),
            availableCredit = BankService.availableCredit(gs),
            totalDebt = BankService.totalDebt(gs),
            weeklyInstallments = BankService.weeklyInstallmentsTotal(gs),
            offers = BankService.offersFor(gs),
            activeLoans = BankService.activeLoans(gs)
        )
    }
}

class TakeLoanUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val loan: BankLoan, val installment: Long) : Result()
        data class ExceedsCredit(val available: Long, val requested: Long) : Result()
        data class LowReputation(val required: Int, val current: Int) : Result()
    }
    operator fun invoke(offer: LoanOffer): Result {
        val gs = GameRepository.current()
        return when (val r = BankService.takeLoan(gs, offer)) {
            is BankService.TakeResult.Ok -> {
                GameRepository.log(
                    "BANK",
                    "💰 Empréstimo \"${offer.label}\" contratado: R$ ${"%,d".format(offer.principal)} creditados."
                )
                GameRepository.save(ctx)
                Result.Ok(r.loan, BankService.installmentFor(offer))
            }
            is BankService.TakeResult.ExceedsCredit ->
                Result.ExceedsCredit(r.available, offer.principal)
            BankService.TakeResult.LowReputation ->
                Result.LowReputation(offer.minReputation, gs.coachProfile.reputation)
        }
    }
}

class PayOffLoanUseCase(private val ctx: Context) {
    sealed class Result {
        data class Ok(val loanLabel: String, val amountPaid: Long) : Result()
        data class InsufficientFunds(val needed: Long, val budget: Long) : Result()
        object NotFound : Result()
    }
    operator fun invoke(loanId: String): Result {
        val gs = GameRepository.current()
        val loan = BankService.activeLoans(gs).find { it.id == loanId } ?: return Result.NotFound
        return when (val r = BankService.payOffEarly(gs, loanId)) {
            is BankService.PayoffResult.Ok -> {
                GameRepository.log(
                    "BANK",
                    "✅ Empréstimo \"${loan.label}\" quitado antecipadamente (R$ ${"%,d".format(r.amountPaid)})."
                )
                GameRepository.save(ctx)
                Result.Ok(loan.label, r.amountPaid)
            }
            is BankService.PayoffResult.InsufficientFunds ->
                Result.InsufficientFunds(r.needed, gs.budget)
            BankService.PayoffResult.NotFound -> Result.NotFound
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// PROPOSTAS RECEBIDAS
// ────────────────────────────────────────────────────────────────────────────

/** Snapshot da tela de propostas recebidas. */
data class IncomingOffersUiState(
    val offers: List<IncomingOfferRow>,
    val marketOpen: Boolean,
    val marketStatus: String
)

/**
 * Linha pronta para a UI: a oferta + sinal comparativo com o preço de mercado
 * já resolvido. O preço de mercado vinha do roster do gerente (no
 * [com.cblol.scout.game.TransferMarket.marketPriceOf]) e era calculado no
 * adapter — puxar para cá mantém a Activity livre de lógica.
 *
 * @property vsMarketPercent positivo = oferta acima do mercado; negativo =
 *   abaixo; zero = mesmo valor; null = jogador não está mais no elenco (sem
 *   referência de preço, esconde o comparativo).
 */
data class IncomingOfferRow(
    val offer: com.cblol.scout.data.IncomingTransferOffer,
    val vsMarketPercent: Int?
)

class GetIncomingOffersStateUseCase(private val ctx: Context) {
    operator fun invoke(): IncomingOffersUiState {
        val gs = GameRepository.current()
        val roster = GameRepository.rosterOf(ctx, gs.managerTeamId)
        val rows = IncomingOfferService.activeOffers(gs).map { offer ->
            val player = roster.find { it.id == offer.playerId }
            val vsPct = player?.let {
                val market = TransferMarket.marketPriceOf(it)
                if (market > 0) ((offer.amountBrl - market) * 100 / market).toInt() else 0
            }
            IncomingOfferRow(offer, vsPct)
        }
        return IncomingOffersUiState(
            offers = rows,
            marketOpen = TransferWindowService.isMarketOpen(gs),
            marketStatus = TransferWindowService.statusMessage(gs)
        )
    }
}

class AcceptIncomingOfferUseCase(private val ctx: Context) {
    operator fun invoke(offerId: String): SellResult =
        TransferMarket.acceptIncomingOffer(ctx, offerId)
}

class RejectIncomingOfferUseCase(private val ctx: Context) {
    operator fun invoke(offerId: String) {
        TransferMarket.rejectIncomingOffer(ctx, offerId)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// NOTÍCIAS
// ────────────────────────────────────────────────────────────────────────────

data class NewsUiState(
    val items: List<NewsItem>,
    val teamId: String
)

class GetNewsStateUseCase(private val ctx: Context) {
    operator fun invoke(): NewsUiState {
        val gs = GameRepository.current()
        return NewsUiState(
            items = NewsService.feed(gs),
            teamId = gs.managerTeamId
        )
    }
}
