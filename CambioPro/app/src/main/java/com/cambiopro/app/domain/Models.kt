package com.cambiopro.app.domain

import kotlinx.serialization.Serializable

enum class AssetKind { FIAT, CRYPTO }

@Serializable
data class Asset(
    val id: String,
    val symbol: String,
    val name: String,
    val kind: AssetKind,
    val imageUrl: String? = null,
) {
    /** Chave estável usada em favoritos e histórico, ex.: "CRYPTO:bitcoin" ou "FIAT:USD". */
    val key: String get() = "${kind.name}:$id"

    companion object {
        fun fiat(code: String, name: String = code): Asset =
            Asset(code.uppercase(), code.uppercase(), name, AssetKind.FIAT)

        fun crypto(id: String, symbol: String, name: String, imageUrl: String? = null): Asset =
            Asset(id, symbol.uppercase(), name, AssetKind.CRYPTO, imageUrl)
    }
}

@Serializable
data class PricePoint(val t: Long, val v: Double)

@Serializable
data class MarketCoin(
    val id: String,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val price: Double,
    val change24hPct: Double?,
    val marketCap: Double?,
    val volume24h: Double?,
    val high24h: Double?,
    val low24h: Double?,
    val sparkline: List<Double>,
    val updatedAtMillis: Long?,
    val rank: Int?,
) {
    fun asAsset(): Asset = Asset.crypto(id, symbol, name, imageUrl)
}

/** Tabela de câmbio de referência: unidades de cada moeda por 1 USD. */
@Serializable
data class FiatTable(val date: String, val perUsd: Map<String, Double>)

@Serializable
data class Spot(val usd: Double?, val brl: Double?)

/** Dado com carimbo de tempo. [stale] = true quando a atualização falhou e o cache antigo foi usado. */
data class Quoted<out T>(val data: T, val fetchedAt: Long, val stale: Boolean)

@Serializable
data class ConversionRecord(
    val fromKey: String,
    val toKey: String,
    val fromSymbol: String,
    val toSymbol: String,
    val amount: Double,
    val result: Double,
    val rate: Double,
    val timestamp: Long,
)

class DataUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ApiFormatException(message: String) : Exception(message)
