package com.daengs.app.ui.walk.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.diary.DiarySceneAddress
import com.daengs.app.walk.diary.DiarySceneContent
import com.daengs.app.walk.diary.addressText
import java.math.BigDecimal
import java.math.RoundingMode

internal fun sceneConditionsLabel(content: DiarySceneContent?): String = listOfNotNull(
    content?.administrativeAddress?.cardLabel()?.takeIf { it.isNotBlank() }
        ?: addressText(content?.address),
    content?.temperatureC?.takeIf { it.isFinite() && it in -90.0..60.0 }?.let {
        BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "°C"
    },
).joinToString(" · ")

@Composable
internal fun DiarySceneConditions(content: DiarySceneContent?) {
    val label = sceneConditionsLabel(content)
    if (label.isNotBlank()) Text(label, Modifier.padding(top = 6.dp),
        fontSize = 12.sp, lineHeight = 18.sp, color = TextMuted)
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.3f)
@Composable
private fun DiarySceneConditionsPreview() { DiaryReviewTheme {
    Column(Modifier.padding(20.dp)) {
        DiarySceneConditions(DiarySceneContent("", "", locationLabel = "",
            administrativeAddress = DiarySceneAddress("서울특별시", "중랑구", "상봉동", "administrative_dong"),
            temperatureC = 20.0))
        DiarySceneConditions(DiarySceneContent("", "", locationLabel = "",
            administrativeAddress = DiarySceneAddress("경기도", "수원시 영통구", "매탄3동", "administrative_dong"),
            temperatureC = -2.3))
    }
} }
