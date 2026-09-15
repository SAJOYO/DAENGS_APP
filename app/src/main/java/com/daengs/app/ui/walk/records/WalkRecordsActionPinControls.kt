package com.daengs.app.ui.walk.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.daengs.app.R
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
}

/** The only action-kind control. Its display search never changes the selected walk population. */
@Composable
internal fun WalkRecordsActionSearch(state: WalkRecordsActionPinState, behavior: WalkMomentType? = null,
    modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    if (!state.enabled.value) return
    Row(modifier.horizontalScroll(rememberScrollState()).testTag("records-action-filter"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val types = if (behavior != null) listOf(behavior) else listOf(null) + RECORD_ACTION_TYPES
        types.forEach { type ->
            FilterChip(label = { Text(type?.label ?: "전체", style = MaterialTheme.typography.labelMedium) }, selected = (behavior ?: state.type.value) == type,
                leadingIcon = type?.let { { Image(painterResource(when (type) {
                    WalkMomentType.SNIFFING -> R.drawable.ic_walk_sniffing
                    WalkMomentType.EXCRETION -> R.drawable.ic_walk_excretion
                    WalkMomentType.BARKING -> R.drawable.ic_walk_barking
                    else -> R.drawable.ic_walk_note
                }), null, Modifier.size(16.dp)) } },
                onClick = { state.type.value = type; state.clearInspection(); onSearch() },
                modifier = Modifier.testTag("records-pins-type-${type?.behaviorCode ?: "all"}"))
        }
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun WalkRecordsActionPinControlsPreview() {
    DaengsTheme { Column { val state = rememberWalkRecordsActionPinState(); WalkRecordsActionSearch(state); WalkRecordsActionPinControls(state) } }
}
