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
 * 마커 아이콘 묶음.
 *
 * **어느 묶음인지는 서버가 정한다** (검색 응답의 `icon_group`). 원본 `kind` 목록은
 * 데이터셋이 늘 때마다 같이 늘어나므로, 앱은 아는 묶음을 그림에 잇는 일만 한다.
 *
 * 모르는 값이 오면 [ETC] 다 — **마커를 버리지 않는다.** 분류를 못 했을 뿐
 * 그 시설은 지도 위에 실제로 있다.
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
