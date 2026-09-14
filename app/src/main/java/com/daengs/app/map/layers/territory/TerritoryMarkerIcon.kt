package com.daengs.app.map.layers.territory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.DrawableRes
import com.daengs.app.R
import kotlin.math.roundToInt

internal enum class TerritoryPoleStyle(val occupancy: TerritoryMarkerOccupancy, val isMine: Boolean, val tint: Int) {
    NEUTRAL(TerritoryMarkerOccupancy.NEUTRAL, false, Color.TRANSPARENT),
    MINE_UNVERIFIED(TerritoryMarkerOccupancy.UNVERIFIED, true, 0xFF329AFF.toInt()),
    MINE_VERIFIED(TerritoryMarkerOccupancy.VERIFIED, true, 0xFF1EF66F.toInt()),
    OTHER_UNVERIFIED(TerritoryMarkerOccupancy.UNVERIFIED, false, 0xFFFFA600.toInt()),
    OTHER_VERIFIED(TerritoryMarkerOccupancy.VERIFIED, false, 0xFFFF3D51.toInt());

    companion object {
        fun of(occupancy: TerritoryMarkerOccupancy, isMine: Boolean) = when (occupancy) {
            TerritoryMarkerOccupancy.NEUTRAL -> NEUTRAL
            TerritoryMarkerOccupancy.UNVERIFIED -> if (isMine) MINE_UNVERIFIED else OTHER_UNVERIFIED
            TerritoryMarkerOccupancy.VERIFIED -> if (isMine) MINE_VERIFIED else OTHER_VERIFIED
        }
    }
}

/** tools/map_sprite.py와 같은 캔버스/밑면 접점. 지도와 Preview에서 함께 쓴다. */
internal object TerritoryPoleArt {
    const val WIDTH = 256
    const val HEIGHT = 640
    const val ANCHOR_X = .5f
    const val ANCHOR_Y = 624f / HEIGHT

    // Selection is expressed by the range and card, never by enlarging the object.
    fun size(scale: Float = 1f): Pair<Int, Int> {
        val width = (48 * scale).roundToInt()
        return width to (width * HEIGHT.toFloat() / WIDTH).roundToInt()
    }

    @DrawableRes
    fun resource(occupancy: TerritoryMarkerOccupancy, isMine: Boolean = false): Int =
        when (TerritoryPoleStyle.of(occupancy, isMine)) {
            TerritoryPoleStyle.NEUTRAL -> R.drawable.map_territory_pole_neutral_ready
            TerritoryPoleStyle.MINE_UNVERIFIED -> R.drawable.map_territory_pole_mine_unverified_ready
            TerritoryPoleStyle.MINE_VERIFIED -> R.drawable.map_territory_pole_mine_verified_ready
            TerritoryPoleStyle.OTHER_UNVERIFIED -> R.drawable.map_territory_pole_other_unverified_ready
            TerritoryPoleStyle.OTHER_VERIFIED -> R.drawable.map_territory_pole_other_verified_ready
        }
}

/** 형광/그림자/체크까지 완성된 nodpi 그림. 생성기는 debug 소스에만 있다. */
internal fun territoryMarkerIcon(context: Context, occupancy: TerritoryMarkerOccupancy, isMine: Boolean = false): Bitmap =
    checkNotNull(BitmapFactory.decodeResource(context.resources, TerritoryPoleArt.resource(occupancy, isMine)))
