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
) {
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
        )
    }
}
