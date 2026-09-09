package com.daengs.app.walk.pin

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.daengs.app.DaengsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Fast foreground timer plus durable OS recovery. Both use the same one-shot database CAS. */
class ActionPinScheduler(private val context: Context, private val scope: CoroutineScope) {
    suspend fun schedule(id: String, deadline: Long) {
        val wait = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork("action-pin:$id", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ActionPinWorker>().setInitialDelay(wait, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("entry_id" to id)).build()).await()
        scope.launch {
            delay(wait)
            val app = context.applicationContext as DaengsApp
            try { app.walkRuntime.writer.ordered { app.actionPins.finish(id) }.await() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* The durable worker retries a failed write. */ }
        }
    }
}

class ActionPinWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        val id = inputData.getString("entry_id") ?: return Result.failure()
        return try {
            app.walkRuntime.writer.ordered { app.actionPins.finish(id) }.await()
            val row = app.walkEntryDao.entry(id)
            val pin = row?.pinPayload?.let(::ActionPin)
            if (pin?.state == "provisional") Result.retry() else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
