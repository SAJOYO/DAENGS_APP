package com.daengs.app.map.features.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 상태와 이벤트만 받는다. PR1의 실제 PlacesRoute에는 미연결 입력을 노출하지 않는다. */
@Composable
fun PlaceNameSearchField(
    query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
) {
    Surface(modifier, shape = RoundedCornerShape(19.dp), color = PlaceSearchColors.Field) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query, onValueChange = onQueryChange, enabled = enabled, singleLine = true,
                modifier = Modifier.weight(1f).semantics { contentDescription = "장소명 검색" },
                textStyle = TextStyle(fontSize = 16.sp, color = PlaceSearchColors.Ink),
                cursorBrush = SolidColor(PlaceSearchColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (enabled) onSearch() }),
                decorationBox = { field ->
                    Box { if (query.isEmpty()) Text("어디를 찾으세요?", color = PlaceSearchColors.Muted,
                        fontSize = 16.sp); field() }
                },
            )
            Surface(onClick = onSearch, enabled = enabled, shape = RoundedCornerShape(14.dp),
                color = PlaceSearchColors.Accent,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "장소 검색 실행" }) {
                Canvas(Modifier.padding(14.dp)) {
                    val r = size.minDimension * .32f
                    val center = Offset(size.width * .4f, size.height * .4f)
                    drawCircle(PlaceSearchColors.Background, r, center, style = Stroke(1.8.dp.toPx()))
                    drawLine(PlaceSearchColors.Background,
                        center + Offset(r * .7f, r * .7f), Offset(size.width * .95f, size.height * .95f),
                        1.8.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/** 목적의 표시 ID/문구. 시설 종류나 검색 필터를 이 모델에 넣지 않는다. */
data class PlaceAiSuggestion(val id: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceAiQuestionPanel(
    question: String, onQuestionChange: (String) -> Unit, onAsk: () -> Unit,
    suggestions: List<PlaceAiSuggestion>, onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier, loading: Boolean = false,
) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = PlaceSearchColors.AiBackground) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✦ AI에게 물어보기", color = PlaceSearchColors.AiInk, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().background(PlaceSearchColors.Background,
                RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = question, onValueChange = onQuestionChange, enabled = !loading,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "AI에게 질문" },
                    singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = PlaceSearchColors.Ink),
                    cursorBrush = SolidColor(PlaceSearchColors.AiInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!loading && question.isNotBlank()) onAsk() }),
                    decorationBox = { field -> Box {
                        if (question.isEmpty()) Text("강아지랑 뭐 하고 놀까?", color = PlaceSearchColors.Muted,
                            fontSize = 14.sp)
                        field()
                    } },
                )
                TextButton(onClick = onAsk, enabled = !loading && question.isNotBlank()) {
                    Text(if (loading) "생각 중" else "질문", color = PlaceSearchColors.AiInk)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                suggestions.forEach { suggestion ->
                    TextButton(onClick = { onSuggestion(suggestion.id) }, enabled = !loading,
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("#${suggestion.label}", color = PlaceSearchColors.AiInk, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
