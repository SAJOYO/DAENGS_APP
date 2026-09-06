package com.daengs.app.territory

import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PhotoSimulation(val label: String) {
    ACCEPT("성공"), REJECT("부적합"), DELAY("지연 후 성공"), FAILURE("통신 장애"),
}

fun interface TerritoryPhotoJudge {
    suspend fun judge(file: File, simulation: PhotoSimulation): ClaimPhotoOutcome
}

/** Explicit debug fake: does not inspect the image or call a VLM. */
class FakeTerritoryPhotoJudge : TerritoryPhotoJudge {
    override suspend fun judge(file: File, simulation: PhotoSimulation): ClaimPhotoOutcome {
        delay(if (simulation == PhotoSimulation.DELAY) 15_000 else 2_000)
        return when (simulation) {
            PhotoSimulation.ACCEPT, PhotoSimulation.DELAY -> ClaimPhotoOutcome.ACCEPTED
            PhotoSimulation.REJECT -> ClaimPhotoOutcome.REJECTED
            PhotoSimulation.FAILURE -> ClaimPhotoOutcome.RETRYABLE_FAILURE
        }
    }
}

data class TerritoryPhotoJob(
    val attemptId: String,
    val siteId: String,
    val captureId: String,
    val file: File,
    val status: ClaimPhotoStatus = ClaimPhotoStatus.PENDING,
    val conflict: Boolean = false,
)

/** Process-local debug outbox. Its scope outlives the map; process restart resets this fake. */
class TerritoryPhotoQueue(
    private val repository: InMemoryTerritoryClaimRepository,
    private val scope: CoroutineScope,
    private val judge: TerritoryPhotoJudge = FakeTerritoryPhotoJudge(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutableJobs = MutableStateFlow<List<TerritoryPhotoJob>>(emptyList())
    val jobs = mutableJobs.asStateFlow()

    fun submit(attemptId: String, file: File, simulation: PhotoSimulation) {
        require(file.isFile && file.length() > 0) { "사진 저장을 확인하지 못했어요" }
        val captureId = UUID.randomUUID().toString()
        val attempt = repository.submitPhoto(attemptId, captureId)
        val job = TerritoryPhotoJob(attemptId, attempt.siteId, captureId, file)
        mutableJobs.value = mutableJobs.value.filterNot { it.attemptId == attemptId } + job
        evaluate(job, simulation)
    }

    fun retry(attemptId: String, simulation: PhotoSimulation = PhotoSimulation.ACCEPT) {
        val job = mutableJobs.value.firstOrNull { it.attemptId == attemptId } ?: return
        if (job.status != ClaimPhotoStatus.RETRY_PENDING || job.conflict) return
        repository.resume(attemptId)
        val resumed = job.copy(status = ClaimPhotoStatus.PENDING)
        replace(resumed)
        evaluate(resumed, simulation)
    }

    private fun evaluate(job: TerritoryPhotoJob, simulation: PhotoSimulation) {
        scope.launch {
            val outcome = try {
                if (!job.file.isFile || job.file.length() == 0L) ClaimPhotoOutcome.REJECTED
                else judge.judge(job.file, simulation)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ClaimPhotoOutcome.RETRYABLE_FAILURE
            }
            val resolved = runCatching {
                repository.resolvePhoto(job.attemptId, job.captureId, outcome, nowMillis())
            }
            resolved.fold(
                onSuccess = { attempt ->
                    if (attempt.photoStatus != ClaimPhotoStatus.RETRY_PENDING) job.file.delete()
                    replace(job.copy(status = attempt.photoStatus))
                },
                onFailure = {
                    // Concurrent ownership policy is deferred: never invent an overwrite winner.
                    job.file.delete()
                    replace(job.copy(conflict = true))
                },
            )
        }
    }

    private fun replace(job: TerritoryPhotoJob) {
        mutableJobs.value = mutableJobs.value.map { if (it.captureId == job.captureId) job else it }
    }
}
