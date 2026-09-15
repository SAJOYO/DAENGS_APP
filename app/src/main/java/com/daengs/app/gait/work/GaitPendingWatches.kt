package com.daengs.app.gait.work

import androidx.work.WorkInfo

/**
 * 보행 완료 감지 작업에 다는 tag.
 *
 * **왜 tag 인가.** 챗에 다시 들어왔을 때 진행 중인 카드를 되살리려면 "이 강아지의 어느
 * 기록을 지켜보는 중인지" 를 알아야 한다. 그런데 WorkManager 가 돌려주는 [WorkInfo] 에는
 * **입력 데이터가 없고 tag 만 있다.** 그래서 등록할 때 강아지와 기록을 tag 로도 남긴다.
 *
 * Worker 의 companion 이 아니라 따로 둔 것은, 고르는 판단([pendingGaitRecords])을 Worker
 * 클래스 없이 순수 JVM 테스트로 잡으려는 것이다.
 */
internal object GaitWatchTags {
    private const val PET = "gait-pet:"
    private const val RECORD = "gait-record:"

    /** 이 강아지의 작업을 한 번에 찾는 tag. `getWorkInfosByTagFlow` 에 쓴다. */
    fun pet(petId: String): String = "$PET$petId"

    fun record(recordId: String): String = "$RECORD$recordId"

    /** [tags] 에서 기록 id 를 꺼낸다. 이 변경 전에 등록된 작업은 없으므로 null 이다. */
    fun recordIdOf(tags: Set<String>): String? =
        tags.firstOrNull { it.startsWith(RECORD) }?.removePrefix(RECORD)
}

/**
 * 챗에 다시 들어왔을 때 **"보행 분석 중" 카드로 되살릴 기록**을 고른다.
 *
 * 챗 대화는 서버 이력에서 다시 그려지는데 보행 카드는 거기 없다. 진행 중인지는 WorkManager
 * 가 이미 정확히 알고 있으므로, 같은 사실을 따로 저장하지 않고 거기서 고른다.
 *
 *  - **이 강아지 것만** — 대표를 바꿔 들어오면 남의 아이 카드가 붙으면 안 된다. 강아지를
 *    모르면([petId] 가 null) 아무것도 고르지 않는다
 *  - **아직 안 끝난 것만** — 끝난 것은 알림으로 들어온 완료 경로와 완료 카드가 맡는다.
 *    여기서 "분석 중" 으로 되살리면 끝난 분석이 영영 도는 것처럼 보인다
 *  - **이미 대화에 있는 기록은 빼고** — 방금 접수해 카드가 떠 있거나 완료 카드로 바뀐
 *    기록을 또 붙이면 겹친다
 *
 * @param shownRecordIds 대화에 이미 있는 보행 카드들의 기록 id (진행 중 · 완료 모두)
 * @return 붙일 기록 id. [works] 의 순서를 따르고 같은 기록은 한 번만
 */
internal fun pendingGaitRecords(
    works: List<WorkInfo>,
    petId: String?,
    shownRecordIds: Set<String>,
): List<String> {
    if (petId == null) return emptyList()
    val petTag = GaitWatchTags.pet(petId)
    return works.asSequence()
        .filter { !it.state.isFinished && petTag in it.tags }
        .mapNotNull { GaitWatchTags.recordIdOf(it.tags) }
        .filter { it !in shownRecordIds }
        .distinct()
        .toList()
}
