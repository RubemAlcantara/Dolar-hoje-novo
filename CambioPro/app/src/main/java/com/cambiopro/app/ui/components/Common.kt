@file:OptIn(ExperimentalMaterial3Api::class)

package com.cambiopro.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cambiopro.app.domain.Format
import com.cambiopro.app.domain.PricePoint
import com.cambiopro.app.ui.theme.changeColor
import com.cambiopro.app.ui.theme.downColor
import com.cambiopro.app.ui.theme.upColor
import kotlin.math.roundToInt

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar cotação"))
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Tentar novamente") }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp))
}

/** Mostra data/hora da última atualização e avisa quando o dado é antigo ou veio do cache sem conexão. */
@Composable
fun StatusBanner(fetchedAt: Long?, stale: Boolean, offline: Boolean, modifier: Modifier = Modifier) {
    if (fetchedAt == null && !offline) return
    val old = fetchedAt != null && System.currentTimeMillis() - fetchedAt > 15 * 60_000L
    val warn = stale || offline || old
    val text = when {
        fetchedAt == null -> "Sem conexão e sem dados salvos."
        stale || offline -> "Sem atualização desde ${Format.dateTime(fetchedAt)}. Exibindo a última cotação conhecida; ela pode estar desatualizada."
        old -> "Cotação de ${Format.dateTime(fetchedAt)} (já tem mais de 15 min)."
        else -> "Atualizado em ${Format.dateTime(fetchedAt)}"
    }
    Surface(
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warn) Icons.Filled.CloudOff else Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ChangeText(p: Double?, modifier: Modifier = Modifier) {
    Text(
        text = if (p == null) "– sem variação" else "${Format.arrow(p)} ${Format.percent(p)}",
        color = changeColor(p),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun Avatar(symbol: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol.take(4), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable
fun AssetRow(
    title: String,
    symbol: String,
    price: String,
    change: Double?,
    spark: List<Double>,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showSpark = maxWidth > 340.dp && spark.size > 1
        val base = Modifier.fillMaxWidth()
        Row(
            (if (onClick != null) base.clickable(onClick = onClick) else base)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(symbol)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(symbol, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (showSpark) {
                Sparkline(spark, Modifier.width(64.dp).height(30.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                ChangeText(change)
            }
            if (onFavorite != null) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                        tint = if (favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val up = values.last() >= values.first()
    val color = if (up) upColor() else downColor()
    val desc = if (up) "Gráfico resumido: tendência de alta" else "Gráfico resumido: tendência de baixa"
    Canvas(modifier.semantics { contentDescription = desc }) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - ((v - min) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Gráfico de linha interativo (toque/arraste para ver o valor de um ponto). Só desenha dados recebidos. */
@Composable
fun LineChart(
    points: List<PricePoint>,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val up = points.last().v >= points.first().v
    val color = if (up) upColor() else downColor()
    val sel = selected?.let { points.getOrNull(it) }
    val min = points.minOf { it.v }
    val max = points.maxOf { it.v }

    Column(modifier) {
        Text(
            text = if (sel != null) "${formatTime(sel.t)} • ${formatValue(sel.v)}"
            else "Toque ou arraste no gráfico para ver os valores",
            style = MaterialTheme.typography.labelLarge,
            color = if (sel != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(200.dp)
                .semantics { contentDescription = "Gráfico de preço de ${formatTime(points.first().t)} a ${formatTime(points.last().t)}" }
                .pointerInput(points) {
                    detectTapGestures { o ->
                        selected = ((o.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { o ->
                            selected = ((o.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                        },
                        onDrag = { change, _ ->
                            selected = ((change.position.x / size.width) * (points.size - 1)).roundToInt()
                                .coerceIn(0, points.lastIndex)
                        },
                    )
                },
        ) {
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            fun px(i: Int) = size.width * i / (points.size - 1)
            fun py(v: Double) = size.height - ((v - min) / range * size.height).toFloat()
            val line = Path()
            points.forEachIndexed { i, p -> if (i == 0) line.moveTo(px(i), py(p.v)) else line.lineTo(px(i), py(p.v)) }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            selected?.let { i ->
                val p = points.getOrNull(i) ?: return@let
                drawLine(color.copy(alpha = 0.5f), Offset(px(i), 0f), Offset(px(i), size.height), strokeWidth = 1.dp.toPx())
                drawCircle(color, radius = 5.dp.toPx(), center = Offset(px(i), py(p.v)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mín: ${formatValue(min)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Máx: ${formatValue(max)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
