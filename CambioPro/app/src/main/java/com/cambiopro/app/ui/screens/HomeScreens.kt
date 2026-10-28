@file:OptIn(ExperimentalMaterial3Api::class)

package com.cambiopro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cambiopro.app.domain.Format
import com.cambiopro.app.domain.MarketCoin
import com.cambiopro.app.ui.FavoritesViewModel
import com.cambiopro.app.ui.FiatCard
import com.cambiopro.app.ui.MarketViewModel
import com.cambiopro.app.ui.SortKey
import com.cambiopro.app.ui.components.AppTopBar
import com.cambiopro.app.ui.components.AssetRow
import com.cambiopro.app.ui.components.EmptyState
import com.cambiopro.app.ui.components.ErrorState
import com.cambiopro.app.ui.components.LoadingState
import com.cambiopro.app.ui.components.SectionTitle
import com.cambiopro.app.ui.components.StatusBanner
import com.cambiopro.app.ui.visibleCoins

private data class Highlight(val id: String, val name: String, val symbol: String)

private val HIGHLIGHTS = listOf(
    Highlight("bitcoin", "Bitcoin", "BTC"),
    Highlight("ethereum", "Ethereum", "ETH"),
    Highlight("solana", "Solana", "SOL"),
    Highlight("ripple", "XRP", "XRP"),
    Highlight("binancecoin", "BNB", "BNB"),
)

@Composable
fun CoinRow(coin: MarketCoin, currency: String, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    AssetRow(
        title = coin.name,
        symbol = coin.symbol,
        price = Format.money(coin.price, currency),
        change = coin.change24hPct,
        spark = coin.sparkline,
        favorite = favorite,
        onFavorite = onFavorite,
        onClick = onClick,
    )
}

@Composable
fun FiatRow(card: FiatCard, currency: String, favorite: Boolean, onFavorite: () -> Unit) {
    AssetRow(
        title = card.name,
        symbol = card.code,
        price = Format.money(card.price, currency),
        change = card.changePct,
        spark = card.spark,
        favorite = favorite,
        onFavorite = onFavorite,
        onClick = null,
    )
}

@Composable
private fun UnavailableRow(name: String, symbol: String) {
    AssetRow(name, symbol, "Indisponível", null, emptyList(), false, null, null)
}

@Composable
fun HomeScreen(vm: MarketViewModel, onOpenCoin: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Câmbio Pro", actions = {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar cotações") }
        })
        when {
            s.loading && s.coins.isEmpty() && s.fiat.isEmpty() -> LoadingState()
            s.error != null && s.coins.isEmpty() && s.fiat.isEmpty() ->
                ErrorState(s.error!!, onRetry = { vm.refresh() })
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StatusBanner(s.fetchedAt, s.stale, s.offline) }
                if (s.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { SectionTitle("Criptomoedas") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column {
                            HIGHLIGHTS.forEachIndexed { i, h ->
                                val coin = s.coins.firstOrNull { it.id == h.id }
                                if (coin != null) {
                                    CoinRow(coin, s.currency, "CRYPTO:${coin.id}" in favs,
                                        onFavorite = { vm.toggleFavorite("CRYPTO:${coin.id}") },
                                        onClick = { onOpenCoin(coin.id) })
                                } else {
                                    UnavailableRow(h.name, h.symbol)
                                }
                                if (i < HIGHLIGHTS.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                item { SectionTitle("Moedas (câmbio de referência)") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column {
                            if (s.fiat.isEmpty()) {
                                UnavailableRow("Dólar americano", "USD")
                                HorizontalDivider()
                                UnavailableRow("Euro", "EUR")
                            } else {
                                s.fiat.forEachIndexed { i, f ->
                                    FiatRow(f, s.currency, "FIAT:${f.code}" in favs, onFavorite = { vm.toggleFavorite("FIAT:${f.code}") })
                                    if (i < s.fiat.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
                item {
                    val date = s.fiat.firstOrNull()?.date
                    Text(
                        "Câmbio de referência (Frankfurter/BCE)${if (date != null) ", dados de $date" else ""}. " +
                            "Atualizado uma vez por dia útil; não é a cotação de compra/venda de bancos nem dólar turismo. " +
                            "Variação das moedas: contra o dia útil anterior. Criptomoedas: CoinGecko, variação em 24 h.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun CryptoScreen(vm: MarketViewModel, onOpenCoin: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val list = visibleCoins(s)
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Criptomoedas", actions = {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar cotações") }
        })
        OutlinedTextField(
            value = s.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("Buscar por nome ou símbolo") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (s.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Limpar busca") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SortKey.entries.toList()) { k ->
                val selected = s.sort == k
                FilterChip(
                    selected = selected,
                    onClick = { vm.setSort(k) },
                    label = { Text(k.label + if (selected) (if (s.ascending) " ↑" else " ↓") else "") },
                )
            }
        }
        when {
            s.loading && s.coins.isEmpty() -> LoadingState()
            s.error != null && s.coins.isEmpty() -> ErrorState(s.error!!, onRetry = { vm.refresh() })
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item { StatusBanner(s.fetchedAt, s.stale, s.offline, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(list, key = { it.id }) { coin ->
                    CoinRow(coin, s.currency, "CRYPTO:${coin.id}" in favs,
                        onFavorite = { vm.toggleFavorite("CRYPTO:${coin.id}") },
                        onClick = { onOpenCoin(coin.id) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (s.remoteResults.isNotEmpty()) {
                    item { Text("Outros resultados", Modifier.padding(16.dp, 12.dp), style = MaterialTheme.typography.titleSmall) }
                    items(s.remoteResults, key = { "r_" + it.id }) { a ->
                        AssetRow(a.name, a.symbol, "ver detalhes", null, emptyList(), false, null) { onOpenCoin(a.id) }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (list.isEmpty() && s.remoteResults.isEmpty()) {
                    item { EmptyState("Nenhum ativo encontrado.") }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: FavoritesViewModel, onOpenCoin: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Favoritos", actions = {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar favoritos") }
        })
        when {
            favs.isEmpty() -> EmptyState("Você ainda não tem favoritos. Toque na estrela de um ativo para salvá-lo aqui.")
            s.loading && s.coins.isEmpty() && s.fiat.isEmpty() -> LoadingState()
            s.error != null && s.coins.isEmpty() && s.fiat.isEmpty() -> ErrorState(s.error!!, onRetry = { vm.refresh() })
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item { StatusBanner(s.fetchedAt, s.stale, false, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(s.coins, key = { it.id }) { coin ->
                    CoinRow(coin, s.currency, true, onFavorite = { vm.toggle("CRYPTO:${coin.id}") }, onClick = { onOpenCoin(coin.id) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                items(s.fiat, key = { "f_" + it.code }) { f ->
                    FiatRow(f, s.currency, true, onFavorite = { vm.toggle("FIAT:${f.code}") })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
