package com.daengs.app.gait

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 보행 기록을 들고 있는 자리. `PetHolder` 와 같은 결이다 —
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
    /**
     * access token. 목록·삭제가 쓴다 (분석은 [HttpGaitAnalyzer] 가 자기 것을 쓴다).
     *
     * **[MockGaitAnalyzer] 를 끼울 때는 부르지 않는다** — 아래 [remote] 가 먼저 막는다.
     */
    private val accessToken: suspend () -> String? = { null },
) {
    /**
     * 서버에 같이 알릴 것인가.
     *
     * **분석기가 진짜 서버를 쓸 때만 목록·삭제·비교도 서버로 간다.** 둘을 따로
     * 켜면 "분석은 기기에서 했는데 삭제는 서버로 가는" 반쪽 상태가 생긴다.
     * [MockGaitAnalyzer] 를 끼우면 통째로 기기 안에서 돌아서 단위 테스트와
     * `@Preview` 가 네트워크를 타지 않는다.
     */
    private val remote: Boolean get() = analyzer is HttpGaitAnalyzer
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
    suspend fun compare(recentId: String, pastId: String): GaitComparison? {
        val recent = find(recentId) ?: return null
        val past = find(pastId) ?: return null

        // 표본끼리는 서버에 없다. 서버 주소가 없을 때도 마찬가지다.
        val sample = recentId.startsWith(SAMPLE_PREFIX) || pastId.startsWith(SAMPLE_PREFIX)
        if (!remote || sample) {
            return GaitComparison.of(recent, past, GaitSampleRecords.metricsFor(recent, past))
        }

        // ⚠️ **새 계약(`/app/gait/…`)에는 비교가 아직 없다.** 옛 `/gait/compare` 는 인증이
        //    없던 주소라 같이 쓸 수 없고(#64), 기록도 저쪽에 없다 — 새 기록은 backend DB 에
        //    산다. 그래서 **지어내지 않고 말한다.** 여기서 표본 지표로 물러서면 서버가
        //    계산하지 않은 비교를 진짜처럼 보여 주게 된다.
        //    backend 에 `/app/gait/compare` 가 생기면 이 자리에 그대로 끼운다.
        error = "기록 비교는 아직 준비 중이에요. 조금만 기다려 주세요."
        return null
    }

    /**
     * 기록 하나를 지운다. 상세 화면의 삭제 자리가 부른다.
     *
     * **화면에서 먼저 빼고 서버를 부른다.** 지우기는 누른 사람이 결과를 이미 아는
     * 동작이라 몇 초 기다리게 할 이유가 없다. 다만 서버가 실패하면 되돌린다 —
     * 지운 줄 알았는데 다음에 켜면 살아 있는 것이 제일 나쁘다.
     *
     * 표본 기록(`sample-` )은 서버에 없으니 부르지 않는다.
     */
    suspend fun remove(id: String) {
        val before = records
        records = records.filterNot { it.id == id }
        if (!remote || id.startsWith(SAMPLE_PREFIX)) return
        val token = accessToken()
        if (token == null) {
            records = before
            error = "로그인이 필요해요. 다시 로그인해 주세요."
            return
        }
        GaitApi.delete(token, id).onFailure {
            records = before
            error = it.message ?: "기록을 지우지 못했어요."
        }
    }

    /**
     * 서버에서 이 강아지의 기록을 받아 온다.
     *
     * **실패해도 화면을 비우지 않는다.** 목록이 통째로 사라지면 사용자는 기록이
     * 지워진 줄 안다. 못 받아 왔으면 들고 있던 것을 그대로 두고 [error] 로만 말한다.
     *
     * 저쪽은 **오래된 것부터** 준다. 앱 목록은 최근이 앞이라 뒤집는다.
     */
    suspend fun load(petId: String) {
        if (!remote) return
        val token = accessToken() ?: return  // 로그인 전이면 조용히 둔다 — 화면이 아직 뜨는 중이다
        GaitApi.records(token, petId)
            .onSuccess { page -> records = page.records.reversed().map { it.toRecord() } }
            .onFailure { error = it.message ?: "기록을 받아오지 못했어요." }
    }

    companion object {
        /** 화면을 채우려고 만든 기록의 id 접두사. 서버에 없으므로 부르지 않는다. */
        const val SAMPLE_PREFIX = "sample-"
    }
}

/**
 * 서버가 붙어 있으면 [HttpGaitAnalyzer], 아니면 [MockGaitAnalyzer] 를 끼운다.
 *
 * **고르는 자리는 여기 하나다.** 화면은 어느 쪽이 들어갔는지 모른다.
 */
@Composable
fun rememberGaitHolder(
    petId: String?,
    /** `MainActivity` 의 `freshToken` — 만료됐으면 재발급까지 하고 준다. */
    accessToken: suspend () -> String? = { null },
): GaitHolder {
    val context = LocalContext.current

    // **홀더를 다시 만들지 않는다.** `remember(petId)` 로 두면 대표를 바꾸는 순간
    // 홀더가 새로 생겨서 대화에 올려 둔 기록이 통째로 사라진다. 최신 값만 읽는다.
    val latest by rememberUpdatedState(petId)
    val token by rememberUpdatedState(accessToken)

    return remember {
        GaitHolder(
            analyzer = if (GaitApi.configured) {
                HttpGaitAnalyzer(
                    context.applicationContext,
                    petId = { latest },
                    accessToken = { token() },
                )
            } else {
                MockGaitAnalyzer()
            },
            accessToken = { token() },
        )
    }
}
