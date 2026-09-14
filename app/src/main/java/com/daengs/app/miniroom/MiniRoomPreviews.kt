package com.daengs.app.miniroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.ItemArtSpec
import com.daengs.app.miniroom.art.ItemLabels
import com.daengs.app.miniroom.RoomTheme
import com.daengs.app.miniroom.art.itemSpecs
import com.daengs.app.miniroom.art.rememberItemCatalog
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextMuted

@Preview(widthDp = 411, heightDp = 380, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun MiniRoomCanvasPreview() {
    DaengsTheme {
        MiniRoomCanvas(
            state = rememberMiniRoomState(),
            catalog = rememberItemCatalog(),
            modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
            // 무한 애니메이션은 미리보기에서 안 돌기 때문에 프레임을 찍어준다
            frameTimeMs = 400L,
        )
    }
}

/**
 * 홈 첫 진입 연출의 **한가운데.** 카메라가 문에서 반쯤 빠져나온 순간 — 문은 아직 열려
 * 있고, 밤이라 방은 어둡다. 확대된 그림이 캔버스 밖으로 안 새는지(`clipToBounds`)와
 * 문밖 풍경이 문틀 안에만 보이는지를 여기서 본다.
 */
@Preview(name = "첫 진입 — 문으로 들어오기(밤)", widthDp = 411, heightDp = 380, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun MiniRoomIntroPreview() {
    DaengsTheme {
        MiniRoomCanvas(
            state = rememberMiniRoomState(),
            catalog = rememberItemCatalog(),
            outside = OutsideView.NIGHT_CLEAR,
            modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
            frameTimeMs = 400L,
            intro = RoomIntro(previewElapsedMs = (IntroTimeline.CAMERA_START_MS + IntroTimeline.CAMERA_END_MS) / 2),
        )
    }
}

/**
 * 배웅한 아이가 있는 방.
 *
 * 하트가 **얼마나 작아야 하는지**를 여기서 본다. 실기기에서는 폰을 들고 방을 열어야
 * 하는데, 이 표시는 크기 하나로 성패가 갈린다 — 크면 소품이 되고 작으면 안 보인다.
 */
@Preview(name = "배웅한 아이", widthDp = 411, heightDp = 380, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun MiniRoomDepartedPreview() {
    DaengsTheme {
        MiniRoomCanvas(
            state = rememberMiniRoomState(),
            catalog = rememberItemCatalog(),
            // 셋 중 가운데 아이만 배웅했다. 나머지 둘과 나란히 놓고 봐야 표시가
            // 눈에 띄는지, 너무 튀는지를 잴 수 있다.
            herd = rememberDogHerd(DogBreed.demoRoster(3), departed = setOf(1)),
            modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
            frameTimeMs = 400L,
        )
    }
}

@Preview(widthDp = 411, heightDp = 380, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun MiniRoomDoorOpenPreview() {
    DaengsTheme {
        MiniRoomCanvas(
            state = rememberMiniRoomState(),
            catalog = rememberItemCatalog(),
            modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
            frameTimeMs = 400L,
            doorOpenOverride = 0.55f,
        )
    }
}

/**
 * 창밖·문밖 여섯 벌. **문을 반쯤 열어 둔다** — 안 그러면 문밖 그림이 안 보여서
 * 절반만 확인하게 된다.
 *
 * 이걸 미리보기로 두는 이유: 실제 시각·날씨를 따르기 때문에 앱을 켜서 볼 수 있는
 * 것은 지금 바깥에 있는 한 벌뿐이다. 밤·눈을 보려고 밤에 눈이 오길 기다릴 수는 없다.
 *
 * **폭을 411dp 로 맞춘다.** 창유리는 화면에서 101px 밖에 안 되고 문틈은 86px 이다.
 * 원본 크기로 보면 비가 굵어 보이는데 화면에서는 점이 된다 (HISTORY 11절).
 */
@Preview(widthDp = 411, heightDp = 1180, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun OutsideViewsPreview() {
    DaengsTheme {
        Column(
            modifier = Modifier.background(CreamBg).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OutsideView.entries.forEach { outside ->
                Text(outside.label, fontSize = 10.sp, color = TextMuted)
                MiniRoomCanvas(
                    state = rememberMiniRoomState(),
                    catalog = rememberItemCatalog(),
                    outside = outside,
                    modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
                    frameTimeMs = 400L,
                    doorOpenOverride = 0.7f,
                )
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 320, showBackground = true, backgroundColor = 0xFFFDF1EC)
@Composable
private fun MiniRoomCanvasSmallPreview() {
    DaengsTheme {
        MiniRoomCanvas(
            state = rememberMiniRoomState(),
            catalog = rememberItemCatalog(),
            modifier = Modifier.fillMaxWidth().aspectRatio(RoomSpec.ASPECT),
            frameTimeMs = 900L,
        )
    }
}

/**
 * 아트 시트 — 카탈로그의 모든 도형을 확대해서 ArtBox 윤곽선·기준점과 함께 늘어놓는다.
 *
 * 도형을 손볼 때 이 미리보기만 보면 되므로 왕복이 짧아지고,
 * 기준점(anchor)을 잘못 잡은 아트가 눈에 바로 띈다.
 * 십자 표시가 바닥에 닿는 점 = 타일 중심에 놓이는 지점이다.
 */
@Preview(widthDp = 411, heightDp = 460, showBackground = true)
@Composable
private fun ArtSheetPreview() {
    val zoom = 1.7f
    DaengsTheme {
        Column(
            Modifier.background(CreamBg).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 아트가 전부 PNG 라 여기서는 그림이 안 그려진다(Res 는 리소스 해석이
            // @Composable 이라 Canvas 안에서 못 부른다). 대신 **상자와 기준점**이 보이므로
            // 앵커를 맞출 때 쓸모가 있다 — 그게 원래 이 미리보기의 목적이었다.
            itemSpecs(RoomTheme.DEFAULT).entries.chunked(3).forEach { rowSpecs ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSpecs.forEach { (id, spec) ->
                        Column(Modifier.size(125.dp, 140.dp)) {
                            Text(
                                "${ItemLabels[id] ?: id}  ${spec.box.size.width.toInt()}x${spec.box.size.height.toInt()}",
                                fontSize = 9.sp,
                                color = TextMuted,
                            )
                            Canvas(Modifier.size(125.dp, 128.dp)) {
                                // 체커보드 — 투명 영역과 흰 아트를 구분하기 위해
                                val c = 8f
                                var y = 0f
                                var r = 0
                                while (y < size.height) {
                                    var x = 0f
                                    var q = r
                                    while (x < size.width) {
                                        if (q % 2 == 0) {
                                            drawRect(
                                                Color(0xFFEDEDED),
                                                Offset(x, y),
                                                Size(c, c),
                                            )
                                        }
                                        x += c; q++
                                    }
                                    y += c; r++
                                }

                                val box = spec.box
                                val ox = (size.width - box.size.width * zoom) / 2f
                                val oy = (size.height - box.size.height * zoom) / 2f
                                translate(ox, oy) {
                                    scale(zoom, zoom, pivot = Offset.Zero) {
                                        when (spec) {
                                            is ItemArtSpec.Shapes -> spec.draw(this, 2)
                                            is ItemArtSpec.Sheet -> spec.fallback(this, 2)
                                            is ItemArtSpec.Res -> Unit
                                        }
                                    }
                                    // ArtBox 윤곽선
                                    drawRect(
                                        Color(0x553355FF),
                                        Offset.Zero,
                                        Size(box.size.width * zoom, box.size.height * zoom),
                                        style = Stroke(1f),
                                    )
                                    // 기준점 십자
                                    val a = Offset(box.anchor.x * zoom, box.anchor.y * zoom)
                                    drawLine(Color(0xAAFF3366), a - Offset(6f, 0f), a + Offset(6f, 0f))
                                    drawLine(Color(0xAAFF3366), a - Offset(0f, 6f), a + Offset(0f, 6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
