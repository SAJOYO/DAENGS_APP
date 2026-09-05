package com.daengs.app.territory

import android.content.Context
import androidx.work.*
import com.daengs.app.DaengsApp
import java.util.concurrent.TimeUnit

internal suspend fun enqueueTerritoryActions(context: Context, append: Boolean = true) {
    WorkManager.getInstance(context).enqueueUniqueWork("territory-actions",
        if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<TerritoryActionWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()).await()
}

class TerritoryActionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as? DaengsApp)?.territoryActions ?: return Result.success()
        return try {
            val actionsDone = sync.deliver()
            val photosDone = sync.photos?.deliverBound() ?: true
            if (actionsDone && photosDone) Result.success() else Result.retry()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
