package com.daengs.app.ui.places.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daengs.app.place.PlaceKey
import com.daengs.app.place.PlaceKind
import com.daengs.app.place.PlaceSearchHit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LabPhase { LOADING, RESULTS, EMPTY, ERROR, PERMISSION, UNSAMPLED }

data class LabCriteria(
    val query: String = "",
    val kind: PlaceKind? = PlaceKind.CAFE,
    val parkingFirst: Boolean = false,
    val region: String = "seongsu",
    val radiusMeters: Int = 3_000,
)

data class PlaceSearchLabState(
    val draft: String = "",
    val aiMode: Boolean = false,
    val applied: LabCriteria = LabCriteria(),
    val selectedDogIds: Set<String> = emptySet(),
    val hits: List<PlaceSearchHit> = emptyList(),
    val phase: LabPhase = LabPhase.LOADING,
    val selected: PlaceKey? = null,
    val expanded: PlaceKey? = null,
    val notice: String? = null,
    val errorText: String? = null,
    val truncated: Boolean = false,
) {
    fun select(key: PlaceKey) = if (hits.any { it.place.key == key }) copy(selected = key) else this
    fun toggle(key: PlaceKey) = select(key).let {
        if (it.hits.none { hit -> hit.place.key == key }) it
        else it.copy(expanded = key.takeUnless { key == expanded })
    }
    fun results(items: List<PlaceSearchHit>) = copy(
        hits = items,
        phase = if (items.isEmpty()) LabPhase.EMPTY else LabPhase.RESULTS,
        selected = selected?.takeIf { key -> items.any { it.place.key == key } }
            ?: items.firstOrNull()?.place?.key,
        expanded = expanded?.takeIf { key -> items.any { it.place.key == key } },
    )
}

/** 개발용 공급자. API·프로필·AI 연결은 후속 단계에서 별도 구현한다. */
fun interface PlaceLabSource {
    suspend fun search(criteria: LabCriteria): List<PlaceSearchHit>
}

class PlaceSearchLabViewModel(private val source: PlaceLabSource) : ViewModel() {
    private val mutable = MutableStateFlow(PlaceSearchLabState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var revision = 0

    init { search(LabCriteria()) }
    fun edit(text: String) { mutable.value = mutable.value.copy(draft = text) }
    fun toggleAi() { mutable.value = mutable.value.copy(aiMode = !mutable.value.aiMode, notice = null) }
    fun submit() {
        if (mutable.value.aiMode) {
            mutable.value = mutable.value.copy(notice = "AI 연결 전입니다. 일반 검색을 사용해 주세요.")
        } else search(mutable.value.applied.copy(query = mutable.value.draft.trim()))
    }
    fun category(kind: PlaceKind?) = search(mutable.value.applied.copy(kind = kind))
    fun parking(value: Boolean) = search(mutable.value.applied.copy(parkingFirst = value))
    fun region(value: String) = search(mutable.value.applied.copy(region = value))
    fun retry() = search(mutable.value.applied)
    fun select(key: PlaceKey) { mutable.value = mutable.value.select(key) }
    fun toggle(key: PlaceKey) { mutable.value = mutable.value.toggle(key) }
    fun dog(id: String) {
        val ids = mutable.value.selectedDogIds
        mutable.value = mutable.value.copy(
            selectedDogIds = if (id in ids) ids - id else ids + id,
            notice = "프로필 선택 예시 · 검색 미연동. 저장된 대형견 조건으로 검토합니다.",
        )
    }
    fun simulate(phase: LabPhase) {
        request?.cancel(); revision++
        mutable.value = mutable.value.copy(phase = phase, hits = emptyList(), selected = null, expanded = null)
    }
    private fun search(criteria: LabCriteria) {
        request?.cancel()
        val current = ++revision
        mutable.value = mutable.value.copy(applied = criteria, phase = LabPhase.LOADING, notice = null)
        if (criteria.kind != null && criteria.kind !in setOf(PlaceKind.CAFE, PlaceKind.RESTAURANT)) {
            simulate(LabPhase.UNSAMPLED)
            return
        }
        request = viewModelScope.launch {
            try {
                val hits = source.search(criteria)
                if (current == revision) mutable.value = mutable.value.results(hits)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == revision) simulate(LabPhase.ERROR)
            }
        }
    }
}
