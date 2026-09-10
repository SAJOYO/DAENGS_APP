package com.daengs.app.ui.places.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.daengs.app.location.GeoPoint
import com.daengs.app.map.shell.MapHost
import com.daengs.app.map.shell.MapScene
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.places.PlaceDogAssistant
import com.daengs.app.ui.theme.DaengsTheme

/** Debug 전용. 실제 지도 projection/IME를 확인하되 검색·LLM·인증 API는 호출하지 않는다. */
class PlaceDogBubbleLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DogBubbleLab() }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DogBubbleLab() {
    val origin = remember { GeoPoint(37.5446, 127.0559) }
    var anchor by remember { mutableStateOf<Offset?>(null) }
    var phase by remember { mutableStateOf("입력") }
    var open by remember { mutableStateOf(false) }
    DaengsTheme {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text("말풍선 UI 검토 · 예시 응답 · 서버 미연결", Modifier.padding(12.dp))
            Row {
                listOf("입력", "생각", "답변").forEach { option ->
                    TextButton(onClick = { phase = option; open = true }) { Text(option) }
                }
            }
            Box(Modifier.weight(1f)) {
                MapHost(MapScene(currentPosition = origin), origin, true, avatarRes = DogBreed.BEAGLE.portraitRes,
                    onCameraIdle = {}, onCameraGesture = {}, onSelectPlace = {}, onAvatarPosition = { anchor = it },
                    modifier = Modifier.fillMaxSize())
                key(phase) {
                    PlaceDogAssistant(anchor, phase == "생각", phase == "답변", open, { open = it },
                        { phase = "생각" }, { open = false }, onUndo = if (phase == "답변") ({ phase = "입력" }) else null) {
                        Text("주차 가능한 카페를 검색 조건에 담았어. [예시]")
                    }
                }
            }
        }
    }
}
