package com.cblol.scout.di

import com.cblol.scout.data.realm.RealmStaticDataSource
import com.cblol.scout.domain.datasource.StaticDataSource
import com.cblol.scout.domain.usecase.*
import com.cblol.scout.ui.viewmodel.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // ── Fonte de dados estáticos (Realm criptografado) ────────────────
    // Singleton: abre o Realm uma vez e mantém os dados em cache. Também é
    // instalado em StaticData no CBLOLApp para os repositórios `object` (que não
    // recebem injeção) acessarem. Exposto aqui para use cases/ViewModels que
    // queiram depender da abstração diretamente via DI.
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
            getAllMatches       = get(),
            savePickBanPlan    = get(),
            simulateMapWithPicks = get(),
            updateSeriesState  = get(),
            finalizeMatch      = get()
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
            hasSav      = get(),
            loadCareer   = get(),
            startNewCareer = get(),
            clearCareer  = get()
        )
    }
}
