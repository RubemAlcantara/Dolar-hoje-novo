package com.cambiopro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Todas as taxas abaixo são FIXAS de teste; os resultados esperados não dependem da cotação real do dia. */
class ConverterTest {
    private val usd = Asset.fiat("USD")
    private val brl = Asset.fiat("BRL")
    private val eur = Asset.fiat("EUR")
    private val btc = Asset.crypto("bitcoin", "btc", "Bitcoin")
    private val eth = Asset.crypto("ethereum", "eth", "Ethereum")
    private val dead = Asset.crypto("deadcoin", "ded", "Dead")
    private val unknown = Asset.crypto("unknown", "unk", "Unknown")

    private val book = RateBook(
        cryptoUsd = mapOf("bitcoin" to 60_000.0, "ethereum" to 3_000.0, "deadcoin" to 0.0),
        // BRL direto propositalmente diferente de USD*5,5 para provar que a cotação direta é usada.
        cryptoBrl = mapOf("bitcoin" to 330_000.0, "ethereum" to 16_500.0),
        fiatPerUsd = mapOf("USD" to 1.0, "BRL" to 5.5, "EUR" to 0.9),
    )

    private fun ok(o: ConversionOutcome) = o as ConversionOutcome.Ok

    @Test fun usdToBrl() = assertEquals(550.0, ok(Converter.convert(100.0, usd, brl, book)).result, 1e-9)
    @Test fun brlToUsd() = assertEquals(100.0, ok(Converter.convert(550.0, brl, usd, book)).result, 1e-9)
    @Test fun eurToBrl() = assertEquals(61.1111111111, ok(Converter.convert(10.0, eur, brl, book)).result, 1e-6)

    @Test fun cryptoToCrypto() {
        val o = ok(Converter.convert(1.0, btc, eth, book))
        assertEquals(20.0, o.result, 1e-9)
        assertEquals(RateKind.CRYPTO_CROSS, o.kind)
    }

    @Test fun cryptoToBrlUsesDirectPrice() {
        val o = ok(Converter.convert(0.5, btc, brl, book))
        assertEquals(165_000.0, o.result, 1e-6)
        assertEquals(RateKind.CRYPTO_DIRECT, o.kind)
    }

    @Test fun brlToCrypto() = assertEquals(0.1, ok(Converter.convert(33_000.0, brl, btc, book)).result, 1e-12)

    @Test fun cryptoToOtherFiatCrossesViaUsd() {
        val o = ok(Converter.convert(1.0, btc, eur, book))
        assertEquals(54_000.0, o.result, 1e-6)
        assertEquals(RateKind.CRYPTO_CROSS, o.kind)
    }

    @Test fun invertingPairGivesReciprocalRate() {
        val pairs = listOf(usd to brl, eur to brl, btc to eth, btc to brl, btc to eur)
        for ((a, b) in pairs) {
            val ab = Converter.rate(a, b, book)!!
            val ba = Converter.rate(b, a, book)!!
            assertEquals("$a/$b", 1.0, ab * ba, 1e-9)
        }
    }

    @Test fun zeroAmountGivesZero() {
        val o = ok(Converter.convert(0.0, usd, brl, book))
        assertEquals(0.0, o.result, 0.0)
    }

    @Test fun negativeAmountIsRejected() =
        assertEquals(ConversionOutcome.Fail(FailReason.NEGATIVE), Converter.convert(-1.0, usd, brl, book))

    @Test fun emptyOrBlankFieldIsRejected() {
        assertEquals(ConversionOutcome.Fail(FailReason.EMPTY), Converter.convertText("", usd, brl, book))
        assertEquals(ConversionOutcome.Fail(FailReason.EMPTY), Converter.convertText("   ", usd, brl, book))
    }

    @Test fun garbageIsInvalid() {
        for (t in listOf("abc", "1e5", "--1", "1,2,3a", "NaN", "Infinity", "-", ",", "."))
            assertEquals(t, ConversionOutcome.Fail(FailReason.INVALID), Converter.convertText(t, usd, brl, book))
    }

    @Test fun nonFiniteAmountIsInvalid() {
        assertEquals(ConversionOutcome.Fail(FailReason.INVALID), Converter.convert(Double.NaN, usd, brl, book))
        assertEquals(ConversionOutcome.Fail(FailReason.INVALID), Converter.convert(Double.POSITIVE_INFINITY, usd, brl, book))
    }

    @Test fun decimalParsingAcceptsBrazilianAndInternationalFormats() {
        assertEquals(1.5, Converter.parseAmount("1,5")!!, 0.0)
        assertEquals(1.5, Converter.parseAmount("1.5")!!, 0.0)
        assertEquals(1234.56, Converter.parseAmount("1.234,56")!!, 1e-9)
        assertEquals(1234.56, Converter.parseAmount("1,234.56")!!, 1e-9)
        assertEquals(1_234_567.0, Converter.parseAmount("1.234.567")!!, 0.0)
        assertEquals(0.00012345, Converter.parseAmount("0,00012345")!!, 1e-12)
        assertEquals(-3.0, Converter.parseAmount("-3")!!, 0.0)
    }

    @Test fun assetWithoutQuoteIsUnavailable() {
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, unknown, usd, book))
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, usd, unknown, book))
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, unknown, btc, book))
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, usd, brl, RateBook()))
    }

    @Test fun zeroPriceNeverDividesByZero() {
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, usd, dead, book))
        assertEquals(ConversionOutcome.Fail(FailReason.RATE_UNAVAILABLE), Converter.convert(1.0, dead, btc, book))
        assertNull(Converter.rate(btc, dead, book))
    }

    @Test fun overflowIsReportedInsteadOfInfinity() =
        assertEquals(ConversionOutcome.Fail(FailReason.OVERFLOW), Converter.convert(1e308, usd, brl, book))

    @Test fun sameAssetHasRateOne() {
        val o = ok(Converter.convert(42.0, btc, btc, RateBook()))
        assertEquals(42.0, o.result, 0.0)
        assertEquals(RateKind.IDENTITY, o.kind)
    }

    @Test fun convertTextEndToEnd() {
        val o = Converter.convertText("1.000,50", usd, brl, book)
        assertNotNull(o)
        assertTrue(o is ConversionOutcome.Ok)
        assertEquals(5502.75, (o as ConversionOutcome.Ok).result, 1e-9)
    }
}
