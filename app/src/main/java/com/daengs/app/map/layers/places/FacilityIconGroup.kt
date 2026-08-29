package com.daengs.app.map.layers.places

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.R

/**
 * Marker icon buckets. The server decides which bucket a facility falls into
 * (`icon_group` in the search response) because the source `kind` list grows with
 * every new dataset; the app only maps a known bucket to a drawable.
 *
 * An unknown wire value is [ETC], never a dropped marker — a facility we cannot
 * classify still exists on the map.
 */
enum class FacilityIconGroup(val wire: String, @DrawableRes val marker: Int) {
    MEDICAL("medical", R.drawable.ic_facility_medical),
    SUPPLY("supply", R.drawable.ic_facility_supply),
    FOOD("food", R.drawable.ic_facility_food),
    STAY("stay", R.drawable.ic_facility_stay),
    CULTURE("culture", R.drawable.ic_facility_culture),
    OUTDOOR("outdoor", R.drawable.ic_facility_outdoor),
    CARE("care", R.drawable.ic_facility_care),
    ETC("etc", R.drawable.ic_facility_etc),
    ;

    companion object {
        private val BY_WIRE = entries.associateBy { it.wire }

        fun fromWire(value: String?): FacilityIconGroup = BY_WIRE[value] ?: ETC
    }
}


// 마커 8종을 한눈에. 지도 표면(NaverMapSurface)은 SDK 런타임이 필요해 프리뷰가
// 안 되므로, 프리뷰 가능한 조각은 아이콘 쪽이다.
@Preview(showBackground = true)
@Composable
private fun FacilityIconGroupPreview() {
    Row {
        FacilityIconGroup.entries.forEach { group ->
            Image(
                painter = painterResource(group.marker),
                contentDescription = group.wire,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
