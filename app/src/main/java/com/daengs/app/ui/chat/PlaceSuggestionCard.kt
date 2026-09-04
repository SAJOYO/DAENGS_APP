package com.daengs.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.daengs.app.assistant.PlaceSuggestions
import com.daengs.app.map.features.places.formatPlaceMeters
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * Place 카드 묶음. 말풍선 아래 [placeCards] 가 고른 후보를 그대로 그린다.
 *
 * **여기서 다시 고르지 않는다.** 어느 후보를 보여줄지는 [placeCards] 가 이미 정했고
 * ([MAX_PLACE_CARDS] · 그룹 라운드로빈), 이 파일은 그 결과를 그리기만 한다.
 *
 * 위치 고지(`notices`)가 맨 위에 온다 — Option B 고지("현재 기기 위치를 기준으로…")는
 * 조건 없이 나가야 하는 문장이라, 카드들 밑에 묻히면 안 읽힐 수 있다.
 */
@Composable
internal fun PlaceSuggestionCards(
    presentation: PlaceCardsPresentation,
    onOpenMap: (PlaceSuggestions.Candidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presentation.notices.forEach { notice ->
            Text(notice, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        presentation.candidates.forEach { candidate ->
            PlaceSuggestionCard(candidate, onOpenMap = { onOpenMap(candidate) })
        }
        // 서버 예산(전체 9개)이 아니라 **앱이 카드로 보여줄 수 있는 상한**이라, 서버가
        // 낼 법한 notice 를 지어내지 않고 이 한 줄로만 다르다는 것을 말한다.
        if (presentation.totalCandidateCount > presentation.candidates.size) {
            Text(
                "화면에는 최대 ${MAX_PLACE_CARDS}곳만 보여드려요. " +
                    "(전체 ${presentation.totalCandidateCount}곳 중)",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 후보 하나.
 *
 * **facts·notices 문장을 다시 쓰지 않는다.** 저쪽 projection 이 이미 "확인되지
 * 않았어요." 처럼 사람이 읽을 문장으로 접어 보낸다 — [PlaceSuggestions.Fact.text]
 * 는 그대로 옮긴다. [PlaceSuggestions.Fact.severity] 만 톤을 고르는 데 쓴다.
 */
@Composable
internal fun PlaceSuggestionCard(
    candidate: PlaceSuggestions.Candidate,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.title,
                    color = TextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (candidate.kindLabel.isNotBlank()) {
                    Surface(color = PinkFaint, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            candidate.kindLabel,
                            color = TextDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(placeMetaLine(candidate), color = TextMuted, fontSize = 12.sp)

            if (candidate.summary.isNotBlank()) {
                Text(candidate.summary, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
            }

            candidate.facts.forEach { fact ->
                Text(
                    "${fact.label} · ${fact.text}",
                    color = severityColor(fact.severity),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            candidate.notices.forEach { notice ->
                Text(
                    notice.message,
                    color = severityColor(notice.severity),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }

            if (candidate.whyMatched.isNotEmpty()) {
                Text(candidate.whyMatched.joinToString(" · "), color = TextMuted, fontSize = 11.sp)
            }

            if (candidate.lat != null && candidate.lon != null) {
                DaengsWideButton("지도에서 보기", onOpenMap, accent = true)
            }
        }
    }
}

/** `미용 · 420m · 서울 마포구 양화로 1` — 거리가 없으면 그 자리만 빠진다. */
private fun placeMetaLine(candidate: PlaceSuggestions.Candidate): String = buildList {
    if (candidate.kindLabel.isNotBlank()) add(candidate.kindLabel)
    candidate.distanceMeters?.let { add(formatPlaceMeters(it)) }
    if (candidate.address.isNotBlank()) add(candidate.address)
}.joinToString(" · ")

private fun severityColor(severity: PlaceSuggestions.Severity): Color = when (severity) {
    PlaceSuggestions.Severity.WARNING -> DaengsColors.Warning
    PlaceSuggestions.Severity.CRITICAL -> DaengsColors.Error
    PlaceSuggestions.Severity.INFO, PlaceSuggestions.Severity.UNKNOWN -> TextMuted
}

/**
 * 서버가 준 좌표로 앱이 직접 만드는 `geo:` intent.
 *
 * **서버 URL 을 그대로 열지 않는다.** [PlaceSuggestions.Candidate.lat]/[lon] 은
 * [PlaceSuggestions] 파서가 이미 위경도 범위로 걸러 온 값이라 여기서 다시 검사하지
 * 않는다 — null 이면 후보에 좌표가 없다는 뜻이고, 이 함수는 그때 안 불린다
 * ([PlaceSuggestionCard] 가 버튼 자체를 감춘다).
 *
 * `journey/MapHandoff.kt` 의 신뢰 스킴 검증은 여기 관여하지 않는다 — 그건 서버가 준
 * 네이버 경로 링크(`nmap://route/...`) 전용이고, 여긴 앱이 좌표로 직접 짓는 intent 다.
 */
internal fun placeMapIntent(candidate: PlaceSuggestions.Candidate): Intent? {
    val lat = candidate.lat ?: return null
    val lon = candidate.lon ?: return null
    val label = candidate.title.ifBlank { "장소" }
    return Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})".toUri())
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun PlaceSuggestionCardsPreview() {
    val candidates = listOf(
        PlaceSuggestions.Candidate(
            placeId = PlaceSuggestions.PlaceId("kto", "P-1001"),
            title = "멍멍이 미용실 홍대점",
            summary = "반려견 전용 미용실이에요.",
            kindLabel = "미용",
            lat = 37.5563,
            lon = 126.9236,
            distanceMeters = 420,
            address = "서울 마포구 양화로 1",
            facts = listOf(
                PlaceSuggestions.Fact("반려동물 출입", "반려동물 동반이 가능해요.", PlaceSuggestions.Severity.INFO),
                PlaceSuggestions.Fact("주차", "건물 내 주차장을 이용할 수 있어요.", PlaceSuggestions.Severity.INFO),
            ),
            notices = emptyList(),
            whyMatched = listOf("요청하신 미용 목적과 일치해요."),
        ),
        PlaceSuggestions.Candidate(
            placeId = PlaceSuggestions.PlaceId("kto", "P-1002"),
            title = "댕댕 펫살롱",
            summary = "예약제로 운영되는 소형견 전문 미용실이에요.",
            kindLabel = "미용",
            lat = null,
            lon = null,
            distanceMeters = 610,
            address = "서울 마포구 동교로 15",
            facts = listOf(
                PlaceSuggestions.Fact("반려동물 출입", "확인되지 않았어요.", PlaceSuggestions.Severity.WARNING),
            ),
            notices = listOf(
                PlaceSuggestions.Notice("방문 전에 전화로 확인해 주세요.", PlaceSuggestions.Severity.WARNING),
            ),
            whyMatched = emptyList(),
        ),
    )
    DaengsTheme {
        PlaceSuggestionCards(
            presentation = PlaceCardsPresentation(
                candidates = candidates,
                totalCandidateCount = 5,
                notices = listOf(
                    "현재 기기 위치를 기준으로 찾았어요. 질문에 지역 이름이 있어도 아직 그 지역으로는 찾지 못하고, 반영하지 않았습니다.",
                ),
            ),
            onOpenMap = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
