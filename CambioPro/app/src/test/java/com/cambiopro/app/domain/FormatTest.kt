package com.cambiopro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun brazilianMoney() {
        assertEquals("R$ 1.234,50", Format.money(1234.5, "BRL"))
        assertEquals("US$ 0,9900", Format.money(0.99, "USD"))
        assertEquals("€ 10,00", Format.money(10.0, "EUR"))
        assertEquals("CHF 3,00", Format.money(3.0, "CHF"))
    }

    @Test fun largeValues() {
        assertEquals("R$ 1.234.567.890,12", Format.money(1_234_567_890.123, "BRL"))
        assertEquals("1,50 bi", Format.compact(1_500_000_000.0))
        assertEquals("2,35 tri", Format.compact(2.346e12))
        assertEquals("4,50 mi", Format.compact(4_500_000.0))
        assertEquals("12,35 mil", Format.compact(12_346.0))
        assertEquals("—", Format.compact(null))
    }

    @Test fun smallValuesGetMoreDecimals() {
        assertEquals("0,00001234", Format.number(0.00001234))
        assertEquals("0,000123", Format.number(0.000123))
        assertEquals("0,0500", Format.number(0.05))
        assertEquals("1,00", Format.number(1.0))
    }

    @Test fun resultDecimalsDependOnAssetKind() {
        assertEquals("1.234,57", Format.result(1234.5678, AssetKind.FIAT))
        assertEquals("0,00123457", Format.result(0.00123456789, AssetKind.CRYPTO))
        assertEquals("1.234,57", Format.result(1234.5678, AssetKind.CRYPTO))
        assertEquals("2,5000", Format.result(2.5, AssetKind.CRYPTO))
    }

    @Test fun percentAndArrowDoNotRelyOnColor() {
        assertEquals("+1,23%", Format.percent(1.234))
        assertEquals("-2,50%", Format.percent(-2.5))
        assertEquals("—", Format.percent(null))
        assertEquals("▲", Format.arrow(0.1))
        assertEquals("▼", Format.arrow(-0.1))
        assertEquals("–", Format.arrow(null))
    }

    @Test fun nonFiniteIsDashed() {
        assertEquals("—", Format.number(Double.NaN))
        assertEquals("—", Format.money(Double.POSITIVE_INFINITY, "BRL"))
    }
}
