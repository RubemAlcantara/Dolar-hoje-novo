package com.cambiopro.app.data

import com.cambiopro.app.data.local.CacheEntry
import com.cambiopro.app.data.local.CacheStore
import com.cambiopro.app.data.remote.CoinGeckoApi
import com.cambiopro.app.data.remote.FrankfurterApi
import com.cambiopro.app.data.remote.MarketDto
import com.cambiopro.app.data.remote.Network
import com.cambiopro.app.domain.ApiFormatException
import com.cambiopro.app.domain.Asset
import com.cambiopro.app.domain.DataUnavailableException
import com.cambiopro.app.domain.FiatTable
import com.cambiopro.app.domain.MarketCoin
import com.cambiopro.app.domain.PricePoint
import com.cambiopro.app.domain.Quoted
import com.cambiopro.app.domain.Spot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Fonte única de dados. Regras:
 * - cache em disco com TTL por tipo de dado;
 * - requisições idênticas simultâneas são deduplicadas (mutex por chave);
 * - refresh forçado é ignorado se o dado tem menos de [MIN_INTERVAL_MS] (respeita limites da API);
 * - até 2 novas tentativas para erros de rede/5xx, com espera crescente;
 * - se a atualização falhar e houver cache, devolve o cache marcado como `stale = true`.
 */
class MarketRepository(
    private val gecko: CoinGeckoApi,
    private val fx: FrankfurterApi,
    private val cache: CacheStore,
    private val json: Json = Network.json,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val retryDelayMs: Long = 1_000L,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> cached(
        key: String,
        ttlMs: Long,
        force: Boolean,
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
    ): Quoted<T> {
        val callStart = clock()
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            val entry = cache.get(key)
            val hit: Pair<Long, T>? = entry?.let { e ->
                runCatching { json.decodeFromString(serializer, e.json) }.getOrNull()?.let { e.timestamp to it }
            }
            if (hit != null) {
                val age = clock() - hit.first
                val fresh = age in 0 until ttlMs
                val fetchedByOtherCaller = hit.first >= callStart
                val tooSoon = age in 0 until MIN_INTERVAL_MS
                if ((fresh && !force) || fetchedByOtherCaller || tooSoon) {
                    return@withLock Quoted(hit.second, hit.first, false)
                }
            }
            try {
                val data = withRetry(fetch)
                val now = clock()
                cache.put(key, CacheEntry(now, json.encodeToString(serializer, data)))
                Quoted(data, now, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hit != null) Quoted(hit.second, hit.first, true)
                else throw DataUnavailableException(describe(e), e)
            }
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val retryable = e is IOException || (e is HttpException && e.code() >= 500)
                if (!retryable || attempt >= 2) throw e
                attempt++
                delay(retryDelayMs * attempt)
            }
        }
    }

    private fun describe(e: Throwable): String = when {
        e is HttpException && e.code() == 429 ->
            "Limite de requisições da API atingido. Tente novamente em instantes."
        e is HttpException -> "A fonte de dados respondeu com erro (${e.code()})."
        e is IOException -> "Sem conexão com a internet ou fonte de dados indisponível."
        e is SerializationException || e is ApiFormatException || e is IllegalArgumentException ->
            "Resposta inválida da fonte de dados."
        else -> "Não foi possível obter os dados."
    }

    // ---------- Criptomoedas (CoinGecko) ----------

    suspend fun markets(vs: String, force: Boolean = false): Quoted<List<MarketCoin>> =
        cached("markets_$vs", MARKETS_TTL, force, ListSerializer(MarketCoin.serializer())) {
            mapMarkets(gecko.markets(vs), allowEmpty = false)
        }

    suspend fun marketsByIds(ids: Collection<String>, vs: String, force: Boolean = false): Quoted<List<MarketCoin>> {
        val sorted = ids.filter { it.isNotBlank() }.distinct().sorted()
        if (sorted.isEmpty()) return Quoted(emptyList(), clock(), false)
        val key = "markets_${vs}_ids_${sorted.joinToString(",").hashCode()}"
        return cached(key, MARKETS_TTL, force, ListSerializer(MarketCoin.serializer())) {
            mapMarkets(gecko.markets(vs, perPage = 250, sparkline = true, ids = sorted.joinToString(",")), allowEmpty = true)
        }
    }

    suspend fun chart(id: String, vs: String, days: Int, force: Boolean = false): Quoted<List<PricePoint>> {
        val ttl = if (days <= 1) 5 * 60_000L else 15 * 60_000L
        return cached("chart_${id}_${vs}_$days", ttl, force, ListSerializer(PricePoint.serializer())) {
            val pts = gecko.chart(id, vs, days.toString()).prices.mapNotNull { p ->
                val t = p.getOrNull(0)
                val v = p.getOrNull(1)
                if (t != null && v != null && v.isFinite()) PricePoint(t.toLong(), v) else null
            }
            if (pts.size < 2) throw ApiFormatException("Histórico indisponível")
            downsample(pts, 400)
        }
    }

    suspend fun search(query: String): List<Asset> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return cached("search_$q", 10 * 60_000L, false, ListSerializer(Asset.serializer())) {
            gecko.search(q).coins.filter { it.id.isNotBlank() && it.symbol.isNotBlank() }
                .take(25).map { Asset.crypto(it.id, it.symbol, it.name, it.thumb) }
        }.data
    }

    suspend fun cryptoSpot(ids: Collection<String>, force: Boolean = false): Quoted<Map<String, Spot>> {
        val sorted = ids.filter { it.isNotBlank() }.distinct().sorted()
        if (sorted.isEmpty()) return Quoted(emptyMap(), clock(), false)
        val key = "spot_${sorted.joinToString(",").hashCode()}"
        return cached(key, 60_000L, force, MapSerializer(String.serializer(), Spot.serializer())) {
            val raw = gecko.simplePrice(sorted.joinToString(","))
            val out = raw.mapNotNull { (id, m) ->
                val usd = m["usd"]?.takeIf { it.isFinite() && it > 0 }
                val brl = m["brl"]?.takeIf { it.isFinite() && it > 0 }
                if (usd == null && brl == null) null else id to Spot(usd, brl)
            }.toMap()
            if (out.isEmpty()) throw ApiFormatException("Sem preços")
            out
        }
    }

    // ---------- Câmbio tradicional (Frankfurter v1, dados do BCE) ----------

    suspend fun fiatCurrencies(force: Boolean = false): Quoted<Map<String, String>> =
        cached("fx_currencies", 24 * 3_600_000L, force, MapSerializer(String.serializer(), String.serializer())) {
            val m = fx.currencies().filterKeys { it.length == 3 }
            if (m.isEmpty()) throw ApiFormatException("Lista de moedas vazia")
            m
        }

    suspend fun fiatTable(force: Boolean = false): Quoted<FiatTable> =
        cached("fx_table", 60 * 60_000L, force, FiatTable.serializer()) {
            val r = fx.latest("USD")
            val rates = r.rates.filterValues { it.isFinite() && it > 0 } + ("USD" to 1.0)
            if (rates.size < 2) throw ApiFormatException("Taxas vazias")
            FiatTable(r.date, rates)
        }

    suspend fun fiatSeries(base: String, quote: String, days: Int = 30, force: Boolean = false): Quoted<List<PricePoint>> =
        cached("fx_series_${base}_${quote}_$days", 60 * 60_000L, force, ListSerializer(PricePoint.serializer())) {
            val end = Instant.ofEpochMilli(clock()).atZone(ZoneOffset.UTC).toLocalDate()
            val start = end.minusDays(days.toLong())
            val dto = fx.series("$start..$end", base, quote)
            val pts = dto.rates.mapNotNull { (date, m) ->
                val v = m[quote]
                val t = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                if (v != null && v.isFinite() && v > 0 && t != null) PricePoint(t, v) else null
            }.sortedBy { it.t }
            if (pts.size < 2) throw ApiFormatException("Série indisponível")
            pts
        }

    // ---------- Auxiliares ----------

    internal fun mapMarkets(dtos: List<MarketDto>, allowEmpty: Boolean): List<MarketCoin> {
        val out = dtos.mapNotNull { d ->
            val p = d.currentPrice
            if (d.id.isBlank() || p == null || !p.isFinite() || p <= 0.0) null
            else MarketCoin(
                id = d.id,
                symbol = d.symbol.uppercase(),
                name = d.name.ifBlank { d.id },
                imageUrl = d.image,
                price = p,
                change24hPct = d.change24h?.takeIf { it.isFinite() },
                marketCap = d.marketCap?.takeIf { it.isFinite() },
                volume24h = d.totalVolume?.takeIf { it.isFinite() },
                high24h = d.high24h?.takeIf { it.isFinite() },
                low24h = d.low24h?.takeIf { it.isFinite() },
                sparkline = d.sparkline?.price?.filterNotNull()?.filter { it.isFinite() } ?: emptyList(),
                updatedAtMillis = d.lastUpdated?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                rank = d.rank,
            )
        }
        if (out.isEmpty() && !(allowEmpty && dtos.isEmpty())) throw ApiFormatException("Nenhum ativo válido na resposta")
        return out
    }

    companion object {
        const val MARKETS_TTL = 2 * 60_000L
        const val MIN_INTERVAL_MS = 30_000L

        fun downsample(points: List<PricePoint>, max: Int): List<PricePoint> {
            if (points.size <= max || max < 2) return points
            val step = (points.size - 1).toDouble() / (max - 1)
            return (0 until max).map { points[(it * step).toInt().coerceAtMost(points.lastIndex)] }
                .let { if (it.last() != points.last()) it.dropLast(1) + points.last() else it }
        }
    }
}
