package com.daengs.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.camera.CameraPreview
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft

/**
 * 피부 사진을 찍는 화면.
 *
 * **찍는 동안 네모를 보여 준다.** 지금까지는 시스템 카메라로 던져서, 병변이 얼마나
 * 크게 찍혀야 하는지를 찍고 나서야 알았다. 서버는 병변이 가로의 28~68% 일 때만
 * 추론하고 밖이면 재촬영으로 돌려보낸다 — 그 밴드를 찍기 전에 보여 준다.
 *
 * **맞았는지 판정하지 않는다.** 네모는 한 색으로 가만히 있는다. 화면 안에 무엇이
 * 찍히는지는 앱이 모르므로, 색으로 좋다고 말하면 "초록이었는데 왜 다시 찍으라느냐"
 * 가 된다. 보행 촬영 화면과 같은 규칙이다.
 *
 * 찍고 나면 [GuideFrameScreen] 이 열려 네모를 한 번 더 맞춘다. 손이 흔들려 어긋나는
 * 일이 흔하고, 그 네모가 그대로 서버의 bbox 라 마지막 조정이 필요하다.
 */
@Composable
fun SkinCaptureScreen(
    controller: LifecycleCameraController,
    onBack: () -> Unit,
    onShutter: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    BackHandler(onBack = onBack)

    Box(modifier.fillMaxSize().background(Color(0xFF2B2320))) {
        // **사진과 같은 비율로 잡는다.** 화면 전체에 채우면 프리뷰가 잘려서, 화면에서
        // 본 네모와 사진 속 자리가 어긋난다 — 가이드에 맞춰 찍었는데 확인 화면에서
        // 네모가 딴 데 가 있던 이유가 이것이다. 세로 사진은 3:4 다.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(PHOTO_ASPECT),
        ) {
            CameraPreview(controller, Modifier.fillMaxSize())
            SkinGuideBox(Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .align(Alignment.TopStart)
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("<", color = CardWhite, fontSize = 26.sp) }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "확인하고 싶은 곳이 박스의 중앙에 오도록 해주세요",
                color = CardWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "찍은 뒤에 네모를 한 번 더 맞출 수 있어요",
                color = Color(0xFFD9C9C3),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryButton(onPick)
                ShutterButton(enabled = !busy, onClick = onShutter)
                // 오른쪽은 비워 둔다. 셔터를 가운데에 세우려는 자리다 — 보행 촬영
                // 화면과 같은 배치라 두 화면이 같은 손놀림이 된다.
                Spacer(Modifier.size(52.dp))
            }
        }
    }
}

/**
 * 세로 사진의 가로:세로. 카메라를 4:3 으로 고정했으므로 세로로 들면 3:4 다.
 *
 * 이 값이 프리뷰 상자의 비율이자 [Band.CAPTURE_WIDTH] 네모가 정사각이 되는 기준이다.
 */
internal const val PHOTO_ASPECT = 3f / 4f

/**
 * 가운데 네모와 그 바깥의 그늘.
 *
 * 네모 크기는 서버가 실측해 둔 권장 밴드의 한가운데다 (Band.CAPTURE_WIDTH).
 * 여기서 임의로 정하면 가이드에 맞춰 찍었는데 "너무 작아요" 가 뜬다.
 */
@Composable
private fun SkinGuideBox(modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithContent {
            drawContent()
            val side = size.width * Band.CAPTURE_WIDTH
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            // 바깥을 죽인다. 네 장으로 나눠 그리는 것은 가운데를 뚫기 위해서다 —
            // 반투명 사각형 하나로는 구멍을 못 낸다.
            val shade = Color(0x99000000)
            drawRect(shade, Offset.Zero, Size(size.width, top))
            drawRect(shade, Offset(0f, top + side), Size(size.width, size.height - top - side))
            drawRect(shade, Offset(0f, top), Size(left, side))
            drawRect(shade, Offset(left + side, top), Size(size.width - left - side, side))
        },
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(Band.CAPTURE_WIDTH)
                .aspectRatio(1f)
                .border(2.dp, DaengPink, RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(50))
            .background(CardWhite)
            .border(3.dp, PinkSoft, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(50))
                .background(if (enabled) DaengPinkDeep else PinkSoft),
        )
    }
}

@Composable
private fun GalleryButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DaengsIconView(DaengsIcon.Gallery, Modifier.size(22.dp), tint = CardWhite) }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun SkinGuideBoxPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF6B564C))) {
            SkinGuideBox(Modifier.fillMaxSize())
        }
    }
}
