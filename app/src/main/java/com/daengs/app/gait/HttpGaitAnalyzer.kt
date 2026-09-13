package com.daengs.app.gait

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 진짜 서버를 부르는 [GaitAnalyzer]. [MockGaitAnalyzer] 자리에 그대로 끼운다.
 *
 * **인터페이스는 하나도 안 바꿨다** — [MockGaitAnalyzer] 는 테스트와 `@Preview` 가
 * 계속 쓴다. 서버가 죽어 있어도 화면을 돌려 볼 수 있어야 하고, 단위 테스트가
 * 네트워크를 타면 안 된다. 옛 구현과 달라진 것은 [analyze] 의 **속뿐**이다.
 *
 * ### 한 방 요청이 네 걸음이 됐다 (#64 · D-043)
 *
 * ```
 * ① analyze   기록(PENDING) + 업로드 티켓
 * ② upload    영상 바이트를 티켓이 가리키는 곳으로 (backend 가 아니다)
 * ③ confirm   서버가 실존을 확인하고 분석 큐에 넣는다
 * ④ poll      status 가 DONE/FAILED 가 될 때까지 단건 조회
 * ```
 *
 * 옛 구현은 `POST /gait/analyze` 하나로 끝났고 **인증이 없었다.** 이제 매 걸음에
 * 토큰이 붙고 소유권을 서버가 본다.
 *
 * ### 단계를 지어내지 않는다 — 이 규칙은 그대로다
 *
 * 서버가 알려 주는 것은 `PENDING · UPLOADED · PROCESSING · DONE · FAILED` 뿐이라,
 * **관절을 뽑는 중인지 보행을 재는 중인지는 여전히 모른다.** 그래서 아는 것만 올린다:
 *
 * - 시작 → `0` (영상 확인 진행 중)
 * - ③이 성공 → `1` **서버가 파일이 있다고 확인해 준 시점**이라 "영상 확인" 은 진짜로
 *   끝났다. 이어서 워커가 하는 첫 일이 관절 추출이라 그 줄이 진행 중이 된다
 * - `DONE` → 전부 완료
 *
 * 중간 두 줄(`보행 분석`·`결과 정리`)을 시간에 맞춰 넘기면 화면은 그럴듯해지지만
 * **거짓말이 된다.** 서버가 그 경계를 알려 주면 그때 채운다.
 *
 * ⚠️ **분 단위로 걸린다.** 저쪽 실측이 116MB · 37초 영상에 CPU 약 2분이다.
 */
class HttpGaitAnalyzer(
    private val context: Context,
    /**
     * 기록을 묶는 열쇠. **서버가 만든 진짜 `pets.id` UUID 다.**
     *
     * 옛 구현의 `dogId` 와 이름만 비슷하고 성격이 다르다 — 저쪽이 이 값으로
     * `pets.app_user_id` 까지 따라가 **토큰의 주인이 맞는지 확인**한다.
     * 남의 것을 넣으면 404 다.
     */
    private val petId: () -> String?,
    /**
     * access token. **매 걸음 직전에 새로 받는다** — 분석이 분 단위라 시작할 때 받은
     * 토큰이 폴링 도중 만료될 수 있다. 이 람다가 재발급까지 맡는다 (`freshToken`).
     */
    private val accessToken: suspend () -> String?,
    private val today: () -> LocalDate = LocalDate::now,
) : GaitAnalyzer {

    /**
     * ①②③ 만 하고 **접수증을 돌려준다.** ④(끝날 때까지 조회)는 안 한다 — 그건
     * `GaitAnalysisWorker` 가 앱 밖에서 맡는다 (#220).
     *
     * 여기서 멈추는 시점이 중요하다. ③ `confirm` 이 성공했다는 것은 **서버가 파일이
     * 실제로 있는 것을 확인하고 분석 큐에 넣었다**는 뜻이다. 그 전에 손을 놓으면
     * 올라가다 만 영상이 큐에 안 들어간 채 남는다.
     */
    override suspend fun submit(
        video: PreparedVideo,
        title: String?,
    ): Result<GaitSubmission> = runCatching {
        val pet = petId() ?: error(
            "어느 강아지의 기록인지 몰라 올릴 수 없어요.\n강아지를 먼저 등록해 주세요.",
        )
        GaitApi.oversizeMessage(context, video.uri)?.let { error(it) }

        val ticket = GaitApi.startAnalysis(
            accessToken = token(),
            petId = pet,
            sourceFile = GaitApi.displayNameOf(context, video.uri),
            contentType = GaitApi.contentTypeOf(context, video.uri),
            capturedAt = today(),
            note = title,
        ).getOrThrow()

        GaitApi.upload(context, ticket, video.uri).getOrThrow()

        val confirmed = GaitApi.confirm(token(), ticket.recordId).getOrThrow()

        GaitSubmission(
            recordId = ticket.recordId,
            // 저쪽이 confirm 응답에 status 를 준다. 아주 짧은 영상이면 여기서 이미
            // DONE 인 일이 있어, 부르는 쪽이 Worker 를 안 걸고 바로 넘어갈 수 있다.
            status = confirmed.status,
            title = title,
        )
    }

    override suspend fun analyze(
        video: PreparedVideo,
        onStage: (GaitProgress) -> Unit,
        title: String?,
    ): Result<GaitRecord> = runCatching {
        val pet = petId() ?: error(
            "어느 강아지의 기록인지 몰라 올릴 수 없어요.\n강아지를 먼저 등록해 주세요.",
        )
        // 올리기 전에 크기부터. 150MB 를 다 보내고 413 을 받으면 데이터도 시간도 버린다.
        GaitApi.oversizeMessage(context, video.uri)?.let { error(it) }

        onStage(GaitProgress.START)
        val date = today()

        // ① 티켓
        val ticket = GaitApi.startAnalysis(
            accessToken = token(),
            petId = pet,
            sourceFile = GaitApi.displayNameOf(context, video.uri),
            contentType = GaitApi.contentTypeOf(context, video.uri),
            capturedAt = date,
            // 제목은 저쪽 `note` 로 간다 — 지금 서버에는 title 컬럼이 없다. 별도 메모가
            // 생기면 갈라야 한다 ([GaitTitleStore] 머리말).
            note = title,
        ).getOrThrow()

        // ② 업로드 — 티켓이 준 주소·헤더 그대로. 우리 토큰을 얹지 않는다.
        GaitApi.upload(context, ticket, video.uri).getOrThrow()

        // ③ 실존 확인 + 큐 발행
        GaitApi.confirm(token(), ticket.recordId).getOrThrow()
        onStage(GaitProgress(1))

        // ④ 끝날 때까지 조회
        val finished = poll(ticket.recordId)
        if (finished.status == GaitStatus.FAILED) {
            // **저쪽 failure_reason 을 그대로 띄우지 않는다** — 운영 진단용이라 내부 경로가
            // 들어 있을 수 있다. 사용자에게는 다시 해볼 수 있다는 것만 알린다.
            error("분석을 마치지 못했어요. 잠시 뒤에 다시 시도해 주세요.")
        }
        onStage(GaitProgress(GaitStage.entries.size))

        GaitRecord(
            id = finished.recordId,
            date = finished.date ?: date,
            // 길이는 **기기에서 읽은 값**이다. 저쪽 응답에 없다.
            seconds = video.seconds,
            video = video.uri,
            // 서버가 만든 스켈레톤 영상. 있으면 재생 화면이 원본 대신 이걸 튼다. 저장소가
            // 미설정이면 저쪽이 null 로 주고, 그때는 방금 찍은 원본으로 물러난다.
            overlay = finished.overlayUrl?.let(Uri::parse),
            thumbnail = video.thumbnail,
            // **앱이 정하지 않는다.** 저쪽 quality_status 가 그대로 온다.
            comparable = finished.qualityOk,
            // 사유도 같이 나른다. 여기서 버리면 화면에는 불리언만 남아,
            // 왜 못 쓰는지를 앱이 지어내게 된다.
            qualityReason = finished.reason,
            qualityAdvice = finished.recommendation,
            // 화면이 영상 비율대로 자리를 잡는다. 저쪽 응답에 크기가 없어서
            // 기기에서 읽은 값이 유일한 근거다.
            aspect = video.aspect,
            // 요약 문장이 등급으로 갈린다. 숫자(유효 프레임 등)는 여기서 버린다.
            qualityTier = finished.tier,
            hasOverlay = finished.hasOverlay,
            title = title,
        )
    }

    /**
     * 끝날 때까지 단건 조회를 되풀이한다.
     *
     * **매번 토큰을 새로 받는다** — access token 이 5분이라 2분짜리 분석 하나에도
     * 중간에 만료될 수 있다. 시작할 때 받은 것을 들고 있으면 폴링이 401 로 죽는다.
     *
     * 조회가 한 번 실패해도 바로 포기하지 않는다. 이동 중에 잠깐 끊기는 것이 흔한데,
     * 그때마다 분석을 통째로 실패로 만들면 **서버에서는 멀쩡히 끝난 기록**을 사용자가
     * 못 보게 된다. 연속으로 실패할 때만 손을 든다.
     */
    private suspend fun poll(recordId: String): GaitAnalyzed {
        var misses = 0
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val result = GaitApi.record(token(), recordId)
            val record = result.getOrNull()
            if (record == null) {
                if (++misses > MAX_CONSECUTIVE_MISSES) {
                    throw result.exceptionOrNull()
                        ?: IllegalStateException("분석 상태를 확인하지 못했어요.")
                }
                continue
            }
            misses = 0
            if (record.settled) return record
        }
        error("분석이 오래 걸리고 있어요. 잠시 뒤 목록에서 다시 확인해 주세요.")
    }

    /** 없으면 로그인이 풀린 것이다. 여기서 멈춰야 아래 호출들이 401 로 흩어지지 않는다. */
    private suspend fun token(): String =
        accessToken() ?: error("로그인이 필요해요. 다시 로그인해 주세요.")

    private companion object {
        /** 분석이 분 단위라 촘촘히 물을 이유가 없다. 배터리와 서버 양쪽에 낫다. */
        const val POLL_INTERVAL_MS = 5_000L

        /** 저쪽 실측이 2분 남짓. 넉넉히 두되 무한정 기다리지는 않는다. */
        const val POLL_TIMEOUT_MS = 10 * 60 * 1000L

        /** 이동 중 한두 번 끊기는 것은 넘어간다. */
        const val MAX_CONSECUTIVE_MISSES = 5
    }
}
