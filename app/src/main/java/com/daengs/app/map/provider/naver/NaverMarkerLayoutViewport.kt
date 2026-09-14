package com.daengs.app.map.provider.naver

import androidx.compose.ui.unit.IntSize
import com.daengs.app.map.layout.MarkerRect
import com.daengs.app.map.shell.MapVisibilityQuery

internal fun markerViewport(size: IntSize, density: Float, top: Int, bottom: Int) =
    MarkerRect(4.0, top/density+4.0, size.width/density-4.0, (size.height-bottom)/density-4.0)

internal fun markerExclusions(size: IntSize, density: Float, query: MapVisibilityQuery?): List<MarkerRect> = buildList {
    if (query != null) {
        if (query.topLeftCoverWidthPx > 0 && query.topLeftCoverHeightPx > 0)
            add(MarkerRect(0.0,0.0,query.topLeftCoverWidthPx/density.toDouble(),query.topLeftCoverHeightPx/density.toDouble()))
        if (query.topRightCoverPx > 0) add(MarkerRect((size.width-query.topRightCoverPx)/density.toDouble(),0.0,
            size.width/density.toDouble(),query.topRightCoverPx/density.toDouble()))
    }
}
