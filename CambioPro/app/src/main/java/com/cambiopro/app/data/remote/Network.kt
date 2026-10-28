package com.cambiopro.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * [geckoKey] devolve a chave demo OPCIONAL digitada pelo usuário (guardada só no aparelho).
     * Nenhuma chave é embutida no APK.
     */
    fun okHttp(geckoKey: () -> String?): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val key = if (req.url.host == "api.coingecko.com") geckoKey() else null
            val out = if (!key.isNullOrBlank()) req.newBuilder().header("x-cg-demo-api-key", key).build() else req
            chain.proceed(out)
        }
        .build()

    inline fun <reified T> retrofit(client: OkHttpClient, baseUrl: String): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(T::class.java)
}
