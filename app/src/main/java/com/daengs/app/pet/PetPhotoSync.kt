package com.daengs.app.pet

/**
 * 한 아이의 사진을 **어느 쪽으로 옮겨야 하나.**
 *
 * 이 셈만 따로 꺼내 둔 이유는 [PetPhotoTarget] 과 같다 — **틀리면 사진이 사라지거나
 * 남의 판으로 덮인다.** 화면에서는 "왜 사진이 없어졌지" 로만 보이고 원인이 안 보이는
 * 종류라, 네트워크·파일과 떼어 놓고 이것만 시험할 수 있게 둔다.
 */
enum class PhotoAction {
    /** 할 일 없음. 서버와 기기가 같은 판이거나, 양쪽 다 없다. */
    NOTHING,

    /** 기기 것을 서버로. 서버에 없거나, 아직 한 번도 안 올린 사진이다. */
    UPLOAD,

    /** 서버 것을 기기로. 서버가 더 새 판이거나 기기에 없다. */
    DOWNLOAD,

    /** 기기 것을 지운다. **다른 기기에서 지웠다는 뜻이다.** */
    DELETE_LOCAL,
}

/**
 * 무엇을 할지 정한다.
 *
 * @param serverHasPhoto 목록이 준 `has_photo`
 * @param serverUpdatedAt 목록이 준 `photo_updated_at`. 서버 사진이 없으면 null
 * @param localExists 기기에 파일이 있나
 * @param localStamp 그 파일이 **서버의 어느 판인가**. null 이면 서버에서 온 것이 아니다
 *   (사용자가 방금 고른 사진이거나, 서버가 생기기 전부터 이 폰에 있던 사진)
 *
 * ## 판단이 갈리는 자리
 *
 * **도장이 없는 기기 사진은 언제나 올린다.** 서버에 이미 뭔가 있어도 그렇다 —
 * 도장이 없다는 건 사용자가 이 폰에서 방금 고른 사진이라는 뜻이고, 그게 제일 새 뜻이다.
 * 여기서 서버 것을 내려받으면 **사용자가 방금 고른 사진이 눈앞에서 되돌아간다.**
 *
 * **서버에 없는데 도장이 있으면 지운다.** 올렸던 사진이 서버에서 없어졌다는 뜻이고,
 * 그건 사용자가 **다른 기기에서 지운 것**이다. 여기서 다시 올리면 지운 것이 되살아난다.
 */
fun photoActionFor(
    serverHasPhoto: Boolean,
    serverUpdatedAt: String?,
    localExists: Boolean,
    localStamp: String?,
    /** 공동 돌봄 아이는 서버 사진을 읽을 수 있지만 바꿀 수는 없다. */
    canUpload: Boolean = true,
): PhotoAction = when {
    // 대표가 아니면 서버 상태를 기기에 그대로 비춘다. 예전에 대표였을 때 남은 도장 없는
    // 사진을 올리면 승계 뒤에도 남의 강아지 사진을 바꾸게 된다.
    !canUpload && !serverHasPhoto -> if (localExists) PhotoAction.DELETE_LOCAL else PhotoAction.NOTHING
    !canUpload && (!localExists || localStamp != serverUpdatedAt) -> PhotoAction.DOWNLOAD

    // 기기에 아무것도 없다 — 서버에 있으면 받아 온다 (새 폰이 여기다).
    !localExists -> if (serverHasPhoto) PhotoAction.DOWNLOAD else PhotoAction.NOTHING

    // 아직 서버 것이 아닌 사진. 사용자가 방금 고른 것이거나 서버가 생기기 전 사진이다.
    // **서버에 뭐가 있든 이쪽이 이긴다.**
    localStamp == null -> PhotoAction.UPLOAD

    // 도장은 있는데 서버에 없다 = 다른 기기에서 지웠다.
    !serverHasPhoto -> PhotoAction.DELETE_LOCAL

    // 둘 다 서버 판인데 다르다 = 다른 기기에서 바꿨다.
    localStamp != serverUpdatedAt -> PhotoAction.DOWNLOAD

    else -> PhotoAction.NOTHING
}
