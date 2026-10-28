package com.cambiopro.app.di

import android.app.Application
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.cambiopro.app.data.ConnectivityObserver
import com.cambiopro.app.data.MarketRepository
import com.cambiopro.app.data.local.FileCacheStore
import com.cambiopro.app.data.local.UserPrefs
import com.cambiopro.app.data.remote.CoinGeckoApi
import com.cambiopro.app.data.remote.FrankfurterApi
import com.cambiopro.app.data.remote.Network
import com.cambiopro.app.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Injeção de dependências manual (sem framework), criada uma vez em [com.cambiopro.app.CambioApp]. */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = appScope,
    ) { File(app.filesDir, "datastore/user.preferences_pb") }

    val userPrefs = UserPrefs(dataStore)

    @Volatile
    private var geckoKey: String = ""

    private val http = Network.okHttp { geckoKey }

    val repo = MarketRepository(
        gecko = Network.retrofit<CoinGeckoApi>(http, "https://api.coingecko.com/api/v3/"),
        fx = Network.retrofit<FrankfurterApi>(http, "https://api.frankfurter.dev/"),
        cache = FileCacheStore(File(app.filesDir, "cache")),
    )

    val connectivity = ConnectivityObserver(app)

    init {
        appScope.launch { userPrefs.settings.collect { geckoKey = it.geckoKey.trim() } }
        appScope.launch {
            userPrefs.settings.map { it.refreshMinutes }.distinctUntilChanged()
                .collect { RefreshScheduler.apply(app, it) }
        }
    }
}
