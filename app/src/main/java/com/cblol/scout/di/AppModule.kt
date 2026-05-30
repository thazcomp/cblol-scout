package com.cblol.scout.di

import com.cblol.scout.data.realm.RealmStaticDataSource
import com.cblol.scout.domain.datasource.StaticDataSource
import com.cblol.scout.domain.usecase.*
import com.cblol.scout.ui.viewmodel.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Módulo Koin — registra UseCases e ViewModels.
 *
 * **Organização**: UseCases agrupados por bounded context, ViewModels no fim.
 * Cada nova ViewModel implica adicionar (1) os UseCases que ela consome no
 * bloco apropriado e (2) o `viewModel { ... }` aqui.
 */
val appModule = module {

    // ── Fonte de dados estáticos (Realm criptografado) ────────────────
    single<StaticDataSource> { RealmStaticDataSource(androidContext()) }

    // ── UseCases — Career ────────────────────────────────────────────────
    factory { StartNewCareerUseCase(androidContext()) }
    factory { LoadCareerUseCase(androidContext()) }
    factory { HasSaveUseCase(androidContext()) }
    factory { ClearCareerUseCase(androidContext()) }
    factory { ValidateRosterUseCase(androidContext()) }
    factory { IsMissingStarterUseCase(androidContext()) }
    factory { StarterCountUseCase(androidContext()) }
    factory { CanSellPlayerUseCase(androidContext()) }

    // ── UseCases — Hub ───────────────────────────────────────────────────
    factory { GetHubStateUseCase(androidContext()) }

    // ── UseCases — Match ─────────────────────────────────────────────────
    factory { GetNextMatchUseCase(androidContext()) }
    factory { GetAllMatchesUseCase(androidContext()) }
    factory { SavePickBanPlanUseCase(androidContext()) }
    factory { SimulateMapWithPicksUseCase(androidContext()) }
    factory { UpdateSeriesStateUseCase(androidContext()) }
    factory { FinalizeMatchUseCase(androidContext()) }

    // UseCases — Calendário (agrega eventos de TODOS os subsistemas)
    factory { CalendarEventsAggregator(androidContext()) }

    // ── UseCases — Squad ─────────────────────────────────────────────────
    factory { GetRosterUseCase(androidContext()) }
    factory { GetStartersUseCase(androidContext()) }
    factory { GetReservesUseCase(androidContext()) }
    factory { SwapStartersUseCase(androidContext()) }
    factory { PromoteFromBenchUseCase(androidContext()) }
    factory { GetStandingsUseCase(androidContext()) }

    // ── UseCases — Transfer ──────────────────────────────────────────────
    factory { GetMarketRosterUseCase(androidContext()) }
    factory { BuyPlayerUseCase(androidContext()) }
    factory { GetMarketPriceUseCase() }

    // ── UseCases — Academia ──────────────────────────────────────────────
    factory { GetAcademyStateUseCase(androidContext()) }
    factory { RecruitProspectUseCase(androidContext()) }
    factory { EvaluateProspectUseCase(androidContext()) }
    factory { PromoteProspectUseCase(androidContext()) }
    factory { ReleaseProspectUseCase(androidContext()) }
    factory { UpgradeAcademyUseCase(androidContext()) }

    // ── UseCases — Banco ─────────────────────────────────────────────────
    factory { GetBankStateUseCase(androidContext()) }
    factory { TakeLoanUseCase(androidContext()) }
    factory { PayOffLoanUseCase(androidContext()) }

    // ── UseCases — Propostas recebidas ───────────────────────────────────
    factory { GetIncomingOffersStateUseCase(androidContext()) }
    factory { AcceptIncomingOfferUseCase(androidContext()) }
    factory { RejectIncomingOfferUseCase(androidContext()) }

    // ── UseCases — Notícias ──────────────────────────────────────────────
    factory { GetNewsStateUseCase(androidContext()) }

    // ── UseCases — Patrocínios ───────────────────────────────────────────
    factory { GetSponsorsStateUseCase(androidContext()) }
    factory { AcceptSponsorOfferUseCase(androidContext()) }
    factory { RejectSponsorOfferUseCase(androidContext()) }
    factory { CancelSponsorContractUseCase(androidContext()) }

    // ── UseCases — Olheiros ──────────────────────────────────────────────
    factory { GetScoutingStateUseCase(androidContext()) }
    factory { CancelScoutingUseCase(androidContext()) }
    factory { UpgradeScoutingUseCase(androidContext()) }

    // ── UseCases — Treinos ───────────────────────────────────────────────
    factory { GetTrainingStateUseCase(androidContext()) }
    factory { RunTrainingUseCase(androidContext()) }

    // ── ViewModels ───────────────────────────────────────────────────────
    viewModel {
        ManagerHubViewModel(
            loadCareer  = get(),
            getHubState = get(),
            clearCareer = get()
        )
    }

    viewModel {
        ScheduleViewModel(
            getAllMatches        = get(),
            savePickBanPlan      = get(),
            simulateMapWithPicks = get(),
            updateSeriesState    = get(),
            finalizeMatch        = get(),
            calendarAggregator   = get()
        )
    }

    viewModel {
        SquadViewModel(
            getStarters      = get(),
            getReserves      = get(),
            swapStarters     = get(),
            promoteFromBench = get()
        )
    }

    viewModel {
        TransferMarketViewModel(
            getMarketRoster = get(),
            buyPlayer       = get(),
            getMarketPrice  = get()
        )
    }

    viewModel {
        StandingsViewModel(getStandings = get())
    }

    viewModel {
        TeamSelectViewModel(
            hasSav         = get(),
            loadCareer     = get(),
            startNewCareer = get(),
            clearCareer    = get()
        )
    }

    viewModel {
        AcademyViewModel(
            getState = get(),
            recruit  = get(),
            evaluate = get(),
            promote  = get(),
            release  = get(),
            upgrade  = get()
        )
    }

    viewModel {
        BankViewModel(
            getState = get(),
            takeLoan = get(),
            payOff   = get()
        )
    }

    viewModel {
        IncomingOffersViewModel(
            getState = get(),
            accept   = get(),
            reject   = get()
        )
    }

    viewModel {
        NewsViewModel(getState = get())
    }

    viewModel {
        SponsorsViewModel(
            getState       = get(),
            acceptOffer    = get(),
            rejectOffer    = get(),
            cancelContract = get()
        )
    }

    viewModel {
        ScoutingViewModel(
            getState       = get(),
            cancelScouting = get(),
            upgradeDept    = get()
        )
    }

    viewModel {
        TrainingViewModel(
            getState    = get(),
            runTraining = get()
        )
    }
}
