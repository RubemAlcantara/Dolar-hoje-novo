@file:OptIn(ExperimentalMaterial3Api::class)

package com.cambiopro.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.cambiopro.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cambiopro.app.data.local.ThemeMode
import com.cambiopro.app.domain.Format
import com.cambiopro.app.ui.HistoryViewModel
import com.cambiopro.app.ui.SettingsViewModel
import com.cambiopro.app.ui.components.AppTopBar
import com.cambiopro.app.ui.components.EmptyState

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val list by vm.history.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Histórico", onBack = onBack, actions = {
            if (list.isNotEmpty()) {
                IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Limpar histórico") }
            }
        })
        if (list.isEmpty()) {
            EmptyState("Nenhuma conversão salva ainda. As conversões são registradas automaticamente quando você para de digitar.")
        } else {
            LazyColumn {
                items(list, key = { it.timestamp.toString() + it.fromKey + it.toKey + it.amount }) { r ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "${Format.result(r.amount, kindOf(r.fromKey))} ${r.fromSymbol} → ${Format.result(r.result, kindOf(r.toKey))} ${r.toSymbol}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Taxa: 1 ${r.fromSymbol} = ${Format.number(r.rate, Format.decimalsFor(r.rate).coerceAtLeast(2))} ${r.toSymbol} • ${Format.dateTime(r.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Todas as conversões salvas serão apagadas deste aparelho.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirm = false }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

private fun kindOf(key: String) =
    if (key.startsWith("CRYPTO:")) com.cambiopro.app.domain.AssetKind.CRYPTO else com.cambiopro.app.domain.AssetKind.FIAT

@Composable
private fun <T> RadioRow(label: String, value: T, current: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = value == current, onClick = { onSelect(value) }, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == current, onClick = null)
        Text(label, Modifier.padding(start = 12.dp).padding(vertical = 8.dp))
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenHistory: () -> Unit, onOpenAbout: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var key by remember(s.geckoKey) { mutableStateOf(s.geckoKey) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Ajustes")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            RadioRow("Seguir o sistema", ThemeMode.SYSTEM, s.theme, vm::setTheme)
            RadioRow("Claro", ThemeMode.LIGHT, s.theme, vm::setTheme)
            RadioRow("Escuro", ThemeMode.DARK, s.theme, vm::setTheme)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Moeda padrão de exibição", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            RadioRow("Real (BRL)", "BRL", s.currency, vm::setCurrency)
            RadioRow("Dólar americano (USD)", "USD", s.currency, vm::setCurrency)
            RadioRow("Euro (EUR)", "EUR", s.currency, vm::setCurrency)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Atualização automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            RadioRow("A cada 15 minutos", 15, s.refreshMinutes, vm::setRefresh)
            RadioRow("A cada 30 minutos", 30, s.refreshMinutes, vm::setRefresh)
            RadioRow("A cada 1 hora", 60, s.refreshMinutes, vm::setRefresh)
            RadioRow("A cada 3 horas", 180, s.refreshMinutes, vm::setRefresh)
            RadioRow("Desativada", 0, s.refreshMinutes, vm::setRefresh)
            Text(
                "Com o app aberto, a atualização segue este intervalo. Em segundo plano o Android impõe mínimo de 15 min e " +
                    "pode adiar a execução para economizar bateria; não há atualização em tempo real garantida.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Chave demo do CoinGecko (opcional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "O app funciona sem chave. Se você tiver uma chave Demo gratuita, ela amplia o limite de requisições. " +
                    "Fica salva apenas neste aparelho.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it }, singleLine = true, label = { Text("Chave (x-cg-demo-api-key)") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.setGeckoKey(key) }, enabled = key.trim() != s.geckoKey) { Text("Salvar chave") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Histórico de conversões", Modifier.fillMaxWidth().clickable(onClick = onOpenHistory).padding(vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall)
            Text("Sobre o aplicativo", Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Sobre", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo_cambio), contentDescription = "Logo do Câmbio Pro", modifier = Modifier.size(120.dp))
            Text("Câmbio Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Versão $version")
            Text("Fontes de dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("• Criptomoedas: CoinGecko (API pública sem chave, com limites por IP). https://www.coingecko.com")
            Text("• Moedas tradicionais: Frankfurter v1, dados de referência do Banco Central Europeu, atualizados uma vez por dia útil. https://frankfurter.dev")
            Text("Avisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "As cotações são informativas e de referência. Não representam preços garantidos de compra ou venda, " +
                    "cotação bancária, dólar turismo nem recomendação de investimento. Dados em cache aparecem com a data da " +
                    "última atualização e um aviso quando estão desatualizados.",
            )
            Text("Privacidade: o app não pede cadastro nem coleta dados pessoais. Favoritos, histórico e preferências ficam apenas no aparelho.")
        }
    }
}
