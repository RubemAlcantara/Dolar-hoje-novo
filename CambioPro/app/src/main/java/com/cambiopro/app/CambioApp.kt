package com.cambiopro.app

import android.app.Application
import com.cambiopro.app.di.AppContainer

class CambioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
