package com.daengs.app.gait

/**
 * 분석 진행 카드의 네 줄.
 *
 * 순서가 고정이라 `enum` 의 선언 순서가 곧 화면의 순서다. **줄을 늘리려면 여기에
 * 넣는다** — 카드가 `entries` 를 그대로 그리므로 화면은 안 고쳐도 된다.
 */
enum class GaitStage(val label: String) {
    Video("영상 확인"),
    Joints("관절 추출"),
    Walk("보행 분석"),
    Summary("결과 정리"),
}

/** 한 줄의 상태. 색과 오른쪽 글자가 여기서 갈린다. */
enum class GaitStageState(val label: String) {
    Done("완료"),
    Running("진행 중"),
    Waiting("대기 중"),
}

/**
 * 분석이 어디까지 왔나.
 *
 * **끝난 개수 하나로 들고 있는다.** 줄마다 상태를 따로 저장하면 "2번은 완료인데
 * 1번은 대기" 같은 있을 수 없는 상태가 표현돼 버린다. 진행은 앞에서 뒤로만 가므로
 * 숫자 하나면 충분하고, 그 숫자에서 [stateOf] 가 각 줄을 끌어낸다.
 *
 * @param done 끝난 단계 수. `0` 이면 첫 줄이 진행 중, [GaitStage.entries] 크기와
 *   같으면 전부 완료다.
 */
data class GaitProgress(val done: Int) {

    val finished: Boolean get() = done >= GaitStage.entries.size

    fun stateOf(stage: GaitStage): GaitStageState = when {
        stage.ordinal < done -> GaitStageState.Done
        stage.ordinal == done -> GaitStageState.Running
        else -> GaitStageState.Waiting
    }

    fun next(): GaitProgress = GaitProgress((done + 1).coerceAtMost(GaitStage.entries.size))

    companion object {
        val START = GaitProgress(0)
    }
}
