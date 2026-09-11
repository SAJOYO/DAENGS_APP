package com.daengs.app.map.layers.territory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.DrawableRes
import com.daengs.app.R
import kotlin.math.roundToInt

/** tools/map_sprite.py와 같은 캔버스/밑면 접점. 지도와 Preview에서 함께 쓴다. */
internal object TerritoryPoleArt {
    const val WIDTH = 256
    const val HEIGHT = 640
    const val ANCHOR_X = .5f
    const val ANCHOR_Y = 624f / HEIGHT
    // 그림만 축소한다. SDK 선택 범위와 확대 시 밑동 기준점은 유지한다.
    const val ART_SCALE = .65f

    // Selection is expressed by the range and card, never by enlarging the object.
    fun size(scale: Float = 1f): Pair<Int, Int> {
        val width = (48 * scale).roundToInt()
        return width to (width * HEIGHT.toFloat() / WIDTH).roundToInt()
    }

    @DrawableRes
    fun resource(occupancy: TerritoryMarkerOccupancy): Int = when (occupancy) {
        TerritoryMarkerOccupancy.NEUTRAL -> R.drawable.map_territory_pole_neutral
        TerritoryMarkerOccupancy.UNVERIFIED, TerritoryMarkerOccupancy.VERIFIED ->
            R.drawable.map_territory_pole_occupied
    }
}

/** 점유 여부만 그림으로 표시한다. 인증 여부는 기존 지도 caption/카드가 담당한다. */
internal fun territoryMarkerIcon(context: Context, occupancy: TerritoryMarkerOccupancy): Bitmap {
    val source = checkNotNull(BitmapFactory.decodeResource(context.resources, TerritoryPoleArt.resource(occupancy)))
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
    val scale = TerritoryPoleArt.ART_SCALE
    val left = 128f * (1f - scale)
    val top = 624f * (1f - scale)
    canvas.drawBitmap(source, null, RectF(left, top,
        left + source.width * scale, top + source.height * scale), paint)
    source.recycle()
    return result
}
