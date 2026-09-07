package com.daengs.app.map.provider.naver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * 지도의 "내 위치"를 **대표 강아지 얼굴**로 만든다.
 *
 * 기본값은 파란 점이다. 그건 어느 앱에서나 같은 파란 점이라, 내 강아지의 방에서
 * 출발한 화면인데도 여기만 남의 앱처럼 보인다.
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
): Bitmap? = BitmapFactory.decodeResource(context.resources, portraitRes)
    ?.let { circularAvatarBitmap(it, sizePx, ringPx) }

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
