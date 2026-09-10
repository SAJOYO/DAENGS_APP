package com.daengs.app.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.*
import com.daengs.app.ui.theme.*

/** First-season product policy; membership caps and pet score ownership are distinct. */
internal data class GameRule(val id: String, val title: String, val summary: String, val detail: String)
internal val firstSeasonGameRules = listOf(
    GameRule("mark", "가까이 가서 점령해요", "미인증 20점 · 인증하면 누적 100점까지",
        "산책 중 빈 전봇대의 20m 안에서 영역표시하면 기본 점수를 누적 20점까지 받아요. GPS 오차도 거리에 포함돼요.\n\n강아지와 전봇대 주변이 함께 나온 새 사진을 10m 안에서 촬영해 인증하면 누적 100점까지 남은 점수를 받아요. 20점을 받았다면 인증으로 80점을 더 받아요.\n\n한 장소의 기본 점수 한도는 회원·시즌별 100점이에요. 강아지를 바꿔도 한도는 같고, 받은 점수는 행동한 강아지에게 쌓여요."),
    GameRule("takeover", "사진으로 다른 영역에 도전해요", "다른 회원의 영역 인증 탈취 · 매번 +20점",
        "다른 회원이 가진 영역은 새 사진 인증으로 탈취해요. 이전 주인의 인증 여부와 관계없이 성공할 때마다 20점 보너스를 받아요.\n\n그 장소에서 기본 점수를 한 번도 받지 않았다면 기본 100점 + 보너스 20점으로 총 120점을 받아요. 기본 점수를 이미 20점 받았다면 100점, 100점을 모두 받았다면 보너스 20점을 받아요.\n\n인증 직후에는 10분 동안 보호돼요. 같은 회원의 다른 강아지에게 넘기는 행동에는 탈취 보너스가 없어요."),
    GameRule("hold", "영역을 지키며 점수를 쌓아요", "미인증 시간당 2점 · 인증 시간당 10점",
        "보유 중인 영역마다 미인증은 시간당 2점, 인증은 시간당 10점이 쌓여요. 미인증으로 1시간, 인증으로 1시간 보유하면 12점이에요.\n\n동시 보유 계수나 4시간 상한은 없어요. 점수는 실제 보유 구간을 서버가 정산한 뒤 반영돼요."),
    GameRule("renew", "다시 방문해 유지 시간을 늘려요", "최대 72시간 유지 · 연장 보상 0점",
        "점령한 영역은 최대 72시간 유지돼요. 내 미인증 영역은 현장에서 GPS로, 내 인증 영역은 새 사진 인증으로 유지 시간을 연장해요.\n\n연장 보상은 0점이고, 인증 보호 10분을 다시 시작하지 않아요. 유지 기한이나 시즌이 끝나면 영역은 비워져요."),
    GameRule("season", "매달 새로운 시즌이 시작돼요", "한국 시간 매월 1일 · 지난 성적은 결산",
        "시즌은 한국 시간으로 매월 1일 00:00에 바뀌어요. 첫 시즌은 시작한 날부터 그달 말까지 진행해요.\n\n시즌 종료 시 성적을 결산하고 다음 시즌이 자동으로 시작돼요. 새 시즌에는 점령지와 기본 점수 기회가 새로 열려요."),
)

@Composable
internal fun TerritoryGameRules() {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("첫 시즌 점령 방법", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
        firstSeasonGameRules.forEach { rule ->
            val open = expanded == rule.id
            Surface(shape = RoundedCornerShape(16.dp), color = CardWhite) {
                Column {
                    Row(Modifier.fillMaxWidth().testTag("game-rule-${rule.id}")
                        .semantics { stateDescription = if (open) "펼침" else "접힘" }
                        .clickable(role = Role.Button, onClickLabel = if (open) "설명 접기" else "설명 펼치기") {
                            expanded = if (open) null else rule.id
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(rule.title, color = TextDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(rule.summary, color = TextDark.copy(alpha = 0.76f), fontSize = 12.sp)
                        }
                        DaengsIconView(DaengsIcon.CaretDown, Modifier.size(20.dp).rotate(if (open) 180f else 0f), TextDark)
                    }
                    if (open) Text(rule.detail, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                        fontSize = 13.sp, lineHeight = 21.sp, color = TextDark)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TerritoryGameRulesPreview() = DaengsTheme { TerritoryGameRules() }
