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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.daengs.app.dogcard.photo.PhotoCardKey
import com.daengs.app.dogcard.photo.labelList
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
 * 처음 골라 둘 카드. **닫힌 달을 기본으로 두지 않는다** — 누르자마자 서버가 404 를 준다.
 * 잠긴 칸을 눌러 왔으면 그 카드, 아니면 이번 달, 그것도 닫혔으면 열린 첫 달.
 *
 * 종류 카드(딸기·상추)로 들어왔으면 그대로 쓴다 — 종류에는 닫힘이 없다 (D-085).
 * **종류를 기본으로 고르지는 않는다** — 아무 데서나 열었을 때는 이번 달이 자연스럽다.
 */
fun defaultPhotoCard(
    start: PhotoCardKey?,
    today: Int,
    openMonths: Set<Int> = OPEN_PHOTO_MONTHS,
): PhotoCardKey = when {
    start != null && photoCardSelectable(start, openMonths) -> start
    today in openMonths -> PhotoCardKey.of(today)
    else -> PhotoCardKey.of(openMonths.min())
}

/**
 * 만들기 화면에 칸이 뜨는 카드인가. 달은 열려 있고 카탈로그에 있어야 하고, 종류는 앱이
 * 아는 것이어야 한다 — 이름을 모르는 카드는 칸으로 그릴 수가 없다.
 */
fun photoCardSelectable(card: PhotoCardKey, openMonths: Set<Int> = OPEN_PHOTO_MONTHS): Boolean =
    if (card.isKind) card in PhotoCardKey.KINDS else card.month in openMonths && photoCardFor(card) != null

/** 화면이 고를 수 있는 카드 전부 — 열린 달이 앞, 종류가 뒤다. 칸 순서와 같다. */
fun photoSelectableCards(openMonths: Set<Int> = OPEN_PHOTO_MONTHS): List<PhotoCardKey> =
    openMonths.sorted().map(PhotoCardKey::of).filter { photoCardFor(it) != null } + PhotoCardKey.KINDS

fun defaultPhotoDog(dogs: List<PhotoDog>): PhotoDog? = dogs.firstOrNull { it.isPrimary } ?: dogs.firstOrNull()

/**
 * 이름을 문장에 이어 붙일 때 쓰는 「은/는」 자리.
 *
 * 마지막 글자가 한글이면 받침 유무로 가른다(한글 완성형 범위, 코드 - 0xAC00 을 28로 나눈
 * 나머지가 종성 자리다). 영문 이름(NEO 등)은 받침을 모르니 「은(는)」으로 둘 다 적는다.
 */
fun topicName(name: String): String {
    val last = name.lastOrNull()
    val hasBatchim = last != null && last.code in 0xAC00..0xD7A3 && (last.code - 0xAC00) % 28 != 0
    return when {
        last == null || last.code !in 0xAC00..0xD7A3 -> "${name}은(는)"
        hasBatchim -> "${name}은"
        else -> "${name}는"
    }
}

/** 도감 「＋ 포토 카드 만들기」 아래 · 만들기 화면의 남은 횟수 한 줄. null 이면 안 띄운다(§9.2). */
fun photoRemainingText(remaining: Int?): String? = when (remaining) {
    null -> null
    0 -> "오늘은 다 만들었어요 · 내일 다시 만들 수 있어요"
    else -> "오늘 ${remaining}번 남았어요"
}

/**
 * 강아지가 바뀌거나 처음 화면을 열 때 고를 카드.
 *
 * `preferred` 를 고를 수 있고 그 강아지에게 아직 없으면 그대로 쓴다. 막혔으면 **고를 수 있는
 * 카드 중 안 막힌 첫 장**(달이 앞, 종류가 뒤)으로 넘어간다. 다 막혔으면 null — 이때는
 * 고르던 카드를 그대로 두고 제출을 막는다(§9.2).
 *
 * 달이 다 찼어도 딸기·상추가 남아 있으면 그리로 넘어간다 — 한도가 카드 종류마다라
 * 「12달을 다 모았으니 이제 못 만든다」 가 아니다 (D-085).
 */
fun choosePhotoCard(
    preferred: PhotoCardKey,
    taken: Set<PhotoCardKey>,
    openMonths: Set<Int> = OPEN_PHOTO_MONTHS,
): PhotoCardKey? =
    if (photoCardSelectable(preferred, openMonths) && preferred !in taken) preferred
    else photoSelectableCards(openMonths).firstOrNull { it !in taken }

/** 달 칸 한 줄에 몇 칸. 넷이면 12달이 달력처럼 세 줄이고, 360dp 폭에서도 「12월」이 한 줄에 들어간다. */
const val PHOTO_MONTH_COLUMNS = 4

/**
 * 만들기 화면의 달 칸을 줄로 나눈다. 12달을 한 줄에 늘어놓으면 화면 밖으로 밀려서 격자로 둔다.
 * 카탈로그에 없는 달은 칸이 안 생긴다 — 이름을 안 보여줘도 열 수 없는 달까지 칸으로 뜨면 안 된다.
 *
 * **종류 카드는 이 격자에 안 넣는다** — 딸기·상추는 달력이 아니라서 넷씩 끊는 줄에 섞이면
 * 「12월 · 딸기」 가 한 줄에 서고 달 격자의 달력 모양이 깨진다. 아래 제 줄에 따로 둔다.
 */
fun photoMonthRows(open: Set<Int>, columns: Int = PHOTO_MONTH_COLUMNS): List<List<Int>> =
    open.filter { photoCardFor(PhotoCardKey.of(it)) != null }.sorted().chunked(columns)

/**
 * 포토 카드 만들기 — 달 · 아이 · 사진 한 장.
 *
 * **사진을 기기에서 긴 변 1600 JPEG 로 줄여 보낸다** (`Photo.prepare`). 서버도 어차피 그만큼
 * 줄이므로 20MB 원본을 올릴 이유가 없다. 원형 틀에 맞추기(누끼 뽑기)는 없다 — 서버가
 * 사진 전체를 보고 강아지를 찾는다.
 */
@Composable
fun PhotoCardMakeScreen(
    startCard: PhotoCardKey?,
    dogs: List<PhotoDog>,
    busy: Boolean,
    error: String?,
    /** 방금 보낸 카드. null 이면 아직 안 보냈다 */
    watching: PhotoCard?,
    /** 그 카드의 받아 둔 그림 */
    watchingFile: File?,
    /** 목록의 `daily_remaining`. null 이면(배포 전·무제한) 안 띄우고 안 막는다(§9.2) */
    remaining: Int? = null,
    /** 고른 강아지가 이미 가진(ready·generating) 카드들 — 화면은 홀더를 모르고 이 함수만 부른다 */
    takenCards: (dogId: String) -> Set<PhotoCardKey> = { emptySet() },
    onSubmit: (card: PhotoCardKey, dog: PhotoDog, jpeg: ByteArray, titleName: String?) -> Unit,
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
    var card by remember { mutableStateOf(defaultPhotoCard(startCard, LocalDate.now().monthValue)) }
    var dog by remember(dogs) { mutableStateOf(defaultPhotoDog(dogs)) }
    var picked by remember { mutableStateOf<PreparedPhoto?>(null) }
    var reading by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }
    var titleName by remember { mutableStateOf("") }
    val taken = dog?.let { takenCards(it.id) } ?: emptySet()

    // 강아지가 바뀌면(처음 고른 것 포함) 그 강아지에게 안 막힌 카드로 다시 고른다.
    // **`taken` 도 키에 넣는다** — 화면이 열릴 때 `cards` 가 아직 안 와서 `taken` 이 비어
    // 있다가 목록이 늦게 도착하면, `dog?.id` 만 키면 다시 안 돌아 막힌 카드가 기본으로 남는다.
    // **지금 고른 카드가 막혔을 때만** 바꾼다 — 사용자가 손으로 고른 안 막힌 카드는 그대로 둔다
    // (막힌 칸은 어차피 못 누르니, 남은 경우는 다 서버가 알려 준 뒤 자동으로 고른 카드뿐이다).
    // 안 막힌 카드가 하나도 없으면(null) `card` 는 그대로 두고 제출을 막는다.
    LaunchedEffect(dog?.id, taken) {
        if (card in taken) {
            choosePhotoCard(card, taken)?.let { card = it }
        }
    }

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
                card = card,
                onCard = { card = it },
                dogs = dogs,
                dog = dog,
                onDog = { dog = it },
                preview = picked?.thumbnail?.asImageBitmap(),
                busy = busy || reading,
                error = error ?: pickError,
                remainingText = photoRemainingText(remaining),
                taken = taken,
                dogName = dog?.name,
                titleName = titleName,
                onTitleName = { titleName = it.take(20) },
                onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSubmit = {
                    val chosen = dog
                    val photo = picked
                    if (chosen != null && photo != null) {
                        onSubmit(card, chosen, photo.jpeg, titleName.trim().ifEmpty { null })
                    }
                },
                onCancel = onCancel,
                submitEnabled = picked != null && dog != null && remaining != 0 && card !in taken,
            )
        }
        PhotoMakeStage.Drawing -> {
            val made = watching!!
            BackHandler { onWaitElsewhere() }
            PhotoDrawingBody(dogName = made.dogName, card = made.key, onWaitElsewhere = onWaitElsewhere)
        }
        PhotoMakeStage.Reveal -> {
            val made = watching!!
            val dex = photoCardFor(made.key)
            BackHandler { onOpenDex() }
            if (dex != null && watchingFile != null) {
                PhotoRevealFlow(dex, made, watchingFile, onRevealed = onRevealed, onOpenDex = onOpenDex)
            } else if (dex == null) {
                // 모르는 카드면(카탈로그에 없는 달·앱이 모르는 종류) 빈 화면에 갇힌다 — 도감으로 보낸다.
                LaunchedEffect(made.id) { onOpenDex() }
            }
        }
        PhotoMakeStage.Failed -> {
            val made = watching!!
            BackHandler { onRetry(made); onCancel() }
            PhotoFailedBody(
                text = photoFailureText(made.key, made.errorCode),
                onRetry = { onRetry(made) },
                onCancel = { onRetry(made); onCancel() },
            )
        }
    }
}

@Composable
private fun PhotoCardMakeContent(
    card: PhotoCardKey,
    onCard: (PhotoCardKey) -> Unit,
    dogs: List<PhotoDog>,
    dog: PhotoDog?,
    onDog: (PhotoDog) -> Unit,
    preview: ImageBitmap?,
    busy: Boolean,
    error: String?,
    /** 오늘 남은 횟수 한 줄. null 이면 자리를 차지하지 않는다(§9.2) */
    remainingText: String?,
    /** 고른 강아지가 이미 가진(ready·generating) 카드 — 그 칸을 흐리게 막는다 */
    taken: Set<PhotoCardKey>,
    dogName: String?,
    titleName: String,
    onTitleName: (String) -> Unit,
    onPick: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    submitEnabled: Boolean,
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
        Text("사진 한 장으로 그 카드를 그려 드려요. 1분쯤 걸려요.", color = TextMuted, fontSize = 13.sp)
        remainingText?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = if (it.startsWith("오늘은 다 만들었어요")) TextDark else TextMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text("어느 카드로 만들까요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        // 칸에는 달만 적는다(사용자 결정, 2026-09-15). 12달이라 한 줄 대신 달력처럼 넷씩 세 줄.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            photoMonthRows(OPEN_PHOTO_MONTHS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val key = PhotoCardKey.of(m)
                        Choice(
                            key.label,
                            on = key == card,
                            enabled = key !in taken,
                            modifier = Modifier.weight(1f),
                        ) { onCard(key) }
                    }
                    // 덜 찬 줄도 칸 폭이 같게 빈자리를 채운다.
                    repeat(PHOTO_MONTH_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // 종류 카드는 달 격자 아래 제 줄에 둔다 (#593, D-085). **달 칸과 폭을 맞춘다** —
            // 같은 격자의 이어지는 줄처럼 보여야 「또 다른 고를 거리」로 읽힌다.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoCardKey.KINDS.forEach { key ->
                    Choice(
                        key.label,
                        on = key == card,
                        enabled = key !in taken,
                        modifier = Modifier.weight(1f),
                    ) { onCard(key) }
                }
                repeat(PHOTO_MONTH_COLUMNS - PhotoCardKey.KINDS.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // 막힌 카드가 있으면 이유를 한 줄로 — 강아지마다 카드 종류당 한 장(§9.2, D-085).
        // **화면에 칸이 있는 것만 센다** — 앱이 모르는 카드를 서버가 주면 「이미 ??? 카드가
        // 있어요」 가 되는데, 그 칸은 화면에 없으니 막혔다고 말할 것도 없다.
        val blocked = taken.filter { photoCardSelectable(it) }
        if (blocked.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${topicName(dogName.orEmpty())} 이미 ${blocked.labelList()} 카드가 있어요",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("어느 아이인가요?", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dogs.forEach { d -> Choice(d.name, on = d.id == dog?.id) { onDog(d) } }
        }

        Spacer(Modifier.height(20.dp))
        Text("카드에 적힐 이름 (선택)", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titleName,
            onValueChange = { onTitleName(it.take(20)) },
            placeholder = { Text(dogName.orEmpty()) },
            supportingText = { Text("영어 대문자를 추천해요 (예: NEO)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

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
                // 자르지 않고 칸 안에 다 보인다 — 서버는 사진 전체를 보고 강아지를 찾으므로, 잘라 보여주면
                // 보내는 사진과 사용자가 본 사진이 달라진다. 남는 자리는 칸 색(CardWhite)으로 둔다.
                Image(preview, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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
                enabled = submitEnabled,
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
private fun Choice(
    label: String,
    on: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = when {
            !enabled -> TextMuted
            on -> CardWhite
            else -> TextDark
        },
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        // 격자 칸처럼 폭이 넓어지면 글자를 가운데에 둔다. 글자 폭만큼인 칸(아이 이름)은 그대로다.
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> PinkFaint
                    on -> DaengPink
                    else -> CardWhite
                },
            )
            // 막힌 달은 눌러도 아무 일 없다(§9.2) — `clickable` 을 아예 빼면 리플이 없어져
            // "이 칸은 못 누른다" 는 걸 보여 주는데, `enabled = false` 로 두면 그게 그대로 된다.
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun PhotoCardMakeContentPreview() {
    PhotoCardMakeContent(
        card = PhotoCardKey.of(9), onCard = {},
        dogs = listOf(PhotoDog("a", "콩이", true), PhotoDog("b", "보리", false)),
        dog = PhotoDog("a", "콩이", true), onDog = {},
        preview = null, busy = false,
        error = "오늘은 카드를 더 만들 수 없어요. 내일 다시 시도해 주세요.",
        remainingText = photoRemainingText(2),
        taken = emptySet(),
        dogName = "콩이", titleName = "", onTitleName = {},
        onPick = {}, onSubmit = {}, onCancel = {},
        submitEnabled = false,
    )
}

/**
 * 종류 카드를 고른 모습 — 달 격자 아래 줄에서 「딸기」가 켜져 있다.
 * 종류 칸이 달 칸과 폭이 같은지, 넷 중 둘만 차서 왼쪽으로 붙는지를 본다.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 840)
@Composable
private fun PhotoCardMakeContentKindPreview() {
    PhotoCardMakeContent(
        card = PhotoCardKey.Strawberry, onCard = {},
        dogs = listOf(PhotoDog("a", "콩이", true)),
        dog = PhotoDog("a", "콩이", true), onDog = {},
        preview = null, busy = false,
        error = null,
        remainingText = photoRemainingText(1),
        taken = emptySet(),
        dogName = "콩이", titleName = "", onTitleName = {},
        onPick = {}, onSubmit = {}, onCancel = {},
        submitEnabled = true,
    )
}

/**
 * 강아지에게 이미 4·9·12월과 상추 카드가 있고 오늘 남은 횟수도 0인 경우 — 격자의 세 칸과
 * 종류 한 칸, 만들기 버튼이 함께 막힌다. **막힌 줄이 「4월·9월·12월·상추」로 달 먼저 선다.**
 * 360dp 는 작은 폰 폭이다 — 한 줄 넷 칸에 「12월」이 줄바꿈 없이 들어가는지 본다.
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 880)
@Composable
private fun PhotoCardMakeContentBlockedPreview() {
    PhotoCardMakeContent(
        card = PhotoCardKey.of(5), onCard = {},
        dogs = listOf(PhotoDog("a", "안녕", true), PhotoDog("b", "보리", false)),
        dog = PhotoDog("a", "안녕", true), onDog = {},
        preview = null, busy = false,
        error = null,
        remainingText = photoRemainingText(0),
        taken = setOf(PhotoCardKey.of(4), PhotoCardKey.of(9), PhotoCardKey.of(12), PhotoCardKey.Lettuce),
        dogName = "안녕", titleName = "", onTitleName = {},
        onPick = {}, onSubmit = {}, onCancel = {},
        submitEnabled = false,
    )
}

/** 서버가 그리는 동안. 뒷면이 살아 있고, 나가도 된다는 걸 말해 준다. */
@Composable
private fun PhotoDrawingBody(dogName: String, card: PhotoCardKey, onWaitElsewhere: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CreamBg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        PhotoCardBack(Modifier.fillMaxWidth(0.62f))
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.62f), color = DaengPink, trackColor = PinkFaint)
        Spacer(Modifier.height(16.dp))
        Text("${dogName}의 ${card.label} 카드를 그리는 중이에요", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
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
    PhotoDrawingBody("안녕", PhotoCardKey.Strawberry) {}
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PhotoFailedBodyPreview() {
    PhotoFailedBody("9월 카드를 만들지 못했어요 · 잠시 뒤 다시 만들어 주세요", {}, {})
}
