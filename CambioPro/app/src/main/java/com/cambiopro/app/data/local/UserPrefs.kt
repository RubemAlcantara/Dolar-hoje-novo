package com.cambiopro.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.cambiopro.app.domain.ConversionRecord
import com.cambiopro.app.domain.HistoryLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "BRL",
    /** 0 = desativada. WorkManager exige mínimo de 15 min em segundo plano. */
    val refreshMinutes: Int = 30,
    val geckoKey: String = "",
)

class UserPrefs(
    private val ds: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private object K {
        val THEME = stringPreferencesKey("theme")
        val CURRENCY = stringPreferencesKey("currency")
        val REFRESH = intPreferencesKey("refresh_minutes")
        val GECKO_KEY = stringPreferencesKey("gecko_key")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val HISTORY = stringPreferencesKey("history")
    }

    private val serializer = ListSerializer(ConversionRecord.serializer())

    private val data: Flow<Preferences> = ds.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[K.THEME] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
            currency = p[K.CURRENCY]?.takeIf { it in CURRENCIES } ?: "BRL",
            refreshMinutes = p[K.REFRESH] ?: 30,
            geckoKey = p[K.GECKO_KEY] ?: "",
        )
    }

    val favorites: Flow<Set<String>> = data.map { it[K.FAVORITES] ?: emptySet() }

    val history: Flow<List<ConversionRecord>> = data.map { decode(it[K.HISTORY]) }

    private fun decode(raw: String?): List<ConversionRecord> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    suspend fun setTheme(mode: ThemeMode) { ds.edit { it[K.THEME] = mode.name } }
    suspend fun setCurrency(code: String) { if (code in CURRENCIES) ds.edit { it[K.CURRENCY] = code } }
    suspend fun setRefreshMinutes(min: Int) { ds.edit { it[K.REFRESH] = min.coerceAtLeast(0) } }
    suspend fun setGeckoKey(key: String) { ds.edit { it[K.GECKO_KEY] = key.trim() } }

    suspend fun toggleFavorite(key: String) {
        ds.edit { p ->
            val cur = p[K.FAVORITES] ?: emptySet()
            p[K.FAVORITES] = if (key in cur) cur - key else cur + key
        }
    }

    suspend fun addHistory(record: ConversionRecord) {
        ds.edit { p ->
            val updated = HistoryLogic.add(decode(p[K.HISTORY]), record)
            p[K.HISTORY] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun clearHistory() { ds.edit { it.remove(K.HISTORY) } }

    companion object {
        val CURRENCIES = setOf("BRL", "USD", "EUR")
    }
}
