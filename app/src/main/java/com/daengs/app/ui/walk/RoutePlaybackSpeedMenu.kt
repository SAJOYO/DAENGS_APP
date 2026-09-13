package com.daengs.app.ui.walk

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.walk.routeexplorer.RoutePlaybackSpeed

@Composable
internal fun RoutePlaybackSpeedMenu(speed: RoutePlaybackSpeed, onSelect: (RoutePlaybackSpeed) -> Unit, compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true },
            contentPadding = if (compact) PaddingValues(horizontal = 4.dp) else ButtonDefaults.TextButtonContentPadding,
            modifier = Modifier.semantics {
            contentDescription = "재생 속도"
            stateDescription = "${speed.multiplier}배"
        }) { Text("${speed.multiplier}×", fontSize = if (compact) 12.sp else 14.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RoutePlaybackSpeed.entries.forEach { option ->
                DropdownMenuItem(text = { Text("${option.multiplier}×") },
                    modifier = Modifier.semantics { selected = option == speed },
                    trailingIcon = { if (option == speed) Text("✓") },
                    onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutePlaybackSpeedPreview() { DaengsTheme {
    var speed by remember { mutableStateOf(RoutePlaybackSpeed.FOUR) }
    RoutePlaybackSpeedMenu(speed, { speed = it })
} }
