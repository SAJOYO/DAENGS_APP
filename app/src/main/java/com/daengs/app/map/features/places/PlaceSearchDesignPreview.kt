package com.daengs.app.map.features.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.place.PlaceKind
import com.daengs.app.ui.theme.DaengsTheme

/** 네트워크/위치 없는 디자인 샘플. PlacesRoute에서 호출하지 않는다. */
@Preview(name = "전체 디자인 · 기본", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "전체 디자인 · 작은 화면", widthDp = 320, heightDp = 740, showBackground = true)
@Composable
fun PlaceSearchDesignPreview() {
    DaengsTheme {
        var category by remember { mutableStateOf(PlaceKind.CAFE) }
        var query by remember { mutableStateOf("") }
        var question by remember { mutableStateOf("") }
        var event by remember { mutableStateOf("입력과 선택만 확인하는 화면입니다") }
        Column(Modifier.fillMaxSize().background(PlaceSearchColors.Background)
            .systemBarsPadding().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("내 주변", color = PlaceSearchColors.Ink, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("디자인 미리보기 · API 미연결", color = PlaceSearchColors.Muted, fontSize = 12.sp)
                PlaceNameSearchField(query, { query = it }, { event = "장소명 검색 요청: $query (미연결)" })
                PlaceCategoryMenu(category, true, { category = it })
                Text("홍대입구 기준 · 3km · 예시", color = PlaceSearchColors.Muted, fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().height(180.dp).background(PlaceSearchColors.Field),
                contentAlignment = Alignment.Center) {
                Text("지도 영역 · 실제 지형 아님", color = PlaceSearchColors.Muted, fontSize = 12.sp)
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PlaceAiQuestionPanel(question, { question = it }, { event = "AI 질문 요청: $question (미연결)" },
                    listOf(PlaceAiSuggestion("walk", "가볍게산책"),
                        PlaceAiSuggestion("toys", "장난감구경"), PlaceAiSuggestion("rest", "함께쉬기")),
                    { event = "AI 제안 선택: $it (미연결)" })
                Text("일반 검색 · ${categoryLabel(category)}", color = PlaceSearchColors.Ink, fontSize = 16.sp)
                Text(event, color = PlaceSearchColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Preview(name = "독립 입력 · 선택/로딩", widthDp = 360, showBackground = true)
@Composable
private fun PlaceSearchInputsLoadingPreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceNameSearchField("홍대", {}, {}, enabled = false)
            PlaceAiQuestionPanel("강아지랑 놀고 싶어", {}, {},
                listOf(PlaceAiSuggestion("walk", "가볍게산책")), {}, loading = true)
        }
    }
}
