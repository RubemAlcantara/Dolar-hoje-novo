package com.cambiopro.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheEntry(val timestamp: Long, val json: String)

interface CacheStore {
    suspend fun get(key: String): CacheEntry?
    suspend fun put(key: String, entry: CacheEntry)
}

/** Cache em arquivos: 1ª linha = carimbo de tempo (ms), restante = JSON. Escrita atômica via arquivo temporário. */
class FileCacheStore(private val dir: File) : CacheStore {
    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".cache")

    override suspend fun get(key: String): CacheEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(key)
            if (!f.exists()) return@runCatching null
            val text = f.readText()
            val nl = text.indexOf('\n')
            if (nl <= 0) return@runCatching null
            CacheEntry(text.substring(0, nl).trim().toLong(), text.substring(nl + 1))
        }.getOrNull()
    }

    override suspend fun put(key: String, entry: CacheEntry) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val target = file(key)
                val tmp = File(dir, target.name + ".tmp")
                tmp.writeText("${entry.timestamp}\n${entry.json}")
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }
        }
    }
}
