@file:OptIn(ExperimentalMaterial3Api::class)

package com.cambiopro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cambiopro.app.domain.Asset
import com.cambiopro.app.domain.AssetKind
import com.cambiopro.app.domain.ConversionOutcome
import com.cambiopro.app.domain.FailReason
import com.cambiopro.app.domain.Format
import com.cambiopro.app.domain.RateKind
import com.cambiopro.app.ui.ConverterViewModel
import com.cambiopro.app.ui.components.AppTopBar
import com.cambiopro.app.ui.components.StatusBanner
import com.cambiopro.app.ui.components.shareText

private fun failMessage(r: FailReason): String = when (r) {
    FailReason.EMPTY -> "Digite um valor para converter."
    FailReason.INVALID -> "Valor inválido. Use apenas números, vírgula ou ponto."
    FailReason.NEGATIVE -> "Informe um valor maior ou igual a zero."
    FailReason.RATE_UNAVAILABLE -> "Cotação indisponível para este par no momento."
    FailReason.OVERFLOW -> "O resultado é grande demais para ser exibido."
}

private fun sourceText(kind: RateKind, fiatDate: String?): String = when (kind) {
    RateKind.IDENTITY -> "Mesma moeda: taxa 1."
    RateKind.FIAT_REFERENCE ->
        "Câmbio de referência (Frankfurter/BCE)${if (fiatDate != null) ", dados de $fiatDate" else ""}. " +
            "Não é cotação de compra/venda de banco nem dólar turismo."
    RateKind.CRYPTO_DIRECT -> "Preço direto do CoinGecko."
    RateKind.CRYPTO_CROSS -> "Taxa cruzada via USD (CoinGecko + Frankfurter quando há moeda tradicional). Valor aproximado de referência."
}

@Composable
fun ConverterScreen(vm: ConverterViewModel, onOpenHistory: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = origem, false = destino

    Column(Modifier.fillMaxSize().imePadding()) {
        AppTopBar("Conversor", actions = {
            IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = "Histórico de conversões") }
            IconButton(onClick = { vm.refreshRates(true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Atualizar taxas") }
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(s.ratesAt, s.stale, s.offline)
            if (s.loadingRates) LinearProgressIndicator(Modifier.fillMaxWidth())

            AssetSelector("De", s.from) { picking = true }
            val outcome = s.outcome
            OutlinedTextField(
                value = s.amountText,
                onValueChange = vm::setAmount,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Valor em ${s.from.symbol}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = outcome is ConversionOutcome.Fail &&
                    outcome.reason in setOf(FailReason.INVALID, FailReason.NEGATIVE, FailReason.EMPTY),
                supportingText = {
                    if (outcome is ConversionOutcome.Fail &&
                        outcome.reason in setOf(FailReason.INVALID, FailReason.NEGATIVE, FailReason.EMPTY)
                    ) Text(failMessage(outcome.reason))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1", "10", "100", "1000").forEach { v ->
                    AssistChip(onClick = { vm.setAmount(v) }, label = { Text(if (v == "1000") "1.000" else v) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilledTonalIconButton(onClick = vm::swap, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Inverter origem e destino")
                }
            }
            AssetSelector("Para", s.to) { picking = false }

            when (outcome) {
                is ConversionOutcome.Ok -> {
                    val resultText = "${Format.result(outcome.result, s.to.kind)} ${s.to.symbol}"
                    val rateText = "1 ${s.from.symbol} = ${Format.number(outcome.rate, Format.decimalsFor(outcome.rate).coerceAtLeast(2))} ${s.to.symbol}"
                    val stamp = s.ratesAt?.let { Format.dateTime(it) } ?: "—"
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Resultado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(resultText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Taxa: $rateText", style = MaterialTheme.typography.bodyMedium)
                            Text("Cotação de $stamp", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sourceText(outcome.kind, s.fiatDate), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(Format.result(outcome.result, s.to.kind))) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Resultado")
                                }
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(rateText)) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Taxa")
                                }
                                OutlinedButton(onClick = {
                                    shareText(ctx, "${Format.result(outcome.amount, s.from.kind)} ${s.from.symbol} = $resultText ($rateText) — $stamp. Câmbio Pro")
                                }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Compartilhar", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                is ConversionOutcome.Fail -> if (outcome.reason !in setOf(FailReason.INVALID, FailReason.NEGATIVE, FailReason.EMPTY)) {
                    Text(failMessage(outcome.reason), color = MaterialTheme.colorScheme.error)
                }
                null -> Unit
            }
            s.rateMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.refreshRates(true) }) { Text("Tentar novamente") }
            }
        }
    }

    picking?.let { forFrom ->
        AssetPickerDialog(
            assets = s.assets,
            remote = s.remoteAssets,
            onQuery = vm::searchRemote,
            onPick = { a -> if (forFrom) vm.setFrom(a) else vm.setTo(a); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun AssetSelector(label: String, asset: Asset, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${asset.symbol} · ${asset.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Escolher ativo")
        }
    }
}

@Composable
private fun AssetPickerDialog(
    assets: List<Asset>,
    remote: List<Asset>,
    onQuery: (String) -> Unit,
    onPick: (Asset) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val term = q.trim().lowercase()
    val filtered = if (term.isEmpty()) assets
    else assets.filter { it.symbol.lowercase().contains(term) || it.name.lowercase().contains(term) }
    val all = filtered + if (term.length >= 2) remote else emptyList()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
        ) {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it; onQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    singleLine = true,
                    label = { Text("Buscar moeda ou criptomoeda") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
                if (all.isEmpty()) {
                    Text("Nada encontrado.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(all, key = { it.key }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(a) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(a.symbol, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), maxLines = 1)
                            Column(Modifier.weight(1f)) {
                                Text(a.name, maxLines = 1)
                                Text(
                                    if (a.kind == AssetKind.FIAT) "Moeda" else "Criptomoeda",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
