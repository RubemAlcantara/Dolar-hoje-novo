package com.cambiopro.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarketDto(
    val id: String = "",
    val symbol: String = "",
    val name: String = "",
    val image: String? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("market_cap") val marketCap: Double? = null,
    @SerialName("market_cap_rank") val rank: Int? = null,
    @SerialName("total_volume") val totalVolume: Double? = null,
    @SerialName("high_24h") val high24h: Double? = null,
    @SerialName("low_24h") val low24h: Double? = null,
    @SerialName("price_change_percentage_24h") val change24h: Double? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("sparkline_in_7d") val sparkline: SparklineDto? = null,
)

@Serializable
data class SparklineDto(val price: List<Double?> = emptyList())

@Serializable
data class ChartDto(val prices: List<List<Double?>> = emptyList())

@Serializable
data class SearchDto(val coins: List<SearchCoinDto> = emptyList())

@Serializable
data class SearchCoinDto(
    val id: String = "",
    val name: String = "",
    val symbol: String = "",
    val thumb: String? = null,
    @SerialName("market_cap_rank") val rank: Int? = null,
)

@Serializable
data class RatesDto(
    val base: String = "",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap(),
)

@Serializable
data class SeriesDto(
    val base: String = "",
    val rates: Map<String, Map<String, Double>> = emptyMap(),
)
