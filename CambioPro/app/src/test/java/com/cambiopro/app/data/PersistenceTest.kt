package com.cambiopro.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cambiopro.app.data.local.CacheEntry
import com.cambiopro.app.data.local.FileCacheStore
import com.cambiopro.app.data.local.ThemeMode
import com.cambiopro.app.data.local.UserPrefs
import com.cambiopro.app.domain.ConversionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun rec(i: Int) = ConversionRecord("FIAT:USD", "FIAT:BRL", "USD", "BRL", i.toDouble(), i * 5.0, 5.0, i.toLong())

    /** Abre um DataStore no mesmo arquivo; fechar o escopo simula o app sendo encerrado. */
    private fun open(file: File): Pair<UserPrefs, Job> {
        val job = Job()
        val ds = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        return UserPrefs(ds) to job
    }

    @Test fun favoritesAndSettingsSurviveRestart() = runBlocking {
        val file = File(tmp.root, "u1.preferences_pb")
        val (p1, j1) = open(file)
        p1.toggleFavorite("CRYPTO:bitcoin")
        p1.toggleFavorite("FIAT:USD")
        p1.toggleFavorite("FIAT:USD") // remove
        p1.setTheme(ThemeMode.DARK)
        p1.setCurrency("USD")
        p1.setRefreshMinutes(60)
        j1.cancelAndJoin()

        val (p2, j2) = open(file)
        assertEquals(setOf("CRYPTO:bitcoin"), p2.favorites.first())
        val s = p2.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals("USD", s.currency)
        assertEquals(60, s.refreshMinutes)
        j2.cancelAndJoin()
    }

    @Test fun invalidCurrencyIsIgnored() = runBlocking {
        val (p, j) = open(File(tmp.root, "u2.preferences_pb"))
        p.setCurrency("XYZ")
        assertEquals("BRL", p.settings.first().currency)
        j.cancelAndJoin()
    }

    @Test fun historySurvivesRestartIsLimitedAndCanBeCleared() = runBlocking {
        val file = File(tmp.root, "u3.preferences_pb")
        val (p1, j1) = open(file)
        for (i in 1..105) p1.addHistory(rec(i))
        j1.cancelAndJoin()

        val (p2, j2) = open(file)
        val h = p2.history.first()
        assertEquals(100, h.size)
        assertEquals(105L, h.first().timestamp)
        p2.clearHistory()
        assertTrue(p2.history.first().isEmpty())
        j2.cancelAndJoin()
    }

    @Test fun fileCachePersistsAcrossInstances() = runBlocking {
        val dir = File(tmp.root, "cache")
        FileCacheStore(dir).put("markets:brl", CacheEntry(123L, "{\"a\":1}\nlinha2"))
        val e = FileCacheStore(dir).get("markets:brl")!!
        assertEquals(123L, e.timestamp)
        assertEquals("{\"a\":1}\nlinha2", e.json)
    }

    @Test fun fileCacheMissingOrCorruptReturnsNull() = runBlocking {
        val dir = File(tmp.root, "cache2").also { it.mkdirs() }
        val store = FileCacheStore(dir)
        assertNull(store.get("nada"))
        File(dir, "ruim.cache").writeText("sem-quebra-de-linha")
        assertNull(store.get("ruim"))
        File(dir, "ruim2.cache").writeText("abc\n{}")
        assertNull(store.get("ruim2"))
    }
}
