package com.daengs.app.map.layers.territory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import com.daengs.app.R
import kotlin.math.roundToInt

/** tools/map_sprite.py와 같은 캔버스/밑면 접점. 지도와 Preview에서 함께 쓴다. */
internal object TerritoryPoleArt {
    const val WIDTH = 256
    const val HEIGHT = 640
    const val ANCHOR_X = .5f
    const val ANCHOR_Y = 624f / HEIGHT

    fun size(selected: Boolean, scale: Float = 1f): Pair<Int, Int> {
        val width = ((if (selected) 60 else 48) * scale).roundToInt()
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
internal fun territoryMarkerIcon(context: Context, occupancy: TerritoryMarkerOccupancy): Bitmap =
    checkNotNull(BitmapFactory.decodeResource(context.resources, TerritoryPoleArt.resource(occupancy)))
