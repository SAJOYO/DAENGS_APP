package com.daengs.app.ui.places

import com.daengs.app.place.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FacilityUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val response: FacilityResponse? = null,
    val error: String? = null,
    val notice: String? = null,
    val selectedPlaceKey: PlaceKey? = null,
    val canRetry: Boolean = false,
) {
    val confirmedLens: FacilityLens? get() = response?.confirmedLens
}

/** 위치·계정·분류가 바뀌면 이전 continuation을 폐기한다. 재시도 액션은 같은 UUID를 쓴다. */
internal class FacilitySearchCoordinator(
    private val repository: FacilityRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    private val mutable = MutableStateFlow(FacilityUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var owner: String? = null
    private var query: FacilityQuery? = null
    private var pendingAction: FacilityAction? = null

    fun enable(enabled: Boolean) {
        invalidate()
        mutable.value = FacilityUiState(enabled = enabled)
    }

    fun invalidate(message: String? = null) {
        generation++; job?.cancel(); job = null
        owner = null; query = null; pendingAction = null
        mutable.value = FacilityUiState(enabled = mutable.value.enabled, notice = message)
    }

    fun reject(message: String) {
        invalidate()
        mutable.value = mutable.value.copy(error = message)
    }

    fun search(owner: String?, request: FacilityQuery) {
        invalidate()
        mutable.value = mutable.value.copy(enabled = true)
        if (owner == null) { reject(FacilityException(401).facilityMessage()); return }
        this.owner = owner; query = request
        submit { repository.discover(owner, request) }
    }

    fun choose(choice: FacilityChoice) {
        if (mutable.value.loading) return
        val previous = mutable.value.response ?: return
        if (previous.expiresAt?.let { !it.isAfter(now()) } == true) {
            pendingAction = null
            mutable.value = mutable.value.copy(response = null, error = FacilityException(410).facilityMessage(), canRetry = true)
            return
        }
        if (!previous.canChoose(choice)) return
        val action = FacilityAction(previous.searchId, previous.revision, choice)
        pendingAction = action
        submit { repository.act(requireNotNull(owner), previous, action) }
    }

    fun retry() {
        if (mutable.value.loading || !mutable.value.canRetry) return
        val owner = owner ?: return
        val request = query ?: return
        val action = pendingAction
        val previous = mutable.value.response
        if (action != null && previous != null) submit { repository.act(owner, previous, action) }
        else {
            val next = request.copy(clientRequestId = java.util.UUID.randomUUID().toString())
            query = next
            submit { repository.discover(owner, next) }
        }
    }

    fun select(key: PlaceKey) {
        if (mutable.value.confirmedLens?.search?.groups?.flatMap { it.results }?.any { it.place.key == key } == true) {
            mutable.value = mutable.value.copy(selectedPlaceKey = key)
        }
    }

    private fun submit(operation: suspend () -> FacilityResponse) {
        val revision = ++generation
        job?.cancel()
        mutable.value = mutable.value.copy(loading = true, error = null, notice = null, canRetry = false, selectedPlaceKey = null)
        job = scope.launch {
            try {
                val response = operation()
                if (revision != generation) return@launch
                pendingAction = null
                val lens = response.confirmedLens
                val selected = lens?.search?.overviewHits(lens.parking)?.firstOrNull()?.place?.key
                mutable.value = FacilityUiState(enabled = true, response = response, selectedPlaceKey = selected)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (revision != generation) return@launch
                val reset = error !is java.io.IOException && (error !is FacilityException || error.status in listOf(401, 403, 409, 410, 422))
                if (reset) pendingAction = null
                mutable.value = mutable.value.copy(loading = false, error = error.facilityMessage(),
                    response = if (reset) null else mutable.value.response,
                    canRetry = error !is FacilityException || error.status !in listOf(0, 401, 403))
            }
        }
    }
}
