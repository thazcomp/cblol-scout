package com.cblol.scout

import android.app.Application
import com.cblol.scout.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CBLOLApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CBLOLApp)
            modules(appModule)
        }
    }
}
