package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.daengs.app.R

/**
 * 「내 위치」에 넘길 얼굴 리소스. **얼굴을 모르면 발바닥이다 — 절대 null 이 아니다.**
 *
 * 🔒 잠긴 디자인 — `docs/design-locks.md` 1절. 지도의 내 위치는 사용자 프로필이다.
 *
 * `MapHost` 는 `avatarRes` 와 `avatarPhoto` 가 **둘 다 null 이면 SDK 파란 점**을 쓴다
 * (아래 [locationAvatarBitmap]). 그런데 강아지 정보가 없는 때가 흔하다 — 불러오는 중,
 * 로그인 전, 강아지를 아직 안 데려옴, 통신 실패. 견종 얼굴만 넘기면 그때마다 파란
 * 점으로 떨어진다. 2026-09-12 실기기에서 인자를 다 넘기고도 파란 점이 뜬 이유가 이것이다.
 */
@DrawableRes
fun locationFaceRes(@DrawableRes portraitRes: Int?): Int = portraitRes ?: R.drawable.ic_location_paw

/** Null means this screen requested the SDK dot; failed portraits use a local paw instead. */
internal fun locationAvatarBitmap(
    context: Context,
    photo: Bitmap?,
    @DrawableRes portraitRes: Int?,
    sizePx: Int,
    ringPx: Float,
): Bitmap? {
    if (photo == null && portraitRes == null) return null
    val fromPhoto = photo?.takeUnless { it.isRecycled }?.let {
        try {
            circularAvatarBitmap(it, sizePx, ringPx)
        } catch (error: IllegalArgumentException) {
            android.util.Log.w("LocationAvatar", "Cannot render profile photo", error)
            null
        }
    }
    return fromPhoto
        ?: portraitRes?.let { circularAvatarBitmap(context, it, sizePx, ringPx) }
        ?: circularAvatarBitmap(context, R.drawable.ic_location_paw, sizePx, ringPx)
}

/**
 * 지도의 "내 위치"를 **대표 강아지 얼굴**로 만든다.
 *
 * 산책에서 사진과 견종을 알 수 없으면 로컬 발바닥을 쓴다. 얼굴을 요청하지 않는
 * 다른 지도는 SDK 기본 점을 유지한다.
 *
 * 얼굴 그림([com.daengs.app.miniroom.art.DogBreed.portraitRes])은 **크림 배경이 깔린
 * 정사각 이미지**다(투명 픽셀 0%). 그대로 얹으면 지도 위에 크림색 네모가 놓인다.
 * 그래서 원으로 오리고 흰 테두리를 두른다 — 앱의 프로필 아바타가 하는 것과 같다.
 *
 * 흰 테두리가 장식이 아니다. 지도 색이 연해서, 테두리가 없으면 밝은 얼굴이 바탕에
 * 녹아 어디 있는지 안 보인다.
 */
fun circularAvatarBitmap(
    context: Context,
    @DrawableRes portraitRes: Int,
    sizePx: Int,
    ringPx: Float,
): Bitmap? = try {
    // Drawable also supports the vector fallback; BitmapFactory only decodes raster assets.
    context.getDrawable(portraitRes)?.let { drawable ->
        val source = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(source))
        circularAvatarBitmap(source, sizePx, ringPx)
    }
} catch (error: android.content.res.Resources.NotFoundException) {
    android.util.Log.w("LocationAvatar", "Cannot load portrait resource $portraitRes", error)
    null
}

/**
 * 그림 하나를 그대로 받아 동그란 마커로.
 *
 * **사용자가 올린 프로필 사진은 리소스가 아니라 파일이다** (`pet/PetPhotos.kt`).
 * 견종 그림만 받던 자리를 갈라 둬서, 사진이 있으면 지도의 내 위치도 그 얼굴이 된다.
 */
fun circularAvatarBitmap(
    source: Bitmap,
    sizePx: Int,
    ringPx: Float,
): Bitmap {
    val out = createBitmap(sizePx, sizePx)
    val canvas = Canvas(out)
    val radius = sizePx / 2f

    val ring = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(radius, radius, radius, ring)

    val inner = (radius - ringPx).coerceAtLeast(1f)
    val diameter = (inner * 2f).toInt().coerceAtLeast(1)
    val scaled = source.scale(diameter, diameter)
    val face = Paint().apply {
        isAntiAlias = true
        shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            // 셰이더는 비트맵의 (0,0) 부터 칠한다. 원의 왼쪽 위로 옮겨야 얼굴이 가운데 온다.
            setLocalMatrix(
                android.graphics.Matrix().apply { setTranslate(radius - inner, radius - inner) },
            )
        }
    }
    canvas.drawCircle(radius, radius, inner, face)
    return out
}
