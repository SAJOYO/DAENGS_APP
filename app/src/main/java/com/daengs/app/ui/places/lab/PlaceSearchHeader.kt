package com.daengs.app.ui.places.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.places.PlaceSearchStyle

@Composable
internal fun PlaceSearchHeader(draft: String, placeholder: String, ai: Boolean,
    onEdit: (String) -> Unit, onSubmit: () -> Unit, onAi: () -> Unit, onBack: (() -> Unit)?,
    onFilters: (() -> Unit)? = null, filterCount: Int = 0, showAiToggle: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로가기" }) {
            BackSearchIcon(Modifier.size(22.dp))
        }
        Surface(Modifier.weight(1f).height(48.dp).testTag("place-search-field"), shape = shape,
            color = if (ai) PlaceSearchStyle.AiBackground else DaengsColors.Surface,
            border = BorderStroke(1.dp, if (ai) PlaceSearchStyle.AiBorder else PlaceSearchStyle.Border)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value = draft, onValueChange = onEdit, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = DaengsColors.TextPrimary),
                cursorBrush = SolidColor(if (ai) PlaceSearchStyle.AiForeground else DaengsColors.BrandPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                decorationBox = { input ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) Text(placeholder, fontSize = 12.sp, lineHeight = 16.sp,
                            maxLines = 2, color = if (ai) PlaceSearchStyle.AiForeground else DaengsColors.TextSecondary)
                        input()
                    }
                })
                if (onFilters != null) IconButton(onClick = onFilters,
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "검색 필터"
                        stateDescription = "필수 조건 ${filterCount}개 적용"
                    }) {
                    BadgedBox(badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } }) {
                        FilterSearchIcon(Modifier.size(22.dp), active = filterCount > 0)
                    }
                }
                IconButton(onClick = onSubmit, modifier = Modifier.size(48.dp).semantics { contentDescription = "검색 실행" }) {
                    SearchActionIcon(Modifier.size(22.dp))
                }
                if (showAiToggle) IconToggleButton(checked = ai, onCheckedChange = { onAi() },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "AI 조건 검색 전환"
                        stateDescription = if (ai) "켜짐" else "꺼짐"
                    }) {
                    Box(Modifier.size(36.dp).background(
                        if (ai) PlaceSearchStyle.AiButton else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        RobotSearchIcon(Modifier.size(22.dp), active = ai)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SearchHeaderPreview() {
    DaengsTheme { PlaceSearchHeader("", "장소명 검색", false, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 320)
@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AiSearchHeaderPreview() {
    DaengsTheme { PlaceSearchHeader("", "AI에게 원하는 장소를 말해보세요", true, {}, {}, {}, {}) }
}
