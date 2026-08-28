package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/*
 * 방 위에 얹히는 것들.
 *
 * Canvas 가 아니라 일반 Composable 로 만든다:
 *  - 셋 다 글자가 들어간다. Canvas 에 그리려면 TextMeasurer 를 써야 하고
 *    줄바꿈·글꼴 크기 설정 대응이 나빠진다.
 *  - 카메라 버튼은 눌러야 하므로 터치 영역과 접근성 정보가 필요하다.
 *  - 깊이 정렬(col+row)에 낄 이유가 없다. 항상 맨 앞이다.
 */

@Composable
fun TodayCard(
    dateLabel: String,
    note: String,
    accent: Color = DaengPink,
    accentSoft: Color = PinkSoft,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // 시안의 걸린 못 두 개
        Row(
            Modifier.padding(horizontal = 14.dp).width(112.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(accentSoft, RoundedCornerShape(50)),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardWhite.copy(alpha = 0.92f),
            modifier = Modifier.shadow(6.dp, RoundedCornerShape(16.dp), clip = false),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TODAY",
                        color = DaengPinkDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.width(20.dp))
                    DaengsIconView(DaengsIcon.Sun, Modifier.size(17.dp), tint = accent)
                }
                Spacer(Modifier.height(3.dp))
                Text(dateLabel, color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(note, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = CardWhite.copy(alpha = 0.85f),
        modifier = modifier.size(46.dp).shadow(5.dp, RoundedCornerShape(50), clip = false),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DaengsIconView(DaengsIcon.Camera, Modifier.size(23.dp), tint = DaengPinkDeep)
        }
    }
}

/** 방 앞쪽에 걸린 "○○이네" 이름표. */
@Composable
fun NamePlate(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(2.dp, PinkSoft),
        modifier = modifier.shadow(4.dp, RoundedCornerShape(11.dp), clip = false),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            DaengsIconView(DaengsIcon.Heart, Modifier.size(13.dp), tint = DaengPink)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3D8D2)
@Composable
private fun TodayCardPreview() {
    DaengsTheme {
        TodayCard(HomeDemoData.MOCK_DATE, HomeDemoData.TODAY_NOTE, modifier = Modifier.padding(12.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3D8D2, fontScale = 1.5f)
@Composable
private fun NamePlateLargeFontPreview() {
    DaengsTheme {
        Column(Modifier.padding(12.dp)) {
            NamePlate(HomeDemoData.ROOM_LABEL)
            Spacer(Modifier.height(10.dp))
            CameraButton(onClick = {})
        }
    }
}
