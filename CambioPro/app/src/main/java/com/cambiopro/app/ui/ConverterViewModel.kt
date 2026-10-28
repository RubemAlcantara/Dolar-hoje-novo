package com.cambiopro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cambiopro.app.di.AppContainer
import com.cambiopro.app.domain.Asset
import com.cambiopro.app.domain.AssetKind
import com.cambiopro.app.domain.ConversionOutcome
import com.cambiopro.app.domain.ConversionRecord
import com.cambiopro.app.domain.Converter
import com.cambiopro.app.domain.RateBook
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConverterUiState(
    val from: Asset = Asset.fiat("BRL", "Real brasileiro"),
    val to: Asset = Asset.fiat("USD", "Dólar americano"),
    val amountText: String = "1",
    val outcome: ConversionOutcome? = null,
    val loadingRates: Boolean = false,
    val rateMessage: String? = null,
    val ratesAt: Long? = null,
    val stale: Boolean = false,
    val fiatDate: String? = null,
    val assets: List<Asset> = emptyList(),
    val remoteAssets: List<Asset> = emptyList(),
    val offline: Boolean = false,
)

private val FALLBACK_FIAT = FIAT_NAMES.map { (k, v) -> Asset.fiat(k, v) }
private val FIAT_PRIORITY = listOf("BRL", "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF")

class ConverterViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    private var book = RateBook()
    private var ratesJob: Job? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadAssets()
        refreshRates(false)
        viewModelScope.launch {
            c.connectivity.online.collect { online ->
                val wasOffline = _state.value.offline
                _state.update { it.copy(offline = !online) }
                if (online && wasOffline) refreshRates(false)
            }
        }
    }

    fun setAmount(text: String) {
        val clean = text.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }.take(24)
        _state.update { it.copy(amountText = clean) }
        recompute()
    }

    fun setFrom(a: Asset) { _state.update { it.copy(from = a) }; refreshRates(false) }
    fun setTo(a: Asset) { _state.update { it.copy(to = a) }; refreshRates(false) }

    fun swap() {
        _state.update { it.copy(from = it.to, to = it.from) }
        recompute()
    }

    fun refreshRates(force: Boolean) {
        ratesJob?.cancel()
        ratesJob = viewModelScope.launch {
            val s = _state.value
            val assets = listOf(s.from, s.to)
            val cryptoIds = assets.filter { it.kind == AssetKind.CRYPTO }.map { it.id }
            val fiatCodes = assets.filter { it.kind == AssetKind.FIAT }.map { it.id }
            val needTable = fiatCodes.size == 2 || fiatCodes.any { it != "USD" && it != "BRL" }
            _state.update { it.copy(loadingRates = true, rateMessage = null) }

            val spot = if (cryptoIds.isNotEmpty()) runCatching { c.repo.cryptoSpot(cryptoIds, force) } else null
            val table = if (needTable) runCatching { c.repo.fiatTable(force) } else null

            val spotData = spot?.getOrNull()
            val tableData = table?.getOrNull()
            book = RateBook(
                cryptoUsd = spotData?.data?.mapNotNull { (k, v) -> v.usd?.let { k to it } }?.toMap() ?: emptyMap(),
                cryptoBrl = spotData?.data?.mapNotNull { (k, v) -> v.brl?.let { k to it } }?.toMap() ?: emptyMap(),
                fiatPerUsd = tableData?.data?.perUsd ?: emptyMap(),
            )
            val times = listOfNotNull(spotData?.fetchedAt, tableData?.fetchedAt)
            val anyStale = (spotData?.stale == true) || (tableData?.stale == true)
            val err = listOfNotNull(spot?.exceptionOrNull(), table?.exceptionOrNull()).firstOrNull()?.message
            _state.update {
                it.copy(
                    loadingRates = false,
                    ratesAt = times.minOrNull() ?: it.ratesAt,
                    stale = anyStale,
                    fiatDate = tableData?.data?.date ?: it.fiatDate,
                    rateMessage = err,
                )
            }
            recompute()
        }
    }

    private fun recompute() {
        val s = _state.value
        val outcome = Converter.convertText(s.amountText, s.from, s.to, book)
        _state.update { it.copy(outcome = outcome) }
        scheduleSave()
    }

    /** Salva no histórico 1,5 s depois de o usuário parar de digitar, só se o resultado for válido e > 0. */
    private fun scheduleSave() {
        saveJob?.cancel()
        val s = _state.value
        val ok = s.outcome as? ConversionOutcome.Ok ?: return
        if (ok.amount <= 0.0) return
        saveJob = viewModelScope.launch {
            delay(1_500)
            c.userPrefs.addHistory(
                ConversionRecord(
                    s.from.key, s.to.key, s.from.symbol, s.to.symbol,
                    ok.amount, ok.result, ok.rate, System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun loadAssets() {
        viewModelScope.launch {
            val fiatMap = runCatching { c.repo.fiatCurrencies().data }.getOrDefault(emptyMap())
            val fiat = (if (fiatMap.isEmpty()) FALLBACK_FIAT else fiatMap.map { (k, v) -> Asset.fiat(k, v) })
                .sortedWith(compareBy({ FIAT_PRIORITY.indexOf(it.id).let { i -> if (i < 0) 99 else i } }, { it.id }))
            val cur = c.userPrefs.settings.first().currency.lowercase()
            val coins = runCatching { c.repo.markets(cur).data }.getOrDefault(emptyList())
            _state.update { it.copy(assets = fiat + coins.map { coin -> coin.asAsset() }) }
        }
    }

    fun searchRemote(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(remoteAssets = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            val res = runCatching { c.repo.search(query) }.getOrDefault(emptyList())
            val known = _state.value.assets.map { it.key }.toSet()
            _state.update { s -> s.copy(remoteAssets = res.filter { it.key !in known }) }
        }
    }
}
