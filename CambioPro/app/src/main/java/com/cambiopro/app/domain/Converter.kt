package com.cambiopro.app.domain

/**
 * Taxas usadas nas conversões.
 * - cryptoUsd / cryptoBrl: preço de 1 unidade do ativo (por id do CoinGecko).
 * - fiatPerUsd: quantas unidades da moeda existem em 1 USD (USD = 1.0).
 */
data class RateBook(
    val cryptoUsd: Map<String, Double> = emptyMap(),
    val cryptoBrl: Map<String, Double> = emptyMap(),
    val fiatPerUsd: Map<String, Double> = emptyMap(),
)

enum class RateKind { IDENTITY, FIAT_REFERENCE, CRYPTO_DIRECT, CRYPTO_CROSS }

enum class FailReason { EMPTY, INVALID, NEGATIVE, RATE_UNAVAILABLE, OVERFLOW }

sealed interface ConversionOutcome {
    data class Ok(
        val amount: Double,
        val result: Double,
        val rate: Double,
        val kind: RateKind,
    ) : ConversionOutcome

    data class Fail(val reason: FailReason) : ConversionOutcome
}

object Converter {
    private val NUMERIC = Regex("^-?[0-9.,]+$")

    /**
     * Interpreta um valor digitado.
     * "1,5" e "1.5" -> 1.5; "1.234,56" e "1,234.56" -> 1234.56; "1.234.567" -> 1234567.
     * Retorna null se o texto não for um número finito.
     */
    fun parseAmount(text: String): Double? {
        val s0 = text.trim().replace(" ", "").replace("\u00A0", "")
        if (s0.isEmpty() || !NUMERIC.matches(s0)) return null
        val hasComma = s0.contains(',')
        val hasDot = s0.contains('.')
        val s = when {
            hasComma && hasDot ->
                if (s0.lastIndexOf(',') > s0.lastIndexOf('.')) s0.replace(".", "").replace(',', '.')
                else s0.replace(",", "")
            hasComma -> if (s0.count { it == ',' } == 1) s0.replace(',', '.') else s0.replace(",", "")
            hasDot -> if (s0.count { it == '.' } == 1) s0 else s0.replace(".", "")
            else -> s0
        }
        return s.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun convertText(text: String, from: Asset, to: Asset, book: RateBook): ConversionOutcome {
        if (text.isBlank()) return ConversionOutcome.Fail(FailReason.EMPTY)
        val amount = parseAmount(text) ?: return ConversionOutcome.Fail(FailReason.INVALID)
        return convert(amount, from, to, book)
    }

    fun convert(amount: Double, from: Asset, to: Asset, book: RateBook): ConversionOutcome {
        if (!amount.isFinite()) return ConversionOutcome.Fail(FailReason.INVALID)
        if (amount < 0) return ConversionOutcome.Fail(FailReason.NEGATIVE)
        val (rate, kind) = rateWithKind(from, to, book)
            ?: return ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE)
        val result = amount * rate
        if (!result.isFinite()) return ConversionOutcome.Fail(FailReason.OVERFLOW)
        return ConversionOutcome.Ok(amount, result, rate, kind)
    }

    fun rate(from: Asset, to: Asset, book: RateBook): Double? = rateWithKind(from, to, book)?.first

    /** Taxa para converter 1 unidade de [from] em [to], ou null se não houver dados confiáveis. */
    fun rateWithKind(from: Asset, to: Asset, b: RateBook): Pair<Double, RateKind>? {
        if (from.key == to.key) return 1.0 to RateKind.IDENTITY
        val candidate: Pair<Double?, RateKind> = when {
            from.kind == AssetKind.FIAT && to.kind == AssetKind.FIAT ->
                ratio(b.fiatPerUsd[to.id], b.fiatPerUsd[from.id]) to RateKind.FIAT_REFERENCE

            from.kind == AssetKind.CRYPTO && to.kind == AssetKind.FIAT ->
                cryptoInFiat(from.id, to.id, b)

            from.kind == AssetKind.FIAT && to.kind == AssetKind.CRYPTO -> {
                val (price, kind) = cryptoInFiat(to.id, from.id, b)
                ratio(1.0, price) to kind
            }

            else -> ratio(b.cryptoUsd[from.id], b.cryptoUsd[to.id]) to RateKind.CRYPTO_CROSS
        }
        val r = candidate.first ?: return null
        return if (r.isFinite() && r > 0.0) r to candidate.second else null
    }

    private fun ratio(numerator: Double?, denominator: Double?): Double? {
        if (numerator == null || denominator == null) return null
        if (!numerator.isFinite() || !denominator.isFinite()) return null
        if (numerator <= 0.0 || denominator <= 0.0) return null
        return numerator / denominator
    }

    /** Preço de 1 unidade da cripto [id] na moeda [code]. BRL e USD vêm direto da API; as demais cruzam via USD. */
    private fun cryptoInFiat(id: String, code: String, b: RateBook): Pair<Double?, RateKind> = when (code) {
        "BRL" -> b.cryptoBrl[id] to RateKind.CRYPTO_DIRECT
        "USD" -> b.cryptoUsd[id] to RateKind.CRYPTO_DIRECT
        else -> {
            val usd = b.cryptoUsd[id]
            val perUsd = b.fiatPerUsd[code]
            val v = if (usd == null || perUsd == null || usd <= 0.0 || perUsd <= 0.0) null else usd * perUsd
            v to RateKind.CRYPTO_CROSS
        }
    }
}
