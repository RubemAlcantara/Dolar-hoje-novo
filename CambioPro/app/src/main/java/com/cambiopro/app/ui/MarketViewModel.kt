@file:OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.cambiopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cambiopro.app.di.AppContainer
import com.cambiopro.app.domain.Asset
import com.cambiopro.app.domain.Converter
import com.cambiopro.app.domain.MarketCoin
import com.cambiopro.app.domain.RateBook
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SortKey(val label: String) {
    CAP("Cap. de mercado"), PRICE("Preço"), CHANGE("Variação 24h")
}

data class FiatCard(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double?,
    val spark: List<Double>,
    val date: String,
)

data class MarketUiState(
    val loading: Boolean = true,
    val coins: List<MarketCoin> = emptyList(),
    val fiat: List<FiatCard> = emptyList(),
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val currency: String = "BRL",
    val query: String = "",
    val sort: SortKey = SortKey.CAP,
    val ascending: Boolean = false,
    val remoteResults: List<Asset> = emptyList(),
)

val FIAT_NAMES = mapOf(
    "USD" to "Dólar americano", "EUR" to "Euro", "BRL" to "Real brasileiro",
    "GBP" to "Libra esterlina", "JPY" to "Iene japonês", "CAD" to "Dólar canadense",
    "AUD" to "Dólar australiano", "CHF" to "Franco suíço",
)

fun visibleCoins(s: MarketUiState): List<MarketCoin> {
    val q = s.query.trim().lowercase()
    val filtered = if (q.isEmpty()) s.coins
    else s.coins.filter { it.name.lowercase().contains(q) || it.symbol.lowercase().contains(q) }
    val asc = when (s.sort) {
        SortKey.CAP -> filtered.sortedBy { it.marketCap ?: 0.0 }
        SortKey.PRICE -> filtered.sortedBy { it.price }
        SortKey.CHANGE -> filtered.sortedBy { it.change24hPct ?: Double.NEGATIVE_INFINITY }
    }
    return if (s.ascending) asc else asc.reversed()
}

/** Montagem dos cartões de moeda tradicional (USD/EUR/BRL) comparados à moeda padrão. */
suspend fun buildFiatCards(c: AppContainer, cur: String, force: Boolean, withSeries: Boolean): List<FiatCard> {
    val table = c.repo.fiatTable(force).data
    val book = RateBook(fiatPerUsd = table.perUsd)
    return listOf("USD", "EUR", "BRL").filter { it != cur }.take(2).mapNotNull { code ->
        val rate = Converter.rate(Asset.fiat(code), Asset.fiat(cur), book) ?: return@mapNotNull null
        val series = if (withSeries) runCatching { c.repo.fiatSeries(code, cur, 30).data }.getOrDefault(emptyList())
        else emptyList()
        val change = if (series.size >= 2) {
            val a = series[series.size - 2].v
            val b = series.last().v
            if (a > 0) (b / a - 1) * 100 else null
        } else null
        FiatCard(code, FIAT_NAMES[code] ?: code, rate, change, series.map { it.v }, table.date)
    }
}

class MarketViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    val favorites: StateFlow<Set<String>> =
        c.userPrefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val queryFlow = MutableStateFlow("")
    private val refreshMutex = Mutex()

    init {
        // Moeda padrão / intervalo: reinicia o ciclo de atualização quando mudam.
        viewModelScope.launch {
            c.userPrefs.settings.map { it.currency to it.refreshMinutes }.distinctUntilChanged()
                .collectLatest { (cur, mins) ->
                    _state.update {
                        it.copy(
                            currency = cur,
                            coins = if (it.currency != cur) emptyList() else it.coins,
                            fiat = if (it.currency != cur) emptyList() else it.fiat,
                        )
                    }
                    refreshNow(false)
                    if (mins > 0) {
                        while (true) {
                            delay(mins * 60_000L)
                            refreshNow(false)
                        }
                    } else {
                        awaitCancellation()
                    }
                }
        }
        // Volta a atualizar quando a conexão retorna.
        viewModelScope.launch {
            c.connectivity.online.collect { online ->
                val wasOffline = _state.value.offline
                _state.update { it.copy(offline = !online) }
                if (online && wasOffline) refreshNow(false)
            }
        }
        // Busca remota (ativos fora do top 100), com debounce.
        viewModelScope.launch {
            queryFlow.debounce(500).map { it.trim() }.distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) {
                    _state.update { it.copy(remoteResults = emptyList()) }
                } else {
                    val res = runCatching { c.repo.search(q) }.getOrDefault(emptyList())
                    val known = _state.value.coins.map { it.id }.toSet()
                    _state.update { s -> s.copy(remoteResults = res.filter { it.id !in known }.take(15)) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow(true) }
    }

    private suspend fun refreshNow(force: Boolean) = refreshMutex.withLock {
        val cur = _state.value.currency
        _state.update { it.copy(loading = true, error = null) }
        val markets = runCatching { c.repo.markets(cur.lowercase(), force) }
        val fiat = runCatching { buildFiatCards(c, cur, force, withSeries = true) }
        _state.update { s ->
            val q = markets.getOrNull()
            s.copy(
                loading = false,
                coins = q?.data ?: s.coins,
                fiat = fiat.getOrNull() ?: s.fiat,
                fetchedAt = q?.fetchedAt ?: s.fetchedAt,
                stale = q?.stale ?: s.coins.isNotEmpty(),
                error = if (q == null && s.coins.isEmpty()) markets.exceptionOrNull()?.message else null,
            )
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun setSort(key: SortKey) {
        _state.update {
            if (it.sort == key) it.copy(ascending = !it.ascending) else it.copy(sort = key, ascending = false)
        }
    }

    fun toggleFavorite(assetKey: String) {
        viewModelScope.launch { c.userPrefs.toggleFavorite(assetKey) }
    }
}
