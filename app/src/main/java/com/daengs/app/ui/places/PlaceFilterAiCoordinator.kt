package com.daengs.app.ui.places

import com.daengs.app.place.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaceFilterAiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val response: PlaceFilterEditResponse? = null,
    val message: String? = null,
    val canRetry: Boolean = false,
)

/** One edit flight; the search controller remains the only owner of applied conditions/results. */
internal class PlaceFilterAiCoordinator(
    private val repository: PlaceFilterEditRepository?,
    private val scope: CoroutineScope,
    private val matches: (PlaceFilterRequest) -> Boolean,
    private val accept: (PlaceFilterRequest, PlaceFilterResponse) -> Boolean,
) {
    private val mutableState = MutableStateFlow(PlaceFilterAiState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    private var owner: String? = null
    private var query: PlaceFilterEditQuery? = null
    private var pending: PlaceFilterEditAction? = null

    fun enable(enabled: Boolean) { invalidate(); mutableState.value = PlaceFilterAiState(enabled = enabled) }
    fun invalidate(message: String? = null) {
        generation++; job?.cancel(); job = null; query = null; pending = null; owner = null
        mutableState.value = PlaceFilterAiState(enabled = state.value.enabled, message = message)
    }
    fun reject(message: String) { invalidate(message) }

    fun search(owner: String, text: String, base: PlaceFilterRequest) {
        invalidate()
        this.owner = owner; query = PlaceFilterEditQuery(text, base)
        run()
    }

    fun confirm() {
        if (state.value.loading) return
        val previous = state.value.response ?: return
        if (!previous.needsConfirmation || previous.result != null) return
        pending = PlaceFilterEditAction(previous, confirm = true)
        run()
    }

    fun retry() { if (state.value.canRetry && !state.value.loading) run() }

    private fun run() {
        val repository = repository ?: return
        val owner = owner ?: return
        val query = query ?: return
        if (!matches(query.base)) { invalidate("검색 조건이 바뀌었어요. 현재 조건에서 다시 요청해 주세요."); return }
        val token = ++generation
        job?.cancel()
        mutableState.value = state.value.copy(loading = true, message = null, canRetry = false)
        job = scope.launch {
            try {
                var response = pending?.let { repository.apply(owner, it) } ?: repository.propose(owner, query)
                if (token != generation) return@launch
                if (!matches(query.base)) { invalidate("검색 조건이 바뀌어 이전 AI 변경을 적용하지 않았어요."); return@launch }
                if (response.result == null && response.proposed != null && !response.needsConfirmation) {
                    // Clear additions/removals need no second user confirmation; use identical CAS action on retry.
                    pending = PlaceFilterEditAction(response, confirm = false)
                    response = repository.apply(owner, pending!!)
                }
                if (token != generation) return@launch
                val result = response.result
                if (!matches(query.base) || result != null && !accept(query.base, result)) {
                    invalidate("검색 조건이 바뀌어 이전 AI 변경을 적용하지 않았어요."); return@launch
                }
                pending = null
                mutableState.value = state.value.copy(loading = false, response = response,
                    message = if (result != null) "요청한 조건을 적용했어요. 조건 선택에서 직접 수정할 수 있어요." else null)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (token == generation) mutableState.value = state.value.copy(loading = false,
                    message = error.facilityMessage(), canRetry = error !is FacilityException || error.status !in listOf(401, 403, 409, 410, 422))
            }
        }
    }
}
