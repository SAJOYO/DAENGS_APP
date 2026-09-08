package com.daengs.app.dogcard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect
import com.daengs.app.dogcard.store.DrawnCardRow

/**
 * 뽑아 놓은 카드 한 장. 화면이 보는 모양이다.
 *
 * 표의 [DrawnCardRow] 와 거의 같지만 `core` 를 네 칸이 아니라 [IntRect] 로 든다 —
 * 그리는 쪽이 쓰는 모양이 그것이라, 화면마다 다시 조립하지 않게 여기서 한 번 묶는다.
 */
@Immutable
data class DrawnCard(
    val id: String,
    val appUserId: String?,
    val templateId: String,
    val dogId: String?,
    val dogName: String,
    val drawnAtMillis: Long,
    val codeText: String,
    val core: IntRect,
    /**
     * 사용자가 원형 틀에 직접 맞춘 카드인가. `DrawnCardRow.userFramed` 참고.
     *
     * 그리는 쪽(`drawInHoleOf`)이 이 값으로 갈린다 — 맞춘 카드는 **가운데 정렬 + 덮기**
     * 한 번이고, 옛 카드는 예전 보정을 그대로 받는다.
     */
    val userFramed: Boolean = false,
) {
    /**
     * 얼굴이 낀 카드인가. 곧 **뽑아서 만든 카드인가** 와 같은 말이다.
     *
     * 예전 디버그 빌드가 첫 실행에 넣어 두던 시드 열두 장은 뽑은 게 아니라 원래 있던
     * 카드라 누끼가 없고, 그래서 얼굴 자리도 비어 있었다. 시더는 없앴지만 그 줄이 남은
     * 폰이 있어 `CardHolder.load` 가 이 값으로 골라 지운다. 서버는 빈 사각형을 거절하므로
     * 뽑은 카드는 언제나 true 다.
     *
     * 컬럼을 새로 두지 않는다. 얼굴 자리가 없다는 것이 이미 그 뜻이다.
     */
    val drawn: Boolean get() = core.width > 0 && core.height > 0

    fun toRow(): DrawnCardRow = DrawnCardRow(
        id = id,
        appUserId = appUserId,
        templateId = templateId,
        dogId = dogId,
        dogName = dogName,
        drawnAtMillis = drawnAtMillis,
        codeText = codeText,
        coreLeft = core.left,
        coreTop = core.top,
        coreRight = core.right,
        coreBottom = core.bottom,
        userFramed = userFramed,
    )

    companion object {
        fun of(row: DrawnCardRow): DrawnCard = DrawnCard(
            id = row.id,
            appUserId = row.appUserId,
            templateId = row.templateId,
            dogId = row.dogId,
            dogName = row.dogName,
            drawnAtMillis = row.drawnAtMillis,
            codeText = row.codeText,
            core = IntRect(row.coreLeft, row.coreTop, row.coreRight, row.coreBottom),
            userFramed = row.userFramed,
        )
    }
}
