package com.drfxai.maximusvpn.subscription

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drfxai.maximusvpn.data.repository.SettingsRepository
import com.drfxai.maximusvpn.xray.XrayLogManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background subscription auto-update worker.
 *
 * Runs at most every [MIN_PERIOD_HOURS] hours; each run only syncs subscriptions
 * whose per-subscription interval has elapsed. Wi-Fi-only enforcement is applied
 * via network constraints when configured.
 */
class SubscriptionUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SubscriptionRepository.getInstance(applicationContext)
        val settings = SettingsRepository(applicationContext).getSettings()
        if (settings.subscriptionAutoUpdateHours <= 0) return Result.success()

        var anyFailure = false
        for (sub in repo.allSubscriptions().first()) {
            val last = sub.lastUpdatedTimestamp ?: 0L
            val intervalMs = sub.autoUpdateHours.coerceAtLeast(1) * 3_600_000L
            if (System.currentTimeMillis() - last < intervalMs) continue

            val result = repo.sync(sub.id)
            if (result.error != null) {
                anyFailure = true
                XrayLogManager.appendLog(
                    "Subscription '${sub.name}' auto-update failed: ${result.error}", "SUBSCRIPTION"
                )
            } else {
                XrayLogManager.appendLog(
                    "Subscription '${sub.name}' auto-updated: +${result.added} ~${result.updated} -${result.removed}",
                    "SUBSCRIPTION"
                )
            }
        }
        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        const val MIN_PERIOD_HOURS = 6
        const val WORK_NAME = "subscription_auto_update"

        /** (Re)schedules periodic work; cancels everything when autoUpdateHours <= 0. */
        fun schedule(context: Context, autoUpdateHours: Int, wifiOnly: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (autoUpdateHours <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                MIN_PERIOD_HOURS.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
