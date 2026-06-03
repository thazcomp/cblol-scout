package com.cblol.scout

import android.app.Application
import com.cblol.scout.data.StaticData
import com.cblol.scout.data.realm.RealmStaticDataSource
import com.cblol.scout.di.appModule
import com.cblol.scout.game.GameRepository
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CBLOLApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Inicializa o GameRepository antes de qualquer Activity
        GameRepository.load(applicationContext)

        // Instala o DataSource de dados estáticos (Realm criptografado) ANTES de
        // qualquer Activity/repositório rodar. Os repositórios (ChampionRepository,
        // CompositionRepository, SponsorCatalog, ChampionPoolRepository) leem
        // através de StaticData. O Realm é semeado na primeira execução a partir
        // dos objetos `data.seed.*`.
        StaticData.install(RealmStaticDataSource(this))

        startKoin {
            androidLogger()
            androidContext(this@CBLOLApp)
            modules(appModule)
        }
    }
}
