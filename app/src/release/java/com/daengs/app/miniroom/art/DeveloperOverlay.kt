package com.daengs.app.miniroom.art

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.daengs.app.miniroom.DogActor
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.RoomGeometry

/**
 * 릴리스용 빈 껍데기. **진짜는 `app/src/debug/` 에 있다** —
 * 격자·바닥 윤곽·소품 발자국·강아지 반경을 방 위에 덧그린다.
 *
 * 왜 소스셋으로 가르는지는 짝인 `ui/home/DeveloperPanel.kt` 스텁에 적어 뒀다.
 * 여기서도 **시그니처가 debug 쪽과 같아야 하고**, 아무것도 그리면 안 된다.
 */
fun DrawScope.drawDeveloperOverlay(
    g: RoomGeometry,
    state: MiniRoomState,
    catalog: ItemCatalog,
    dogs: List<DogActor>,
    measurer: TextMeasurer,
) = Unit
