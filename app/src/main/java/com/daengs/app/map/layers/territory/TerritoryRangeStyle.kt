package com.daengs.app.map.layers.territory

import com.daengs.app.territory.TerritoryProximityRange

/** Range labels describe location only; green is not permission to claim or photograph. */
data class TerritoryRangeStyle(val outlineArgb: Int, val label: String)

fun territoryRangeStyle(proximity: TerritoryProximityRange): TerritoryRangeStyle = when (proximity) {
    TerritoryProximityRange.UNAVAILABLE -> TerritoryRangeStyle(0xffa79089.toInt(), "위치 확인 중")
    TerritoryProximityRange.APPROACHING -> TerritoryRangeStyle(0xffd9787d.toInt(), "영역표시 범위")
    TerritoryProximityRange.IN_RANGE -> TerritoryRangeStyle(0xff4e9b70.toInt(), "범위 안")
}
