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
    enqueueTerritoryPhotos(context)
}

internal suspend fun enqueueTerritoryPhotos(context: Context, append: Boolean = false) {
    WorkManager.getInstance(context).enqueueUniqueWork("territory-photos",
        if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<TerritoryPhotoWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()).await()
}

class TerritoryActionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as? DaengsApp)?.territoryActions ?: return Result.success()
        return try {
            val actionsDone = sync.deliver()
            // A photo worker may have taken its snapshot before binding. Append a successor:
            // KEEP could drop this wake while that worker is just about to return success.
            enqueueTerritoryPhotos(applicationContext, append = true)
            if (actionsDone) Result.success() else Result.retry()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

/** Upload/poll backoff must never keep a fresh MARK behind an unfinished action worker. */
class TerritoryPhotoWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val photos = (applicationContext as? DaengsApp)?.territoryActions?.photos ?: return Result.success()
        return try {
            if (photos.deliverBound()) Result.success() else Result.retry()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
