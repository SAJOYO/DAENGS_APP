package com.daengs.app.gait

import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 영상을 넘기면 단계가 하나씩 올라가고 기록이 나오는 자리.
 *
 * **서버가 아직 없다.** `CONTEXT.md` 2절에 "보행 체크 = 영상 기록용" 이라고 성격만
 * 적혀 있고, 부를 주소도 응답 모양도 정해진 게 없다. 그래서 여기서 엔드포인트를
 * 지어내지 않고 **인터페이스만 판다** — 계약이 생기면 [MockGaitAnalyzer] 자리에
 * 진짜 구현을 끼우면 되고, 화면과 홀더는 안 건드린다.
 *
 * 계약이 붙을 때 바뀌는 것은 [analyze] 의 속뿐이다. `CONTEXT.md` 2절이 보행을
 * **비동기**(job_id → 큐 → 워커)로 적어 뒀으므로, 진짜 구현도 `job_id` 를 받아
 * 폴링하며 같은 [onStage] 를 부르는 모양이 된다.
 */
interface GaitAnalyzer {

    /**
     * @param video 고르거나 찍은 영상
     * @param onStage 한 단계가 끝날 때마다 불린다. 화면이 이걸로 진행 카드를 다시 그린다
     * @param title 사용자가 정한 제목. null 이면 정하지 않은 것 — 기본값은 화면이 그린다
     * @return 대화에 남을 기록
     */
    suspend fun analyze(
        video: PreparedVideo,
        onStage: (GaitProgress) -> Unit,
        title: String? = null,
    ): Result<GaitRecord>
}

/**
 * 서버가 붙기 전까지 화면을 돌려 보는 구현.
 *
 * **판정을 지어내지 않는다.** 여기서 하는 일은 단계를 시간에 맞춰 넘기고, 영상에서
 * 실제로 읽은 것(길이·표지)만 [GaitRecord] 에 담는 것뿐이다. [GaitRecord.comparable]
 * 도 모델이 뭘 봐서 정한 값이 아니라 **길이만 보고** 정한다 — 너무 짧으면 걸음 한
 * 주기가 안 담기므로 비교 대상으로 못 세운다.
 *
 * 진짜 서버가 붙으면 이 판단도 저쪽으로 넘어간다.
 */
class MockGaitAnalyzer(
    /** 단계 하나에 걸리는 시간. 테스트는 0 으로 두고 기다리지 않는다. */
    private val stepMillis: Long = 900L,
    private val today: () -> LocalDate = LocalDate::now,
) : GaitAnalyzer {

    override suspend fun analyze(
        video: PreparedVideo,
        onStage: (GaitProgress) -> Unit,
        title: String?,
    ): Result<GaitRecord> = runCatching {
        var progress = GaitProgress.START
        onStage(progress)
        repeat(GaitStage.entries.size) {
            delay(stepMillis)
            progress = progress.next()
            onStage(progress)
        }
        GaitRecord(
            id = "gait-${System.currentTimeMillis()}",
            // **기록을 만든 날**이다. 영상 파일의 날짜가 아니다 — 서버 구현도 같다.
            date = today(),
            seconds = video.seconds,
            video = video.uri,
            thumbnail = video.thumbnail,
            comparable = video.seconds >= GaitRecord.RECOMMENDED_SECONDS,
            aspect = video.aspect,
            title = title,
        )
    }
}

/**
 * 지난 기록. **화면을 채우려고 만든 것이다.**
 *
 * 진짜 목록은 서버에서 온다 (`CONTEXT.md` 6절의 Object Storage). 그때 [GaitHolder]
 * 가 이 값 대신 받아 온 목록으로 시작하면 되고, 여기 있는 날짜·길이는 시안(6번 화면)
 * 에 적힌 것을 그대로 옮겼다.
 *
 * 07.15 만 [GaitRecord.comparable] 이 false 다 — 목록에서 "비교 가능 여부" 가 실제로
 * 갈리는 모습을 봐야, 못 고르는 줄이 흐리게 나오는 것을 확인할 수 있다.
 */
object GaitSampleRecords {

    fun of(today: LocalDate = LocalDate.now()): List<GaitRecord> = listOf(
        GaitRecord("sample-0810", today.minusDays(21), seconds = 15),
        GaitRecord("sample-0730", today.minusDays(32), seconds = 11),
        GaitRecord("sample-0715", today.minusDays(47), seconds = 9, comparable = false),
        GaitRecord("sample-0701", today.minusDays(61), seconds = 13),
    )

    /**
     * 비교표에 올릴 관절 여섯 줄.
     *
     * **서버가 주는 것과 같은 모양이어야 한다.** 표본만 다른 모양이면 화면이 표본에서만
     * 맞고 진짜 응답에서 어긋난다 — `@Preview` 로 본 것이 실기기와 다른 화면이 된다.
     *
     * 어느 줄이 어떤 갈래가 될지는 **두 기록의 id 로 정한다.** 무작위로 뽑으면 같은
     * 조합을 다시 열 때 결과가 바뀌어서, 화면을 보는 사람이 자기가 뭘 잘못 눌렀나
     * 헷갈린다.
     *
     * [GaitJointChange.Unknown] 은 안 뽑는다 — 표본은 "잴 수 있었던 경우" 를 그리는
     * 자리이고, 못 잰 경우는 `comparable = false` 인 표본이 이미 만든다.
     */
    fun jointStatesFor(recent: GaitRecord, past: GaitRecord): List<GaitJointState> {
        val seed = recent.id.hashCode() xor past.id.hashCode()
        val picks = listOf(
            GaitJointChange.None,
            GaitJointChange.Horizontal,
            GaitJointChange.Vertical,
            GaitJointChange.Both,
        )
        return GaitJoint.entries.mapIndexed { index, joint ->
            GaitJointState(joint, picks[(seed shr index).mod(picks.size)])
        }
    }
}
