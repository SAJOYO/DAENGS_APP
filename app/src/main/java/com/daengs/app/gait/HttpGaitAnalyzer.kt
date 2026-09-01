package com.daengs.app.gait

import android.content.Context
import java.time.LocalDate

/**
 * 진짜 서버를 부르는 [GaitAnalyzer]. [MockGaitAnalyzer] 자리에 그대로 끼운다.
 *
 * 인터페이스는 하나도 안 바꿨다 — [MockGaitAnalyzer] 는 테스트와 `@Preview` 가
 * 계속 쓴다. 서버가 죽어 있어도 화면을 돌려 볼 수 있어야 하고, 단위 테스트가
 * 네트워크를 타면 안 된다.
 *
 * ### 단계를 지어내지 않는다
 *
 * 저쪽 `POST /gait/analyze` 는 **동기 한 방**이다 (API.md "비동기 job/poll 은 이번
 * 범위에 없다"). 그래서 서버가 지금 관절을 뽑는 중인지 보행을 재는 중인지 앱은
 * 알 수 없다. 아는 것은 둘뿐이라 그 둘만 [onStage] 로 올린다 —
 * **업로드가 끝난 시점**과 **응답이 온 시점**.
 *
 * 중간 두 줄을 시간에 맞춰 넘기면 화면은 그럴듯해지지만 거짓말이 된다. 폴링이
 * 생기면(`docs/orchestration-contracts.md` 의 `job: {job_id, poll}`) 그때 진짜
 * 단계로 채운다.
 *
 * ⚠️ **분 단위로 걸린다.** 저쪽 실측이 37초 영상에 CPU 약 2분이다.
 */
class HttpGaitAnalyzer(
    private val context: Context,
    /**
     * 기록을 묶는 열쇠. **없으면 목록으로 다시 못 찾는다** — 저쪽 목록이 `dog_id`
     * 로만 거른다. 대표 강아지의 id 를 넣는다.
     */
    private val dogId: () -> String?,
    private val today: () -> LocalDate = LocalDate::now,
) : GaitAnalyzer {

    override suspend fun analyze(
        video: PreparedVideo,
        onStage: (GaitProgress) -> Unit,
    ): Result<GaitRecord> {
        val dog = dogId()
            ?: return Result.failure(
                IllegalStateException("어느 강아지의 기록인지 몰라 올릴 수 없어요.\n강아지를 먼저 등록해 주세요."),
            )

        onStage(GaitProgress.START)
        val date = today()
        return GaitApi.analyze(context, video.uri, dogId = dog, date = date)
            .map { analyzed ->
                onStage(GaitProgress(GaitStage.entries.size))
                GaitRecord(
                    id = analyzed.recordId,
                    date = analyzed.date ?: date,
                    // 길이는 **기기에서 읽은 값**이다. 저쪽 응답에 없다.
                    seconds = video.seconds,
                    video = video.uri,
                    thumbnail = video.thumbnail,
                    // **앱이 정하지 않는다.** 저쪽 quality.status 가 그대로 온다 —
                    // Mock 은 길이로 정했지만 그건 서버가 없을 때의 임시였다.
                    comparable = analyzed.qualityOk,
                )
            }
            .recoverCatching { cause ->
                // 저쪽이 왜 못 썼는지 문장으로 준다(`quality.recommendation`). 그건
                // 사용자에게 보여 줄 말이라 그대로 올린다.
                throw cause
            }
    }
}
