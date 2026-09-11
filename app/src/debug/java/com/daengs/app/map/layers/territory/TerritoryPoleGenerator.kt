package com.daengs.app.map.layers.territory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.daengs.app.R

/** 원본은 생성기 전용. 완성 이미지에 다시 축소/발광을 적용하지 않는다. */
internal fun territoryPoleSource(occupancy: TerritoryMarkerOccupancy): Int =
    if (occupancy == TerritoryMarkerOccupancy.NEUTRAL) R.drawable.map_territory_pole_neutral
    else R.drawable.map_territory_pole_occupied

/** 본체 색과 실루엣 발광, 인증 체크. 실제 범위 원/성공 애니메이션과는 별개다. */
internal fun renderTerritoryPole(context: Context, occupancy: TerritoryMarkerOccupancy, isMine: Boolean = false): Bitmap {
    val style = TerritoryPoleStyle.of(occupancy, isMine)
    val source = checkNotNull(BitmapFactory.decodeResource(context.resources, territoryPoleSource(occupancy)))
    val result = Bitmap.createBitmap(TerritoryPoleArt.WIDTH, TerritoryPoleArt.HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // 축소 후에도 밑동 밖으로 보이는 짧은 고깔. 두 상태에 같은 방향·30% 알파.
    paint.color = Color.argb(76, 80, 65, 51)
    canvas.drawPath(Path().apply {
        moveTo(105f, 614f)
        lineTo(233f, 556f)
        quadTo(254f, 551f, 246f, 567f)
        lineTo(150f, 626f)
        quadTo(128f, 627f, 105f, 614f)
        close()
    }, paint)
    paint.color = Color.WHITE
    val scale = .65f
    val left = 128f * (1f - scale)
    val top = 624f * (1f - scale)
    val body = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
    Canvas(body).drawBitmap(source, null, RectF(left, top,
        left + source.width * scale, top + source.height * scale), paint)
    drawPoleBody(canvas, body, style)
    if (occupancy == TerritoryMarkerOccupancy.VERIFIED) drawVerifiedBadge(canvas, style.tint)
    source.recycle()
    body.recycle()
    return result
}

private fun drawPoleBody(canvas: Canvas, body: Bitmap, style: TerritoryPoleStyle) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (style == TerritoryPoleStyle.NEUTRAL) {
        canvas.drawBitmap(body, 0f, 0f, paint)
        return
    }
    val tint = style.tint
    // A bright narrow core inside a wider colored bloom reads as light, not paint.
    val halo = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
    val haloCanvas = Canvas(halo)
    fun bloom(radius: Float, color: Int, alpha: Int) {
        val offset = IntArray(2)
        val mask = body.extractAlpha(Paint().apply {
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }, offset)
        paint.color = color
        paint.alpha = alpha
        haloCanvas.drawBitmap(mask, offset[0].toFloat(), offset[1].toFloat(), paint)
        mask.recycle()
    }
    bloom(26f, tint, 235)
    bloom(10f, tint, 255)
    bloom(3f, Color.rgb((1020 + Color.red(tint)) / 5, (1020 + Color.green(tint)) / 5,
        (1020 + Color.blue(tint)) / 5), 255)
    // Fade the bloom into the foot instead of clipping it at the bitmap's bottom edge.
    haloCanvas.drawRect(0f, 0f, body.width.toFloat(), body.height.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 602f, 0f, 638f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    })
    paint.color = Color.WHITE
    paint.alpha = 255
    canvas.drawBitmap(halo, 0f, 0f, paint)
    halo.recycle()
    canvas.drawBitmap(body, 0f, 0f, paint)
    // Only a hint of reflected color reaches the surface; retain the original material.
    val light = Color.rgb(Color.red(tint) / 8, Color.green(tint) / 8, Color.blue(tint) / 8)
    paint.colorFilter = LightingColorFilter(tint, light)
    paint.alpha = 30
    canvas.drawBitmap(body, 0f, 0f, paint)
}

private fun drawVerifiedBadge(canvas: Canvas, tint: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Match ownership color; a dark face keeps the white check readable.
    paint.color = Color.WHITE
    canvas.drawCircle(198f, 282f, 40f, paint)
    paint.color = Color.rgb(Color.red(tint) / 2, Color.green(tint) / 2, Color.blue(tint) / 2)
    canvas.drawCircle(198f, 282f, 33f, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 12f
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(Path().apply {
        moveTo(181f, 282f)
        lineTo(193f, 294f)
        lineTo(215f, 270f)
    }, paint)
}
