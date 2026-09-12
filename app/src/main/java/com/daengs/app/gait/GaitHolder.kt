package com.daengs.app.gait

import android.net.Uri
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
    /**
     * 고친 제목의 로컬 저장소. **서버에 수정 API 가 없어서** 여기 둔다 — 한계는
     * [GaitTitleStore] 머리말. null 이면(테스트·프리뷰) 고친 제목이 메모리에만 산다.
     */
    private val titles: GaitTitleStore? = null,
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
     * @param title 사용자가 정한 제목. 서버 `note` 로 같이 올라간다. null 이면 기본값
     * @return 만들어진 기록. 실패하면 null 이고 이유는 [error] 에 남는다
     */
    suspend fun analyze(
        video: PreparedVideo,
        title: String? = null,
        onProgress: (GaitProgress) -> Unit,
    ): GaitRecord? {
        error = null
        return analyzer.analyze(video, onProgress, GaitTitleStore.normalize(title))
            .onSuccess { records = listOf(it) + records }
            .onFailure { error = it.message ?: "보행 영상을 분석하지 못했어요." }
            .getOrNull()
    }

    /**
     * 영상 한 편을 **올리고 접수만** 한다 (#220).
     *
     * [analyze] 와 달리 끝나기를 기다리지 않으므로 목록에 얹을 것도 아직 없다 —
     * 결과는 `GaitAnalysisWorker` 가 알려 준 뒤 [load] 로 받아 온다.
     *
     * @return 접수증. 실패하면 null 이고 이유는 [error] 에 남는다
     */
    suspend fun submit(video: PreparedVideo, title: String? = null): GaitSubmission? {
        error = null
        return analyzer.submit(video, GaitTitleStore.normalize(title))
            .onFailure { error = it.message ?: "보행 영상을 올리지 못했어요." }
            .getOrNull()
    }

    /**
     * 두 기록을 나란히 본다.
     *
     * 판정은 [GaitComparison.of] 가 지표에서 끌어낸다 — 여기서 문장을 고르지 않는다.
     */
    suspend fun compare(recentId: String, pastId: String): GaitComparison? {
        val first = find(recentId) ?: return null
        val second = find(pastId) ?: return null

        // **화면의 "최근"·"비교" 라벨은 날짜가 정한다.** B 진입은 사용자가 고른 순서대로
        // 넘기는데, 그 순서를 그대로 라벨에 쓰면 09.02 가 "최근 기록" 으로 붙는다
        // (에뮬레이터에서 실제로 그랬다). 서버는 어차피 날짜로 past/recent 를 가르므로
        // 여기서 맞춰 두면 표와 라벨이 같은 것을 가리킨다. 날짜가 같으면 넘긴 순서대로.
        val (recent, past) =
            if (second.date.isAfter(first.date)) second to first else first to second

        // 표본끼리는 서버에 없다. 서버 주소가 없을 때도 마찬가지다.
        val sample = recentId.startsWith(SAMPLE_PREFIX) || pastId.startsWith(SAMPLE_PREFIX)
        if (!remote || sample) {
            return GaitComparison.of(recent, past, GaitSampleRecords.jointStatesFor(recent, past))
        }

        val token = accessToken()
        if (token == null) {
            error = "로그인이 필요해요. 다시 로그인해 주세요."
            return null
        }

        // 저장된 기록은 목록에 오버레이 주소가 없다(`has_overlay` 만 온다). 비교 화면이
        // 두 편을 재생하려면 각 상세를 받아 오버레이를 채운다 — 방금 분석한 recent 는
        // 이미 들고 있어 건너뛴다.
        val recentFull = withOverlay(recent, token)
        val pastFull = withOverlay(past, token)

        // ⚠️ **순서를 앱이 정하지 않는다.** 저쪽이 날짜로 past/recent 를 가른다 — A 진입
        //    (방금 분석한 것이 기준)과 B 진입(둘 다 고름)이 서로 다른 순서를 보내도
        //    같은 결과가 나와야 해서다.
        // 제목·보조문구는 **관절 결과에서 앱이 짓는다** — 서버 `message_for_ui` 는 어느
        // 다리인지를 말하지 못해서다 ([GaitJoints.kt] 머리말). `reliability_note` 와
        // `version_warning` 은 저쪽 문장을 그대로 넘긴다.
        return GaitApi.compare(token, recentId, pastId)
            .map {
                GaitComparison.of(
                    recentFull,
                    pastFull,
                    it.toJointStates(),
                    it.versionWarning,
                    it.reliabilityNote,
                )
            }
            .onFailure { error = it.message ?: "두 기록을 비교하지 못했어요." }
            .getOrNull()
    }

    /**
     * 저장된 기록에 오버레이 주소를 채운다. 목록 응답에는 없어서(상세에만 `overlay_url`)
     * 재생하려면 상세를 한 번 받아야 한다. 이미 있거나(방금 분석) 표본이면 그대로 둔다.
     * 상세 조회가 실패해도 원본으로 물러나면 되므로 원래 기록을 돌려준다.
     */
    private suspend fun withOverlay(record: GaitRecord, token: String): GaitRecord {
        if (record.id.startsWith(SAMPLE_PREFIX)) return record
        // 오버레이도 길이도 이미 있으면 받을 것이 없다.
        if (record.overlay != null && record.seconds != null) return record
        val detail = GaitApi.record(token, record.id).getOrNull() ?: return record
        return record.copy(
            overlay = record.overlay ?: detail.overlayUrl?.let(Uri::parse),
            // **길이도 여기서 채운다.** 목록 응답에는 길이가 없어 지난 기록이 "길이 미상"
            // 으로만 떴다 — 비교 화면에서 최근 기록만 길이가 있고 옆은 미상이었다.
            // 상세의 샘플 프레임 수(5fps)로 셈한다 ([GaitAnalyzed.approxSeconds]).
            seconds = record.seconds ?: detail.approxSeconds,
            // 등급·오버레이 유무·권고도 상세가 더 정확하다. 목록에 없던 것은 여기서 채운다.
            qualityTier = record.qualityTier ?: detail.tier,
            hasOverlay = record.hasOverlay || detail.hasOverlay,
            qualityReason = record.qualityReason ?: detail.reason,
            qualityAdvice = record.qualityAdvice ?: detail.recommendation,
            createdBy = detail.createdBy ?: record.createdBy,
            canConfirm = detail.canConfirm ?: record.canConfirm,
            canDelete = detail.canDelete ?: record.canDelete,
        )
    }

    /**
     * 상세 화면을 열 때, 저장된 기록에 오버레이를 채워 목록에 도로 넣는다. 이미 있으면
     * 아무 것도 안 한다 — 조용히 재생본이 생긴다. 실패는 무시한다(원본/자리표시로 물러난다).
     *
     * 목록 전체를 미리 채우지 않는 이유: 기록마다 상세 요청이 하나씩 붙어 목록 로드가
     * 느려진다. **여는 기록만** 그때 채운다.
     */
    suspend fun ensureOverlay(id: String) {
        if (!remote) return
        val record = find(id) ?: return
        if ((record.overlay != null && record.seconds != null) || id.startsWith(SAMPLE_PREFIX)) return
        val token = accessToken() ?: return
        val enriched = withOverlay(record, token)
        // 오버레이든 길이든 하나라도 새로 알았으면 목록에 도로 넣는다.
        if (enriched != record) {
            records = records.map { if (it.id == id) enriched else it }
        }
    }

    /**
     * 저장된 기록끼리 비교할 수 있나 (B 진입).
     *
     * **둘 이상**이어야 고를 것이 생긴다. 하나뿐이면 시트를 띄워도 상대가 없어서,
     * 화면이 버튼을 감추거나 안내를 띄우는 근거로 쓴다.
     */
    val comparablePairExists: Boolean
        get() = records.count { it.comparable } >= 2

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
        if (find(id)?.canDelete == false) {
            error = "대표 보호자만 이 보행 기록을 지울 수 있어요."
            return
        }
        val before = records
        records = records.filterNot { it.id == id }
        titles?.remove(id)
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
     * 제목을 고친다. 상세 화면의 ✎ 가 부른다.
     *
     * **날짜는 손대지 않는다** — 제목과 날짜는 다른 필드이고 여기서는 `title` 만 복사한다.
     * 빈 값이면 제목을 지운다(기본값으로 돌아간다). 서버에는 안 간다 — 수정 API 가
     * 없어서 [GaitTitleStore] 에만 남고, 그 한계는 거기 적혀 있다.
     */
    fun rename(id: String, title: String?) {
        val value = GaitTitleStore.normalize(title)
        titles?.set(id, value)
        records = records.map { if (it.id == id) it.copy(title = value) else it }
    }

    /**
     * 서버에서 이 강아지의 기록을 받아 온다.
     *
     * **실패해도 화면을 비우지 않는다.** 목록이 통째로 사라지면 사용자는 기록이
     * 지워진 줄 안다. 못 받아 왔으면 들고 있던 것을 그대로 두고 [error] 로만 말한다.
     *
     * ### 끝까지 받는다
     *
     * 저쪽은 **오래된 것부터** 준다(서버 설명: "한 강아지의 기록, 오래된 것부터").
     * 그래서 첫 장은 **가장 오래된** [GaitApi.PAGE_LIMIT] 개이고, 새로 만든 기록은
     * 뒷장에 있다. 예전에는 첫 장만 받고 `next_cursor` 를 버려서, 기록이 한 장을
     * 넘기는 순간 **방금 분석한 것이 목록에서 사라졌다** — 분석 직후에는 [analyze] 가
     * 앞에 끼워 넣어 보이다가, 챗을 나갔다 들어오면 이 함수가 첫 장으로 덮었다.
     *
     * 정렬을 바꿔 달라고 할 수는 없다. `GET /app/gait/records` 의 파라미터는
     * `pet_id` · `limit` · `cursor` 뿐이라 **최신순을 요청할 방법이 없다.** 커서가
     * 계약이므로 커서를 따른다.
     *
     * 받아 온 뒤 뒤집는다 — 앱 목록은 최근이 앞이다.
     */
    suspend fun load(petId: String) {
        if (!remote) return
        val token = accessToken() ?: return  // 로그인 전이면 조용히 둔다 — 화면이 아직 뜨는 중이다

        val gathered = gatherGaitPages { cursor -> GaitApi.records(token, petId, cursor = cursor) }

        gathered.error?.let { error = it }
        // **첫 장부터 실패했으면 화면을 안 건드린다.** 목록이 통째로 사라지면 사용자는
        // 기록이 지워진 줄 안다. 뒷장에서 끊겼으면 받은 데까지는 쓴다 — 중간에 끊겼다고
        // 앞 장까지 버리면 있던 기록이 사라져 보인다.
        if (gathered.summaries.isNotEmpty()) publish(gathered.summaries)
    }

    /**
     * 받아 온 요약을 화면용 기록으로 바꿔 [records] 에 얹는다.
     *
     * 제목은 **로컬 수정본 → 서버 note → 기본값** 순이다. 서버에 수정 API 가 없어
     * 고친 제목은 기기에만 있다 ([GaitTitleStore]). 옛 기록은 note 도 없어 title 이
     * null 이고, 화면이 "보행 기록" 을 그린다 — 마이그레이션 없음.
     *
     * **id 로 한 번 걸러 낸다.** 장 사이에 새 기록이 끼면 같은 것이 두 장에 걸쳐 올 수 있다.
     */
    private fun publish(summaries: List<GaitSummary>) {
        records = summaries
            .distinctBy { it.recordId }
            .reversed()
            .map { summary ->
                val record = summary.toRecord()
                titles?.get(record.id)?.let { record.copy(title = it) } ?: record
            }
    }

    companion object {
        /** 화면을 채우려고 만든 기록의 id 접두사. 서버에 없으므로 부르지 않는다. */
        const val SAMPLE_PREFIX = "sample-"

        /**
         * [load] 가 한 번에 따라갈 장 수의 상한.
         *
         * 커서를 끝까지 따라가는 것이 맞지만, **끝이 없을 수도 있는 반복은 안 둔다** —
         * 서버가 커서를 잘못 주면 화면 하나 여는 데 요청이 무한히 나간다. 장당
         * [GaitApi.PAGE_LIMIT] 개이므로 이 값이면 기록 [GaitApi.PAGE_LIMIT] × [MAX_PAGES]
         * 개까지 닿는다 — 한 마리가 그만큼 쌓으려면 매일 찍어도 몇 해가 걸린다.
         *
         * 그보다 많아지면 이 방식(전부 받아 오기) 자체가 틀린 것이고, 그때는 서버에
         * 최신순 정렬을 요청하는 편이 맞다.
         */
        const val MAX_PAGES = 10
    }
}

/**
 * 커서를 따라 모은 결과.
 *
 * [error] 가 있어도 [summaries] 에는 **받은 데까지** 담겨 있다 — 뒷장에서 끊긴 것과
 * 첫 장부터 실패한 것을 부르는 쪽이 갈라야 해서다 (전자는 있는 것만이라도 보여 주고,
 * 후자는 화면을 안 건드린다).
 */
internal data class GatheredGaitPages(
    val summaries: List<GaitSummary>,
    val error: String? = null,
    /** 상한([GaitHolder.MAX_PAGES])에 걸려 멈췄나. 걸렸으면 최신 장에 못 닿았을 수 있다. */
    val hitPageLimit: Boolean = false,
)

/**
 * `next_cursor` 를 따라 목록을 끝까지 받는다.
 *
 * **[GaitHolder] 밖으로 뺀 이유는 테스트다.** [GaitApi] 가 오브젝트라 홀더 안에서는
 * 가짜 응답을 끼울 자리가 없고, 그러면 "2장째에 새 기록이 온다" 같은 규칙을 사람이
 * 눈으로만 지켜야 한다. 이 저장소는 같은 이유로 [GaitRecord.summaryLines] 도 화면
 * 밖으로 내렸다.
 *
 * 멈추는 조건 셋:
 *  - `next_cursor` 가 없다 — 마지막 장
 *  - 같은 커서가 다시 왔다 — 서버가 잘못 준 것이다. 안 막으면 같은 장을 [maxPages] 번 받는다
 *    (`OwnedTerritoryBrowser` 가 쓰는 방어와 같다)
 *  - [maxPages] 를 채웠다 — 끝이 없을 수도 있는 반복은 안 둔다
 *
 * @param fetch 커서 하나로 한 장을 받아 오는 것. `null` 이면 첫 장
 */
internal suspend fun gatherGaitPages(
    maxPages: Int = GaitHolder.MAX_PAGES,
    fetch: suspend (cursor: String?) -> Result<GaitPage>,
): GatheredGaitPages {
    val gathered = mutableListOf<GaitSummary>()
    val seen = mutableSetOf<String>()
    var cursor: String? = null

    repeat(maxPages) {
        val result = fetch(cursor)
        val page = result.getOrNull()
            ?: return GatheredGaitPages(
                gathered,
                result.exceptionOrNull()?.message ?: "기록을 받아오지 못했어요.",
            )

        gathered += page.records

        val next = page.nextCursor
        if (next == null || next == cursor || !seen.add(next)) {
            return GatheredGaitPages(gathered)
        }
        cursor = next
    }

    return GatheredGaitPages(gathered, hitPageLimit = true)
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
            titles = GaitTitleStore(context.applicationContext),
        )
    }
}
