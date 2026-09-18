package com.daengs.app.notify

import android.content.Context

/**
 * 「산책 일기 장면이 준비됐어요」 알림 (#8).
 *
 * ### 왜 Worker 에서만 띄우나
 *
 * 일기 장면은 두 군데서 준비된다. 사용자가 일기 화면에서 직접 만들 때와, 산책이 끝난 뒤
 * [com.daengs.app.walk.sync.WalkDeliveryWorker] 가 앱 밖에서 서버와 맞출 때다. **앞은
 * 알릴 필요가 없다** — 보고 있는 화면이 바로 바뀐다. 알림은 뒤에만 붙는다.
 *
 * ### 왜 "성공" 이 아니라 "준비됨" 인가
 *
 * `syncPendingSession` 이 성공해도 장면이 준비됐다는 뜻이 아니다. 그 함수는 `Unit` 을
 * 돌려주고, 장면은 서버가 `ready` 를 줬을 때 로컬 DB(`WalkSceneAnalysisRow`)에 들어간다 —
 * 서버가 아직 `running` 이면 성공하고도 장면이 없다. 그래서 왕복 **전후의 상태를 비교해서**
 * 이번에 준비된 것만 알린다 ([diarySceneBecameReady]).
 */

/** 알림이 `MainActivity` 에 실어 보내는 것. 그 산책의 일기로 데려간다. */
const val EXTRA_OPEN_WALK_DIARY = "com.daengs.app.walk.OPEN_DIARY"

/** 서버가 장면을 다 만들었을 때의 상태 값. `WalkSceneAnalysisRow.status` 의 것이다. */
const val DIARY_SCENE_READY = "ready"

/**
 * 이번 왕복에서 장면이 준비됐나.
 *
 * **이미 `ready` 였으면 다시 알리지 않는다.** 같은 산책의 전달 작업은 네트워크가 돌아올
 * 때마다 다시 돌 수 있어서, 상태만 보고 띄우면 준비된 장면 하나로 알림이 여러 번 뜬다.
 */
fun diarySceneBecameReady(before: String?, after: String?): Boolean =
    after == DIARY_SCENE_READY && before != DIARY_SCENE_READY

/**
 * 알림 자리.
 *
 * **접두사를 붙여 보행 기록과 가른다.** 보행 완료 알림은 `recordId.hashCode()` 를 쓰는데,
 * 둘 다 서버가 준 문자열 id 라 날것으로 해싱하면 값이 겹칠 수 있다 — 겹치면 한쪽이
 * 다른 쪽을 덮어써서 **알림이 조용히 사라진다.**
 */
fun diaryNoticeId(sessionId: String): Int = "walk-diary:$sessionId".hashCode()

/** 장면이 준비됐다고 알린다. 실제로 띄웠으면 `true`. */
fun postWalkDiaryNotice(context: Context, sessionId: String): Boolean =
    postResultNotice(
        context = context,
        id = diaryNoticeId(sessionId),
        title = DIARY_READY_TITLE,
        text = DIARY_READY_TEXT,
        extras = mapOf(EXTRA_OPEN_WALK_DIARY to sessionId),
    )

private const val DIARY_READY_TITLE = "산책 일기 장면이 준비됐어요."
private const val DIARY_READY_TEXT = "오늘 산책을 일기로 남겨 보세요."
