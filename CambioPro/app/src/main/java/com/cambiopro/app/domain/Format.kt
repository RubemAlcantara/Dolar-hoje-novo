package com.cambiopro.app.domain

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Formatação pt-BR. Usa espaço comum (não NBSP) entre prefixo e número. */
object Format {
    private val BR = Locale("pt", "BR")

    private fun df(decimals: Int): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols(BR)).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = true
        }

    /** Casas decimais para exibir um preço: preços pequenos exigem mais casas. */
    fun decimalsFor(v: Double): Int {
        val a = abs(v)
        return when {
            a >= 1.0 || a == 0.0 -> 2
            a >= 0.01 -> 4
            a >= 0.0001 -> 6
            else -> 8
        }
    }

    /** Casas decimais para o resultado de uma conversão, conforme o tipo do ativo de destino. */
    fun resultDecimals(v: Double, kind: AssetKind): Int {
        val a = abs(v)
        return when (kind) {
            AssetKind.FIAT -> if (a >= 0.01 || a == 0.0) 2 else 6
            AssetKind.CRYPTO -> when {
                a >= 1000.0 -> 2
                a >= 1.0 -> 4
                a >= 0.01 -> 6
                else -> 8
            }
        }
    }

    fun number(v: Double, decimals: Int = decimalsFor(v)): String =
        if (!v.isFinite()) "—" else df(decimals).format(v)

    fun result(v: Double, kind: AssetKind): String = number(v, resultDecimals(v, kind))

    private fun prefix(code: String): String = when (code.uppercase()) {
        "BRL" -> "R$"
        "USD" -> "US$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        else -> code.uppercase()
    }

    fun money(v: Double, currency: String): String =
        if (!v.isFinite()) "—" else "${prefix(currency)} ${number(v)}"

    fun percent(p: Double?): String {
        if (p == null || !p.isFinite()) return "—"
        val sign = if (p > 0) "+" else ""
        return "$sign${number(p, 2)}%"
    }

    /** Indicador que não depende de cor. */
    fun arrow(p: Double?): String = when {
        p == null || !p.isFinite() -> "–"
        p > 0 -> "▲"
        p < 0 -> "▼"
        else -> "■"
    }

    /** Valores grandes (capitalização, volume): 1,23 bi, 4,50 mi etc. */
    fun compact(v: Double?): String {
        if (v == null || !v.isFinite()) return "—"
        val a = abs(v)
        return when {
            a >= 1e12 -> "${number(v / 1e12, 2)} tri"
            a >= 1e9 -> "${number(v / 1e9, 2)} bi"
            a >= 1e6 -> "${number(v / 1e6, 2)} mi"
            a >= 1e3 -> "${number(v / 1e3, 2)} mil"
            else -> number(v, 2)
        }
    }

    fun compactMoney(v: Double?, currency: String): String =
        if (v == null || !v.isFinite()) "—" else "${prefix(currency)} ${compact(v)}"

    fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", BR).format(Date(millis))

    fun dayMonth(millis: Long): String = SimpleDateFormat("dd/MM", BR).format(Date(millis))

    fun hourMinute(millis: Long): String = SimpleDateFormat("HH:mm", BR).format(Date(millis))
}
