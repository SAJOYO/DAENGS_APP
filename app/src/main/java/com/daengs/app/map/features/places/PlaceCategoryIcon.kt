package com.daengs.app.map.features.places

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.daengs.app.place.PlaceKind

/** 기존 DaengsIcons와 같은 24 단위 Canvas 아이콘. 텍스트 라벨이 의미를 소유한다. */
@Composable
internal fun PlaceCategoryIcon(kind: PlaceKind?, modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x, y), Offset(x2, y2), 1.6f, StrokeCap.Round)
            when (kind) {
                null -> listOf(5f, 15f).forEach { x -> listOf(5f, 15f).forEach { y ->
                    drawRoundRect(tint, Offset(x, y), Size(5f, 5f), CornerRadius(1.3f), style = stroke)
                } }
                PlaceKind.CAFE -> {
                    drawRoundRect(tint, Offset(4f, 8f), Size(12f, 12f), CornerRadius(3f), style = stroke)
                    drawArc(tint, -90f, 180f, false, Offset(13f, 9f), Size(8f, 7f), style = stroke)
                    line(7f, 3f, 7f, 5f); line(11f, 2f, 11f, 5f)
                }
                PlaceKind.RESTAURANT -> {
                    line(5f, 3f, 5f, 9f); line(8f, 3f, 8f, 21f); line(11f, 3f, 11f, 9f)
                    line(5f, 9f, 11f, 9f); line(18f, 3f, 18f, 21f)
                    line(16f, 3f, 16f, 12f); line(16f, 12f, 18f, 12f)
                }
                PlaceKind.GROOMING -> {
                    drawCircle(tint, 3f, Offset(6f, 17f), style = stroke)
                    drawCircle(tint, 3f, Offset(18f, 17f), style = stroke)
                    line(8f, 15f, 18f, 3f); line(16f, 15f, 6f, 3f)
                }
                PlaceKind.HOSPITAL, PlaceKind.PHARMACY -> {
                    drawRoundRect(tint, Offset(3f, 5f), Size(18f, 16f), CornerRadius(3f), style = stroke)
                    line(9f, 2f, 15f, 2f); line(9f, 2f, 9f, 5f); line(15f, 2f, 15f, 5f)
                    line(8f, 13f, 16f, 13f); line(12f, 9f, 12f, 17f)
                }
                PlaceKind.PET_SHOP, PlaceKind.SHOPPING -> {
                    drawRoundRect(tint, Offset(4f, 7f), Size(16f, 14f), CornerRadius(3f), style = stroke)
                    drawArc(tint, 180f, 180f, false, Offset(8f, 2f), Size(8f, 10f), style = stroke)
                    if (kind == PlaceKind.PET_SHOP) {
                        drawCircle(tint, 2f, Offset(12f, 16f))
                        listOf(9f, 12f, 15f).forEach { drawCircle(tint, .8f, Offset(it, 12f)) }
                    }
                }
                PlaceKind.BOARDING, PlaceKind.PENSION, PlaceKind.HOTEL, PlaceKind.STAY -> {
                    val house = Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                        lineTo(21f, 21f); lineTo(3f, 21f); close() }
                    drawPath(house, tint, style = stroke)
                    drawRoundRect(tint, Offset(9f, 12f), Size(6f, 9f), CornerRadius(1f), style = stroke)
                }
                PlaceKind.TRAVEL, PlaceKind.LEISURE -> {
                    val tree = Path().apply { moveTo(12f, 2f); lineTo(4f, 16f); lineTo(20f, 16f); close() }
                    drawPath(tree, tint, style = stroke); line(12f, 16f, 12f, 22f)
                }
                PlaceKind.MUSEUM, PlaceKind.GALLERY, PlaceKind.ARTS_CENTER, PlaceKind.CULTURE -> {
                    val roof = Path().apply { moveTo(3f, 8f); lineTo(12f, 3f); lineTo(21f, 8f); close() }
                    drawPath(roof, tint, style = stroke)
                    listOf(6f, 12f, 18f).forEach { line(it, 11f, it, 18f) }; line(3f, 21f, 21f, 21f)
                }
                PlaceKind.ETC -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.6f, Offset(it, 12f)) }
            }
        }
    }
}
