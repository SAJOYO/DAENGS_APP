package com.daengs.app.walk.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.daengs.app.DaengsApp
import com.daengs.app.walk.WalkFixLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** 끝난 산책을 운영체제의 durable 작업으로 넘기는 경계. */
interface WalkDeliveryScheduler {
    /** 같은 세션의 미완료 작업이 있으면 새 작업을 만들지 않는다. */
    suspend fun enqueue(sessionId: String)

    /** close와 enqueue 사이에서 프로세스가 죽은 기록까지 다시 찾는다. */
    suspend fun enqueuePending()
}

/**
 * 로컬 완료와 durable 전달 예약의 실패 경계를 나눈다.
 *
 * 완료 실패는 결과를 만들 수 없지만, 예약 실패는 이미 저장한 결과를 되돌릴 이유가
 * 아니다. 후자는 다음 앱 시작의 [WalkDeliveryScheduler.enqueuePending]이 복구한다.
 */
internal suspend fun completeAndEnqueueWalk(
    sessionId: String?,
    complete: suspend () -> Boolean,
    enqueue: suspend (String) -> Unit,
    onCompletionFailure: (Throwable) -> Unit,
    onEnqueueFailure: (Throwable) -> Unit,
) {
    val shouldDeliver = try {
        complete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cause: Throwable) {
        onCompletionFailure(cause)
        false
    }
    if (sessionId == null || !shouldDeliver) return

    try {
        enqueue(sessionId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cause: Throwable) {
        onEnqueueFailure(cause)
    }
}

class WorkManagerWalkDeliveryScheduler(
    context: Context,
    private val log: WalkFixLog,
) : WalkDeliveryScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun enqueue(sessionId: String) {
        workManager.enqueueUniqueWork(
            walkDeliveryWorkName(sessionId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            walkDeliveryRequest(sessionId),
        ).await()
    }

    override suspend fun enqueuePending() {
        log.sessionsPendingAnalysis().forEach { enqueue(it.id) }
    }
}

/**
 * 네트워크가 돌아오면 산책 한 건을 현재 Room 상태부터 이어서 보낸다.
 *
 * 로그인하지 않은 산책은 로컬에 그대로 둔다. 이후 로그인하면 앱의 pending
 * reconciliation이 같은 세션을 다시 enqueue한다. 토큰이 기기에 남아 있는데 refresh만
 * 실패한 경우에는 서버/네트워크의 일시 장애일 수 있으므로 WorkManager가 재시도한다.
 */
class WalkDeliveryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val app = applicationContext as? DaengsApp ?: return Result.failure()
        if (!WalkApi.configured) return Result.success()
        val session = app.sessionProvider.freshSession()
        if (session == null) {
            return if (app.tokenStore.load() == null) Result.success() else Result.retry()
        }

        return try {
            app.walkRuntime.sync.syncPendingSession(session.accessToken, sessionId)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Throwable) {
            if (cause.isRetryableWalkDeliveryFailure()) Result.retry() else Result.failure()
        }
    }
}

internal fun walkDeliveryWorkName(sessionId: String): String = "walk-delivery:$sessionId"

internal fun walkDeliveryRequest(sessionId: String): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<WalkDeliveryWorker>()
        .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
        .addTag(WALK_DELIVERY_TAG)
        .build()

/** 네트워크·timeout·서버 과부하만 재시도하고, 계약/권한 오류는 무한 반복하지 않는다. */
internal fun Throwable.isRetryableWalkDeliveryFailure(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        when (cause) {
            is IOException -> return true
            is WalkHttpException -> return cause.statusCode == 408 ||
                cause.statusCode == 425 ||
                cause.statusCode == 429 ||
                cause.statusCode >= 500
        }
        cause = cause.cause
    }
    return false
}

internal const val KEY_SESSION_ID = "walk_session_id"
private const val WALK_DELIVERY_TAG = "walk-delivery"
private const val BACKOFF_SECONDS = 30L
