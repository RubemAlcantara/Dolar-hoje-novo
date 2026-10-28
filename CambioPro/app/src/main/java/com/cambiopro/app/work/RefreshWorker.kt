package com.cambiopro.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cambiopro.app.CambioApp
import com.cambiopro.app.domain.DataUnavailableException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Atualiza o cache em segundo plano para o app abrir com dados mais recentes. Não envia notificações. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CambioApp).container
        val currency = c.userPrefs.settings.first().currency.lowercase()
        return try {
            c.repo.markets(currency, force = true)
            c.repo.fiatTable(force = true)
            Result.success()
        } catch (e: DataUnavailableException) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object RefreshScheduler {
    const val NAME = "market_refresh"

    /** [minutes] <= 0 desativa. O Android impõe mínimo de 15 min e pode adiar a execução (Doze/economia de bateria). */
    fun apply(ctx: Context, minutes: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (minutes <= 0) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(maxOf(minutes, 15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
