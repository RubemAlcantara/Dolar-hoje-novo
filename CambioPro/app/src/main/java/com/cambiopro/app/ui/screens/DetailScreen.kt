@file:OptIn(ExperimentalMaterial3Api::class)

package com.cambiopro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cambiopro.app.domain.Format
import com.cambiopro.app.ui.DetailViewModel
import com.cambiopro.app.ui.components.AppTopBar
import com.cambiopro.app.ui.components.ChangeText
import com.cambiopro.app.ui.components.ErrorState
import com.cambiopro.app.ui.components.LineChart
import com.cambiopro.app.ui.components.LoadingState
import com.cambiopro.app.ui.components.StatusBanner
import com.cambiopro.app.ui.components.shareText

private val PERIODS = listOf("24 h" to 1, "7 dias" to 7, "30 dias" to 30, "90 dias" to 90, "1 ano" to 365)

@Composable
fun DetailScreen(vm: DetailViewModel, coinId: String, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val coin = s.coin
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val isFav = "CRYPTO:$coinId" in favs

    Column(Modifier.fillMaxSize()) {
        AppTopBar(coin?.name ?: "Detalhes", onBack = onBack, actions = {
            IconButton(onClick = { vm.toggleFavorite() }) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos",
                    tint = if (isFav) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (coin != null) {
                IconButton(onClick = {
                    shareText(
                        ctx,
                        "${coin.name} (${coin.symbol}): ${Format.money(coin.price, s.currency)} • 24 h: ${Format.percent(coin.change24hPct)} " +
                            "• CoinGecko, ${s.coinAt?.let { Format.dateTime(it) } ?: "—"}. Câmbio Pro",
                    )
                }) { Icon(Icons.Filled.Share, contentDescription = "Compartilhar cotação") }
            }
        })
        when {
            s.coinLoading && coin == null -> LoadingState()
            coin == null -> ErrorState(s.coinError ?: "Ativo indisponível.", onRetry = { vm.load(true) })
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusBanner(s.coinAt, s.coinStale, false)
                Column {
                    Text(coin.symbol, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.money(coin.price, s.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    ChangeText(coin.change24hPct)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PERIODS) { (label, days) ->
                        FilterChip(selected = s.days == days, onClick = { vm.setDays(days) }, label = { Text(label) })
                    }
                }
                if (s.chartLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                when {
                    s.points.size >= 2 -> LineChart(
                        points = s.points,
                        formatValue = { Format.money(it, s.currency) },
                        formatTime = { Format.dateTime(it) },
                    )
                    !s.chartLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.chartError ?: "Histórico indisponível para este período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { vm.setDays(s.days) }) { Text("Tentar novamente") }
                        }
                    }
                }
                HorizontalDivider()
                StatRow("Capitalização de mercado", Format.compactMoney(coin.marketCap, s.currency))
                StatRow("Volume em 24 h", Format.compactMoney(coin.volume24h, s.currency))
                StatRow("Máxima em 24 h", coin.high24h?.let { Format.money(it, s.currency) } ?: "—")
                StatRow("Mínima em 24 h", coin.low24h?.let { Format.money(it, s.currency) } ?: "—")
                StatRow("Variação em 24 h", "${Format.arrow(coin.change24hPct)} ${Format.percent(coin.change24hPct)}")
                StatRow("Posição no ranking", coin.rank?.let { "#$it" } ?: "—")
                StatRow("Atualizado pela fonte", coin.updatedAtMillis?.let { Format.dateTime(it) } ?: "—")
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(Format.money(coin.price, s.currency))) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Copiar preço")
                }
                Text("Fonte: CoinGecko. Informativo; não é recomendação de investimento.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
