package com.daengs.app.map.layers.territory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.daengs.app.R

/** 전체 벡터에 단색 tint를 씌우면 전봇대 선까지 원에 묻힌다. 점유색은 테두리에만 준다. */
internal fun territoryMarkerIcon(context: Context, occupancy: TerritoryMarkerOccupancy): Bitmap {
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_territory_site)).mutate().apply {
        setBounds(0, 0, 96, 96)
        draw(canvas)
    }
    val accent = when (occupancy) {
        TerritoryMarkerOccupancy.NEUTRAL -> Color.rgb(115, 125, 135)
        TerritoryMarkerOccupancy.UNVERIFIED -> Color.rgb(227, 145, 45)
        TerritoryMarkerOccupancy.VERIFIED -> Color.rgb(60, 150, 115)
    }
    canvas.drawCircle(48f, 48f, 43f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 5f
    })
    return bitmap
}
