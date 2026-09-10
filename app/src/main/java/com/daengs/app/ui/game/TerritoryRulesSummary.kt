package com.daengs.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.*

/** The entry explains only the actions and immediate rewards; conditions live in the details. */
@Composable
internal fun TerritoryRulesSummary() {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SummaryStep("1", "가까이 가기", "지도에 표시된 점령 위치로 이동해요.")
            SummaryStep("2", "영역표시", "빈 곳의 20m 안에서 영역표시를 눌러요.")
            SummaryStep("3", "강아지 사진 인증", "10m 안에서 강아지를 새로 찍어요.")
        }
        Surface(shape = RoundedCornerShape(18.dp), color = CardWhite) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("점수 한눈에", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                SummaryScore("영역표시", "20점까지")
                SummaryScore("사진 인증", "누적 100점까지")
                SummaryScore("다른 회원 영역 인증 탈취", "+20점")
                Text("20점 받은 뒤 인증하면 +80점\n기본 한도: 회원·장소·시즌당 100점",
                    color = TextDark.copy(alpha = .76f), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SummaryStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).background(PinkSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = TextDark, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SummaryScore(label: String, score: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = TextDark, fontSize = 12.sp, lineHeight = 18.sp)
        Text(score, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun TerritoryRulesSummaryPreview() = DaengsTheme { TerritoryRulesSummary() }

@Preview(showBackground = true)
@Composable
private fun SummaryStepPreview() = DaengsTheme { SummaryStep("1", "가까이 가기", "지도에 표시된 점령 위치로 이동해요.") }

@Preview(showBackground = true, widthDp = 300)
@Composable
private fun SummaryScorePreview() = DaengsTheme { SummaryScore("다른 회원 영역 인증 탈취", "+20점") }
