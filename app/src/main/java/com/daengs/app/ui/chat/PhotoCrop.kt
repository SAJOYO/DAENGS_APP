package com.daengs.app.ui.chat

import android.graphics.Bitmap

/**
 * 확정한 네모대로 사진을 자른다. **대화 말풍선에 올릴 그림용이다.**
 *
 * 자르는 셈은 [pixelBoxOf] 가 하고 여기는 비트맵만 만든다 — 화면과 떼어 둔 계산에
 * 안드로이드 의존을 들이지 않으려고 갈라 뒀다 ([CropGeometry] 첫 주석).
 *
 * **서버로 가는 사진은 안 자른다.** 저쪽은 원본과 `bbox` 를 같이 받아 학습과 같은
 * 함수로 자른다 (`src/agent.py` 의 `crop_for`). 여기서 미리 잘라 보내면 2단계 배율이
 * 어긋난다. 자르는 것은 **보여 주는 쪽뿐**이다.
 *
 * @return 자른 그림. 자를 수 없으면(빈 그림·이상한 네모) 원본을 그대로 돌려준다 —
 *   말풍선이 비는 것보다 통째로라도 보이는 편이 낫다
 */
fun cropForBubble(photo: Bitmap, box: FloatArray): Bitmap {
    val at = pixelBoxOf(box, photo.width, photo.height) ?: return photo
    // 통째나 다름없으면 새 비트맵을 안 만든다. 그럴 때는 자른 티도 안 나고 메모리만 쓴다.
    if (at.left == 0 && at.top == 0 && at.width == photo.width && at.height == photo.height) {
        return photo
    }
    return runCatching {
        Bitmap.createBitmap(photo, at.left, at.top, at.width, at.height)
    }.getOrDefault(photo)
}
