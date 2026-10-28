package com.cambiopro.app.data

import com.cambiopro.app.data.local.CacheEntry
import com.cambiopro.app.data.local.CacheStore
import com.cambiopro.app.data.remote.ChartDto
import com.cambiopro.app.data.remote.CoinGeckoApi
import com.cambiopro.app.data.remote.FrankfurterApi
import com.cambiopro.app.data.remote.MarketDto
import com.cambiopro.app.data.remote.RatesDto
import com.cambiopro.app.data.remote.SearchDto
import com.cambiopro.app.data.remote.SeriesDto
import com.cambiopro.app.domain.DataUnavailableException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

private class MemoryCache : CacheStore {
    val map = mutableMapOf<String, CacheEntry>()
    override suspend fun get(key: String) = map[key]
    override suspend fun put(key: String, entry: CacheEntry) { map[key] = entry }
}

private class FakeGecko : CoinGeckoApi {
    var marketsCalls = 0
    var marketsImpl: suspend () -> List<MarketDto> = { listOf(btcDto()) }
    var chartImpl: suspend () -> ChartDto = { ChartDto(listOf(listOf(1.0, 10.0), listOf(2.0, 11.0))) }

    override suspend fun markets(
        vsCurrency: String, order: String, perPage: Int, page: Int,
        sparkline: Boolean, priceChange: String, ids: String?,
    ): List<MarketDto> { marketsCalls++; return marketsImpl() }

    override suspend fun chart(id: String, vsCurrency: String, days: String) = chartImpl()
    override suspend fun search(query: String) = SearchDto()
    override suspend fun simplePrice(ids: String, vsCurrencies: String): Map<String, Map<String, Double?>> = emptyMap()
}

private class FakeFx : FrankfurterApi {
    override suspend fun latest(base: String, symbols: String?) = RatesDto("USD", "2026-01-02", mapOf("BRL" to 5.0, "EUR" to 0.9))
    override suspend fun currencies(): Map<String, String> = mapOf("USD" to "US Dollar", "BRL" to "Brazilian Real")
    override suspend fun series(range: String, base: String, symbols: String) = SeriesDto()
}

private fun btcDto(price: Double? = 300_000.0) = MarketDto(
    id = "bitcoin", symbol = "btc", name = "Bitcoin", currentPrice = price, change24h = 1.5,
)

class MarketRepositoryTest {
    private val ttl = MarketRepository.MARKETS_TTL
    private var now = 1_000_000L
    private val gecko = FakeGecko()
    private val cache = MemoryCache()
    private fun repo() = MarketRepository(gecko, FakeFx(), cache, clock = { now }, retryDelayMs = 10)

    @Test fun parsesAndCachesMarkets() = runTest {
        val q = repo().markets("brl")
        assertEquals("BTC", q.data.single().symbol)
        assertEquals(300_000.0, q.data.single().price, 0.0)
        assertFalse(q.stale)
        assertNotNull(cache.map["markets_brl"])
    }

    @Test fun freshCacheAvoidsSecondNetworkCall() = runTest {
        val r = repo()
        r.markets("brl")
        now += 60_000 // dentro do TTL (2 min)
        r.markets("brl")
        assertEquals(1, gecko.marketsCalls)
    }

    @Test fun forcedRefreshWithinMinimumIntervalIsIgnored() = runTest {
        val r = repo()
        r.markets("brl")
        now += 5_000
        r.markets("brl", force = true)
        assertEquals(1, gecko.marketsCalls)
    }

    @Test fun refreshesAfterTtl() = runTest {
        val r = repo()
        r.markets("brl")
        now += ttl + 1
        r.markets("brl")
        assertEquals(2, gecko.marketsCalls)
    }

    @Test fun networkFailureFallsBackToStaleCache() = runTest {
        val r = repo()
        val first = r.markets("brl")
        now += ttl + 1
        gecko.marketsImpl = { throw IOException("offline") }
        val second = r.markets("brl")
        assertTrue(second.stale)
        assertEquals(first.fetchedAt, second.fetchedAt) // mostra a data da cotação antiga, não a de agora
        assertEquals(300_000.0, second.data.single().price, 0.0)
    }

    @Test fun networkFailureWithoutCacheThrowsFriendlyError() = runTest {
        gecko.marketsImpl = { throw IOException("offline") }
        try {
            repo().markets("brl")
            fail("deveria lançar")
        } catch (e: DataUnavailableException) {
            assertTrue(e.message!!.contains("conexão"))
        }
        assertEquals(3, gecko.marketsCalls) // 1 tentativa + 2 novas tentativas controladas
    }

    @Test fun retriesTransientFailureThenSucceeds() = runTest {
        var n = 0
        gecko.marketsImpl = { if (++n < 3) throw IOException("timeout") else listOf(btcDto()) }
        val q = repo().markets("brl")
        assertEquals(3, gecko.marketsCalls)
        assertFalse(q.stale)
        assertEquals(1, q.data.size)
    }

    @Test fun invalidResponseIsRejectedAndDoesNotPoisonCache() = runTest {
        gecko.marketsImpl = { listOf(btcDto(price = null), btcDto(price = 0.0), btcDto(price = Double.NaN)) }
        try {
            repo().markets("brl")
            fail("deveria lançar")
        } catch (e: DataUnavailableException) {
            assertTrue(e.message!!.contains("inválida"))
        }
        assertNull(cache.map["markets_brl"])
    }

    @Test fun invalidItemsAreDroppedButValidOnesKept() = runTest {
        gecko.marketsImpl = { listOf(btcDto(price = null), btcDto()) }
        assertEquals(1, repo().markets("brl").data.size)
    }

    @Test fun corruptCacheIsIgnored() = runTest {
        cache.map["markets_brl"] = CacheEntry(now - 10, "{ isto não é json")
        val q = repo().markets("brl")
        assertEquals(300_000.0, q.data.single().price, 0.0)
        assertEquals(1, gecko.marketsCalls)
    }

    @Test fun identicalConcurrentRequestsAreDeduplicated() = runTest {
        gecko.marketsImpl = { delay(100); listOf(btcDto()) }
        val r = MarketRepository(gecko, FakeFx(), cache, clock = { testScheduler.currentTime }, retryDelayMs = 10)
        val results = (1..3).map { async { r.markets("brl") } }.awaitAll()
        assertEquals(1, gecko.marketsCalls)
        assertEquals(3, results.size)
    }

    @Test fun chartWithTooFewPointsIsUnavailable() = runTest {
        gecko.chartImpl = { ChartDto(listOf(listOf(1.0, 10.0))) }
        try {
            repo().chart("bitcoin", "brl", 7)
            fail("deveria lançar")
        } catch (e: DataUnavailableException) {
            // ok: o gráfico não é inventado
        }
    }

    @Test fun chartDropsNullPoints() = runTest {
        gecko.chartImpl = { ChartDto(listOf(listOf(1.0, 10.0), listOf(2.0, null), listOf(3.0, 12.0))) }
        assertEquals(2, repo().chart("bitcoin", "brl", 7).data.size)
    }

    @Test fun fiatTableAlwaysContainsUsdAsOne() = runTest {
        val t = repo().fiatTable().data
        assertEquals(1.0, t.perUsd["USD"]!!, 0.0)
        assertEquals(5.0, t.perUsd["BRL"]!!, 0.0)
        assertEquals("2026-01-02", t.date)
    }

    @Test fun downsampleKeepsFirstAndLast() {
        val pts = (0 until 1000).map { com.cambiopro.app.domain.PricePoint(it.toLong(), it.toDouble()) }
        val d = MarketRepository.downsample(pts, 400)
        assertEquals(400, d.size)
        assertEquals(0L, d.first().t)
        assertEquals(999L, d.last().t)
    }
}
