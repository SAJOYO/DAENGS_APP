package com.daengs.app.ui.dex

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.photoFailureText
import com.daengs.app.screening.Photo
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** 만들기 화면이 고르는 아이. 이름이 제목판에 찍힌다. */
data class PhotoDog(val id: String, val name: String, val isPrimary: Boolean)

/**
 * 처음 골라 둘 달. **닫힌 달을 기본으로 두지 않는다** — 누르자마자 서버가 404 를 준다.
 * 잠긴 칸을 눌러 왔으면 그 달, 아니면 이번 달, 그것도 닫혔으면 열린 첫 달.
 */
fun defaultPhotoMonth(start: Int?, today: Int, open: Set<Int> = OPEN_PHOTO_MONTHS): Int = when {
    start != null && start in open -> start
    today in open -> today
    else -> open.min()
}

fun defaultPhotoDog(dogs: List<PhotoDog>): PhotoDog? = dogs.firstOrNull { it.isPrimary } ?: dogs.firstOrNull()

/**
 * 포토 카드 만들기 — 달 · 아이 · 사진 한 장.
 *
 * **사진을 기기에서 긴 변 1600 JPEG 로 줄여 보낸다** (`Photo.prepare`). 서버도 어차피 그만큼
 * 줄이므로 20MB 원본을 올릴 이유가 없다. 원형 틀에 맞추기(누끼 뽑기)는 없다 — 서버가
 * 사진 전체를 보고 강아지를 찾는다.
 */
@Composable
fun PhotoCardMakeScreen(
    startMonth: Int?,
    dogs: List<PhotoDog>,
    busy: Boolean,
    error: String?,
    /** 방금 보낸 카드. null 이면 아직 안 보냈다 */
    watching: PhotoCard?,
    /** 그 카드의 받아 둔 그림 */
    watchingFile: File?,
    onSubmit: (month: Int, dog: PhotoDog, jpeg: ByteArray) -> Unit,
    /** 「다 되면 알려 주세요」 · 그리는 중 뒤로가기 — 요청은 서버에 있다, 취소가 아니다 */
    onWaitElsewhere: () -> Unit,
    onRevealed: (String) -> Unit,
    /** 실패 뒤 「다시 만들기」 — 실패 행을 지우고 사진 고르기로 */
    onRetry: (PhotoCard) -> Unit,
    onOpenDex: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(defaultPhotoMonth(startMonth, LocalDate.now().monthValue)) }
    var dog by remember(dogs) { mutableStateOf(defaultPhotoDog(dogs)) }
    var picked by remember { mutableStateOf<PreparedPhoto?>(null) }
    var reading by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            Photo.prepare(context, uri)
                .onSuccess { picked = it; pickError = null }
                .onFailure { pickError = it.message ?: "사진을 읽지 못했어요." }
            reading = false
        }
    }

    when (photoMakeStage(watching, watchingFile)) {
        PhotoMakeStage.Pick -> {
            // **항상 등록해 둔다.** `busy` 일 때 꺼 두면 뒤로가기가 바깥(도감)의 핸들러로 넘어가
            // 업로드 중에 화면이 닫히고, 뒤이어 오는 서버 오류(429·404)가 숨은 `createError` 로만 남는다.
            // 눌러도 `busy` 면 무시한다.
            BackHandler { if (!busy) onCancel() }
            PhotoCardMakeContent(
                month = month,
                onMonth = { month = it },
                dogs = dogs,
                dog = dog,
                onDog = { dog = it },
                preview = picked?.thumbnail?.asImageBitmap(),
                busy = busy || reading,
                error = error ?: pickError,
                onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSubmit = {
                    val chosen = dog
                    val photo = picked
                    if (chosen != null && photo != null) onSubmit(month, chosen, photo.jpeg)
                },
                onCancel = onCancel,
            )
        }
        PhotoMakeStage.Drawing -> {
            val card = watching!!
            BackHandler { onWaitElsewhere() }
            PhotoDrawingBody(dogName = card.dogName, month = card.month, onWaitElsewhere = onWaitElsewhere)
        }
        PhotoMakeStage.Reveal -> {
            val card = watching!!
            val dex = photoCardFor(card.month)
            BackHandler { onOpenDex() }
            if (dex != null && watchingFile != null) {
                PhotoRevealFlow(dex, card, watchingFile, onRevealed = onRevealed, onOpenDex = onOpenDex)
            }
        }
        PhotoMakeStage.Failed -> {
            val card = watching!!
            BackHandler { onRetry(card); onCancel() }
            PhotoFailedBody(
                text = photoFailureText(card.month, card.errorCode),
                onRetry = { onRetry(card) },
                onCancel = { onRetry(card); onCancel() },
            )
        }
    }
}

@Composable
private fun PhotoCardMakeContent(
    month: Int,
    onMonth: (Int) -> Unit,
    dogs: List<PhotoDog>,
    dog: PhotoDog?,
    onDog: (PhotoDog) -> Unit,
    preview: ImageBitmap?,
    busy: Boolean,
    error: String?,
    onPick: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("포토 카드 만들기", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("사진 한 장으로 그 달의 카드를 그려 드려요. 1분쯤 걸려요.", color = TextMuted, fontSize = 13.sp)

        Spacer(Modifier.height(20.dp))
        Text("어느 카드로 만들까요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OPEN_PHOTO_MONTHS.sorted().forEach { m ->
                // 카탈로그에 없는 달은 안 보인다 — 이름은 이제 안 보여줘도 열 수 없는
                // 달까지 칸으로 뜨면 안 된다(사용자 결정, 2026-09-15: 달만 적는다).
                photoCardFor(m) ?: return@forEach
                Choice("${m}월", on = m == month) { onMonth(m) }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("어느 아이인가요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dogs.forEach { d -> Choice(d.name, on = d.id == dog?.id) { onDog(d) } }
        }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .clickable(enabled = !busy, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("＋ 사진 고르기", color = DaengPink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        // 서버 README 「정면 사진 안내」 — 엎드린 옆모습 사진에서 닮음이 떨어졌다.
        Text("얼굴이 정면으로 잘 보이는 사진이 잘 나와요", color = TextMuted, fontSize = 12.sp)

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextDark, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            DaengsWideButton("다른 사진", onPick, Modifier.weight(1f), enabled = !busy)
            Spacer(Modifier.size(10.dp))
            DaengsWideButton(
                "이 사진으로 만들기",
                onSubmit,
                Modifier.weight(1f),
                enabled = preview != null && dog != null,
                busy = busy,
                accent = true,
            )
        }
        Spacer(Modifier.height(6.dp))
        // `DaengsTextAction` 은 `enabled` 를 안 받는다 — 업로드 중엔 눌러도 무시해서
        // 같은 자리를 두 번 안 닫는다 (`BackHandler` 와 같은 이유).
        DaengsTextAction("그만두기", { if (!busy) onCancel() }, tint = TextMuted)
    }
}

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) CardWhite else TextDark,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) DaengPink else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun PhotoCardMakeContentPreview() {
    PhotoCardMakeContent(
        month = 9, onMonth = {},
        dogs = listOf(PhotoDog("a", "콩이", true), PhotoDog("b", "보리", false)),
        dog = PhotoDog("a", "콩이", true), onDog = {},
        preview = null, busy = false,
        error = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.",
        onPick = {}, onSubmit = {}, onCancel = {},
    )
}

/** 서버가 그리는 동안. 뒷면이 살아 있고, 나가도 된다는 걸 말해 준다. */
@Composable
private fun PhotoDrawingBody(dogName: String, month: Int, onWaitElsewhere: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CreamBg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        PhotoCardBack(Modifier.fillMaxWidth(0.62f))
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.62f), color = DaengPink, trackColor = PinkFaint)
        Spacer(Modifier.height(16.dp))
        Text("${dogName}의 ${month}월 카드를 그리는 중이에요", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("1분쯤 걸려요. 나가도 다 되면 도감에서 알려 드려요.", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        DaengsWideButton("다 되면 알려 주세요", onWaitElsewhere, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PhotoFailedBody(text: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CreamBg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = TextDark, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        DaengsWideButton("다시 만들기", onRetry, Modifier.fillMaxWidth(), accent = true)
        Spacer(Modifier.height(6.dp))
        DaengsTextAction("그만두기", onCancel, tint = TextMuted)
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun PhotoDrawingBodyPreview() {
    PhotoDrawingBody("안녕", 9) {}
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PhotoFailedBodyPreview() {
    PhotoFailedBody("9월 카드를 만들지 못했어요 · 잠시 뒤 다시 만들어 주세요", {}, {})
}
