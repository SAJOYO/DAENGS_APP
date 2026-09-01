package com.daengs.app.gait

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 보행 기록을 들고 있는 자리. [PetHolder][com.daengs.app.pet.PetHolder] 와 같은 결이다 —
 * `ViewModel` 을 안 쓰고 `mutableStateOf` 홀더를 `remember` 로 잡는다 (이 저장소의 패턴).
 *
 * **지금은 기기 안에만 있다.** 서버가 붙으면 [records] 의 출처만 바뀐다 — `PetHolder`
 * 가 그렇듯 "서버가 진짜" 가 되고 여기 있는 건 그리려고 든 사본이 된다. 그래서 화면은
 * 이 홀더만 보고, 목록을 어디서 받아 왔는지는 모른다.
 *
 * 분석 진행 상태는 여기 두지 않는다. 그건 **대화의 한 줄**이라 말풍선 옆자리에
 * 붙어 있어야 하고, 화면이 `ChatEntry` 로 들고 있다 (피부 진단이 하는 것과 같다).
 */
class GaitHolder(
    private val analyzer: GaitAnalyzer = MockGaitAnalyzer(),
    initial: List<GaitRecord> = GaitSampleRecords.of(),
) {
    /** 최근 것이 앞이다. 목록도 카드도 이 순서를 그대로 쓴다. */
    var records: List<GaitRecord> by mutableStateOf(initial)
        private set

    /** 마지막으로 실패한 이유. 알려 주고 나면 화면이 [clearError] 한다. */
    var error: String? by mutableStateOf(null)
        private set

    /** 비교할 기록이 하나라도 있나. 없으면 결과 카드가 "비교" 버튼을 감춘다. */
    fun hasComparable(exceptId: String): Boolean =
        records.any { it.id != exceptId && it.comparable }

    fun find(id: String): GaitRecord? = records.firstOrNull { it.id == id }

    fun clearError() {
        error = null
    }

    /**
     * 영상 한 편을 분석하고 목록에 얹는다.
     *
     * @param onProgress 단계가 넘어갈 때마다 불린다. 화면이 진행 카드를 다시 그린다
     * @return 만들어진 기록. 실패하면 null 이고 이유는 [error] 에 남는다
     */
    suspend fun analyze(video: PreparedVideo, onProgress: (GaitProgress) -> Unit): GaitRecord? {
        error = null
        return analyzer.analyze(video, onProgress)
            .onSuccess { records = listOf(it) + records }
            .onFailure { error = it.message ?: "보행 영상을 분석하지 못했어요." }
            .getOrNull()
    }

    /**
     * 두 기록을 나란히 본다.
     *
     * 판정은 [GaitComparison.of] 가 지표에서 끌어낸다 — 여기서 문장을 고르지 않는다.
     */
    fun compare(recentId: String, pastId: String): GaitComparison? {
        val recent = find(recentId) ?: return null
        val past = find(pastId) ?: return null
        return GaitComparison.of(recent, past, GaitSampleRecords.metricsFor(recent, past))
    }

    /** 기록 하나를 지운다. 상세 화면의 삭제 자리가 부른다. */
    fun remove(id: String) {
        records = records.filterNot { it.id == id }
    }
}

@Composable
fun rememberGaitHolder(): GaitHolder = remember { GaitHolder() }
