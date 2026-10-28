package com.cambiopro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryLogicTest {
    private fun rec(i: Int, amount: Double = i.toDouble()) =
        ConversionRecord("FIAT:USD", "FIAT:BRL", "USD", "BRL", amount, amount * 5, 5.0, i.toLong())

    @Test fun newestFirstAndLimitedTo100() {
        var list = emptyList<ConversionRecord>()
        for (i in 1..130) list = HistoryLogic.add(list, rec(i))
        assertEquals(100, list.size)
        assertEquals(130L, list.first().timestamp)
        assertEquals(31L, list.last().timestamp)
    }

    @Test fun identicalConsecutiveEntryReplacesPreviousOne() {
        var list = HistoryLogic.add(emptyList(), rec(1, 10.0))
        list = HistoryLogic.add(list, rec(2, 10.0))
        assertEquals(1, list.size)
        assertEquals(2L, list.first().timestamp)
    }

    @Test fun differentAmountIsKept() {
        var list = HistoryLogic.add(emptyList(), rec(1, 10.0))
        list = HistoryLogic.add(list, rec(2, 11.0))
        assertEquals(2, list.size)
    }
}
