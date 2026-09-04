package com.daengs.app.dogcard

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 뽑은 카드를 **사진첩에 바로** 넣는다.
 *
 * 예전에는 SAF 로 저장할 곳을 고르게 했다. 창이 뜨고, 폴더를 고르고, 이름을 확인해야
 * 하는데 그러고도 **사진첩에는 안 보인다.** 카드를 자랑하려는 사람에게 시킬 일이 아니다.
 *
 * **권한을 새로 안 받는다.** `MediaStore` 에 우리가 만든 그림을 넣는 데에 권한이 필요
 * 없어진 것이 안드로이드 10(API 29)부터다. 그 아래에서는 `WRITE_EXTERNAL_STORAGE` 가
 * 있어야 하는데, 지금 이 앱에는 저장 권한이 하나도 없고 **하나 넣으면 스토어 등록
 * 정보의 권한 목록이 바뀐다.** 2018년 이전 폰 때문에 그걸 늘리지 않는다 —
 * API 28 이하는 [usesMediaStore] 가 false 라 예전의 고르는 창으로 간다.
 */

/** 사진첩에 잡힐 앨범 이름. `Pictures/댕스` 다. */
const val GALLERY_ALBUM = "댕스"

/** 카드가 갤러리로 바로 갈 수 있는 기기인가. 아니면 저장할 곳을 묻는다. */
fun usesMediaStore(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.Q

/**
 * 사진첩 한 칸의 정보.
 *
 * **[MediaStore.MediaColumns.IS_PENDING] 를 1 로 두고 시작한다.** 안 그러면 아직 다
 * 안 쓴 그림이 갤러리에 잠깐 뜬다 — 반쯤 그려진 카드가 보이는 것보다 늦게 뜨는 편이 낫다.
 *
 * `RELATIVE_PATH` · `IS_PENDING` 은 API 29 에 생긴 이름이지만 **컴파일 때 박히는
 * 문자열 상수**라 아래 기기에서 부르는 것 자체는 문제가 없다. 다만 그 기기에서는
 * [usesMediaStore] 가 막아서 여기까지 오지 않는다.
 */
fun galleryValues(displayName: String, nowMillis: Long): ContentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
    put(MediaStore.MediaColumns.MIME_TYPE, PNG_MIME)
    // 초 단위다. 밀리초를 넣으면 갤러리가 카드를 2554년으로 보낸다.
    put(MediaStore.MediaColumns.DATE_ADDED, nowMillis / 1000)
    put(MediaStore.MediaColumns.DATE_MODIFIED, nowMillis / 1000)
    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM")
    put(MediaStore.MediaColumns.IS_PENDING, 1)
}

/** 카드 그림 한 장은 PNG 다. 포일과 글자가 있어 JPEG 로 굽으면 테두리가 번진다. */
const val PNG_MIME = "image/png"

/**
 * 그림 한 장을 사진첩에 넣는다.
 *
 * @return 들어간 자리. 못 넣으면 null — 화면은 "저장하지 못했어요" 로 떨어진다
 */
fun saveToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String,
    nowMillis: Long = System.currentTimeMillis(),
): Uri? {
    val resolver = context.contentResolver
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, galleryValues(displayName, nowMillis))
    }.getOrNull() ?: return null

    val wrote = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }.getOrDefault(false)

    if (!wrote) {
        // **빈 칸을 남기지 않는다.** 쓰다 만 자리는 갤러리에서 깨진 그림으로 남는다.
        runCatching { resolver.delete(uri, null, null) }
        return null
    }
    runCatching {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    return uri
}
