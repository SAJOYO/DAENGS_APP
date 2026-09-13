package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.map.features.records.RECORD_ACTION_TYPES
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.WalkMomentType

/** Lives inside the existing map display menu; these choices affect pins only. */
@Composable
internal fun WalkRecordsActionPinControls(state: WalkRecordsActionPinState, behavior: WalkMomentType? = null) {
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    DropdownMenuItem(text = { Text("액션 핀 표시") },
        trailingIcon = { Switch(state.enabled.value, null) },
        onClick = { state.enabled.value = !state.enabled.value }, modifier = Modifier.testTag("records-pins-enabled"))
    if (state.enabled.value) {
        Text("핀 종류 · 산책 조건은 유지", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall)
        val types = if (behavior != null) listOf(behavior) else listOf(null) + RECORD_ACTION_TYPES
        types.forEach { type ->
            DropdownMenuItem(text = { Text(type?.label ?: "모든 액션") },
                leadingIcon = { RadioButton((behavior ?: state.type.value) == type, null) },
                onClick = { state.type.value = type; state.groupPoint.value = null; state.selectedKey.value = null },
                modifier = Modifier.testTag("records-pins-type-${type?.behaviorCode ?: "all"}"))
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WalkRecordsActionPinControlsPreview() {
    DaengsTheme { Column { WalkRecordsActionPinControls(rememberWalkRecordsActionPinState()) } }
}
