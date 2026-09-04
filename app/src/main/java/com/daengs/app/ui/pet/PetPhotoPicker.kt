package com.daengs.app.ui.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.daengs.app.screening.Photo
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.dogcard.FaceFrame
import com.daengs.app.ui.dogcard.FaceFrameStep
import com.daengs.app.ui.dogcard.bakeFramed
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 프로필 사진을 고르고 **원 안에 맞춘다.**
 *
 * 카드 뽑기가 쓰는 그 원형 틀([FaceFrameStep])을 그대로 쓴다. 다른 점은 **누끼를 안
 * 딴다**는 것 하나다 — 프로필은 배경이 있어도 되고, 누끼는 실패할 수 있는 단계다.
 *
 * 열리자마자 사진 고르기가 뜬다. 사진을 안 고르고 닫으면 아무 일도 없다.
 *
 * @param onDone 맞춘 사진. **null 이면 그만둔 것이다**
 */
@Composable
fun PetPhotoPicker(onDone: (Bitmap?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var frame by remember { mutableStateOf(FaceFrame.CENTER) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            // 고르기 창에서 그냥 나온 것. 빈 화면을 남겨 두지 않는다.
            onDone(null)
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            runCatching { Photo.decodeUpright(context, uri, Photo.MAX_EDGE) }
                .onSuccess {
                    photo = it
                    frame = FaceFrame.CENTER
                    error = null
                }
                .onFailure { error = it.message ?: "사진을 읽지 못했어요." }
            busy = false
        }
    }

    // 열리자마자 한 번만. `Unit` 이 키라 되돌아와도 다시 안 뜬다.
    LaunchedEffect(Unit) {
        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    BackHandler { onDone(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shot = photo
        if (shot == null) {
            Text(
                if (busy) "사진을 여는 중이에요…" else error ?: "사진을 고르는 중이에요",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (!busy && error != null) {
                Spacer(Modifier.height(12.dp))
                DaengsTextAction(
                    "다시 고르기",
                    onClick = {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
            return@Column
        }

        Text("얼굴만 원 안에 넣어 주세요", color = TextDark, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        FaceFrameStep(face = shot, frame = frame, onChange = { frame = it })
        Spacer(Modifier.height(10.dp))
        Text(
            "두 손가락으로 키우거나 끌어서 맞춰 주세요.",
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            DaengsWideButton("다시 고르기", { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            DaengsWideButton(
                "이 사진으로",
                {
                    busy = true
                    scope.launch {
                        val made = withContext(Dispatchers.Default) { bakeProfile(shot, frame) }
                        busy = false
                        onDone(made)
                    }
                },
                Modifier.weight(1f),
                accent = true,
                busy = busy,
            )
        }
        Spacer(Modifier.height(6.dp))
        DaengsTextAction("그만두기", { onDone(null) }, tint = TextMuted)
    }
}

/**
 * 맞춘 그대로 정사각형 한 장으로.
 *
 * ⚠ **바탕을 깔고 그린다.** [bakeFramed] 는 원본 밖을 투명으로 남기는데(카드 구멍에
 * 끼우려고 그렇다), 사용자가 `FRAME_MIN_SCALE` 까지 줄이면 가장자리가 빈다. 프로필은
 * JPEG 으로 저장하고 **JPEG 에는 알파가 없어서 그 자리가 까맣게 나온다.**
 * `bakeFramed` 는 카드가 같이 쓰므로 건드리지 않고 여기서 덮는다.
 */
internal fun bakeProfile(source: Bitmap, frame: FaceFrame): Bitmap {
    val framed = bakeFramed(source, frame, com.daengs.app.pet.PetPhotos.SIDE)
    val out = Bitmap.createBitmap(framed.width, framed.height, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        drawColor(PROFILE_BACKING)
        drawBitmap(framed, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    framed.recycle()
    return out
}

/** 빈 자리를 채우는 색. 앱 배경(`CreamBg`)과 같아야 원 밖이 튀지 않는다. */
private const val PROFILE_BACKING = 0xFFFDF4F0.toInt()
