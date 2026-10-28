@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.cambiopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cambiopro.app.data.local.AppSettings
import com.cambiopro.app.data.local.ThemeMode
import com.cambiopro.app.di.AppContainer
import com.cambiopro.app.domain.ConversionRecord
import com.cambiopro.app.domain.MarketCoin
import com.cambiopro.app.domain.PricePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- Favoritos ----------------

data class FavoritesUiState(
    val loading: Boolean = false,
    val coins: List<MarketCoin> = emptyList(),
    val fiat: List<FiatCard> = emptyList(),
    val currency: String = "BRL",
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
    val error: String? = null,
    val hasFavorites: Boolean = false,
)

class FavoritesViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()
    val favorites: StateFlow<Set<String>> =
        c.userPrefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var lastFavs: Set<String> = emptySet()
    private var lastCur: String = "BRL"

    init {
        viewModelScope.launch {
            combine(
                c.userPrefs.favorites,
                c.userPrefs.settings.map { it.currency }.distinctUntilChanged(),
            ) { f, cur -> f to cur }.collectLatest { (f, cur) ->
                lastFavs = f
                lastCur = cur
                load(f, cur, false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(lastFavs, lastCur, true) }
    }

    fun toggle(key: String) {
        viewModelScope.launch { c.userPrefs.toggleFavorite(key) }
    }

    private suspend fun load(favs: Set<String>, cur: String, force: Boolean) {
        val cryptoIds = favs.filter { it.startsWith("CRYPTO:") }.map { it.removePrefix("CRYPTO:") }
        val fiatCodes = favs.filter { it.startsWith("FIAT:") }.map { it.removePrefix("FIAT:") }
        if (favs.isEmpty()) {
            _state.value = FavoritesUiState(currency = cur, hasFavorites = false)
            return
        }
        _state.update { it.copy(loading = true, error = null, currency = cur, hasFavorites = true) }
        val coins = if (cryptoIds.isEmpty()) null else runCatching { c.repo.marketsByIds(cryptoIds, cur.lowercase(), force) }
        val fiat = if (fiatCodes.isEmpty()) emptyList() else runCatching {
            val table = c.repo.fiatTable(force).data
            val book = com.cambiopro.app.domain.RateBook(fiatPerUsd = table.perUsd)
            fiatCodes.mapNotNull { code ->
                val r = com.cambiopro.app.domain.Converter.rate(
                    com.cambiopro.app.domain.Asset.fiat(code), com.cambiopro.app.domain.Asset.fiat(cur), book,
                ) ?: return@mapNotNull null
                FiatCard(code, FIAT_NAMES[code] ?: code, r, null, emptyList(), table.date)
            }.sortedBy { it.code }
        }.getOrDefault(emptyList())
        val q = coins?.getOrNull()
        _state.update { s ->
            s.copy(
                loading = false,
                coins = if (cryptoIds.isEmpty()) emptyList() else (q?.data ?: s.coins.filter { it.id in cryptoIds }),
                fiat = fiat,
                fetchedAt = q?.fetchedAt ?: s.fetchedAt,
                stale = q?.stale ?: (coins != null),
                error = if (coins != null && q == null && s.coins.isEmpty()) coins.exceptionOrNull()?.message else null,
            )
        }
    }
}

// ---------------- Detalhes ----------------

data class DetailUiState(
    val coin: MarketCoin? = null,
    val coinLoading: Boolean = true,
    val coinError: String? = null,
    val coinAt: Long? = null,
    val coinStale: Boolean = false,
    val days: Int = 7,
    val points: List<PricePoint> = emptyList(),
    val chartLoading: Boolean = true,
    val chartError: String? = null,
    val chartAt: Long? = null,
    val currency: String = "BRL",
)

class DetailViewModel(private val c: AppContainer, private val coinId: String) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()
    val favorites: StateFlow<Set<String>> =
        c.userPrefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init { load(false) }

    fun load(force: Boolean) {
        viewModelScope.launch {
            val cur = c.userPrefs.settings.first().currency
            _state.update { it.copy(currency = cur, coinLoading = true, coinError = null) }
            runCatching { c.repo.marketsByIds(listOf(coinId), cur.lowercase(), force) }
                .onSuccess { q ->
                    val coin = q.data.firstOrNull()
                    _state.update {
                        it.copy(
                            coin = coin, coinLoading = false, coinAt = q.fetchedAt, coinStale = q.stale,
                            coinError = if (coin == null) "Ativo sem cotação disponível." else null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(coinLoading = false, coinError = e.message) } }
            loadChart(_state.value.days, force)
        }
    }

    fun setDays(days: Int) {
        _state.update { it.copy(days = days) }
        viewModelScope.launch { loadChart(days, false) }
    }

    private suspend fun loadChart(days: Int, force: Boolean) {
        val cur = _state.value.currency
        _state.update { it.copy(chartLoading = true, chartError = null) }
        runCatching { c.repo.chart(coinId, cur.lowercase(), days, force) }
            .onSuccess { q ->
                if (_state.value.days == days) {
                    _state.update { it.copy(points = q.data, chartLoading = false, chartAt = q.fetchedAt) }
                }
            }
            .onFailure { e ->
                if (_state.value.days == days) {
                    _state.update { it.copy(points = emptyList(), chartLoading = false, chartError = e.message) }
                }
            }
    }

    fun toggleFavorite() {
        viewModelScope.launch { c.userPrefs.toggleFavorite("CRYPTO:$coinId") }
    }
}

// ---------------- Histórico e Configurações ----------------

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val history: StateFlow<List<ConversionRecord>> =
        c.userPrefs.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clear() {
        viewModelScope.launch { c.userPrefs.clearHistory() }
    }
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        c.userPrefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTheme(m: ThemeMode) { viewModelScope.launch { c.userPrefs.setTheme(m) } }
    fun setCurrency(code: String) { viewModelScope.launch { c.userPrefs.setCurrency(code) } }
    fun setRefresh(min: Int) { viewModelScope.launch { c.userPrefs.setRefreshMinutes(min) } }
    fun setGeckoKey(k: String) { viewModelScope.launch { c.userPrefs.setGeckoKey(k) } }
}

// ---------------- Fábrica ----------------

class VmFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        MarketViewModel::class.java -> MarketViewModel(c)
        ConverterViewModel::class.java -> ConverterViewModel(c)
        FavoritesViewModel::class.java -> FavoritesViewModel(c)
        HistoryViewModel::class.java -> HistoryViewModel(c)
        SettingsViewModel::class.java -> SettingsViewModel(c)
        else -> throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
    } as T
}
