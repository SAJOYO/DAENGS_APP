package com.daengs.app.ui.places.lab

import androidx.compose.foundation.BorderStroke
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
    onEdit: (String) -> Unit, onSubmit: () -> Unit, onAi: () -> Unit, onBack: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로가기" }) {
            BackSearchIcon(Modifier.size(22.dp))
        }
        Surface(Modifier.weight(1f).height(48.dp).testTag("place-search-field"), shape = shape,
            color = DaengsColors.Surface, border = BorderStroke(1.dp, PlaceSearchStyle.Border)) {
            BasicTextField(value = draft, onValueChange = onEdit, singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = DaengsColors.TextPrimary),
                cursorBrush = SolidColor(DaengsColors.BrandPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { input ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).padding(start = 12.dp)) {
                            if (draft.isEmpty()) Text(placeholder, fontSize = 12.sp, maxLines = 1, color = DaengsColors.TextSecondary)
                            input()
                        }
                        IconButton(onClick = onSubmit, modifier = Modifier.size(48.dp).semantics { contentDescription = "검색 실행" }) {
                            SearchActionIcon(Modifier.size(22.dp))
                        }
                    }
                })
        }
        OutlinedButton(onClick = onAi, shape = shape, contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (ai) DaengsColors.BrandPrimarySoft else DaengsColors.Surface),
            border = BorderStroke(1.dp, if (ai) DaengsColors.BrandPrimary else PlaceSearchStyle.Border),
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "AI 조건 검색 전환"
                stateDescription = if (ai) "켜짐" else "꺼짐"
            }) { RobotSearchIcon(Modifier.size(22.dp), active = ai) }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SearchHeaderPreview() {
    DaengsTheme { PlaceSearchHeader("", "장소명 검색", false, {}, {}, {}, {}) }
}
