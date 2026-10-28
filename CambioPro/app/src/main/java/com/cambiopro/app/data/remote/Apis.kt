package com.cambiopro.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** CoinGecko — API pública sem chave (limites por IP). Base: https://api.coingecko.com/api/v3/ */
interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun markets(
        @Query("vs_currency") vsCurrency: String,
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") priceChange: String = "24h",
        @Query("ids") ids: String? = null,
    ): List<MarketDto>

    @GET("coins/{id}/market_chart")
    suspend fun chart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String,
        @Query("days") days: String,
    ): ChartDto

    @GET("search")
    suspend fun search(@Query("query") query: String): SearchDto

    @GET("simple/price")
    suspend fun simplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd,brl",
    ): Map<String, Map<String, Double?>>
}

/** Frankfurter v1 (dados do BCE). Base: https://api.frankfurter.dev/ */
interface FrankfurterApi {
    @GET("v1/latest")
    suspend fun latest(
        @Query("base") base: String,
        @Query("symbols") symbols: String? = null,
    ): RatesDto

    @GET("v1/currencies")
    suspend fun currencies(): Map<String, String>

    @GET("v1/{range}")
    suspend fun series(
        @Path("range", encoded = true) range: String,
        @Query("base") base: String,
        @Query("symbols") symbols: String,
    ): SeriesDto
}
