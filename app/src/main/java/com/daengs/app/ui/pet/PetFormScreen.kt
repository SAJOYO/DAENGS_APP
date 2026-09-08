package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.common.DateWheel
import com.daengs.app.ui.common.TimeWheel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetWeight
import com.daengs.app.pet.PetDraft
import com.daengs.app.ui.DogAvatar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.my.PrivacyPolicyLink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * 강아지 하나를 등록하거나 고치는 화면.
 *
 * **등록과 수정이 같은 화면이다.** 서버가 PUT 으로 전체를 다시 받기 때문이고
 * (부분 수정은 null 의 뜻이 갈린다), 화면을 둘로 나누면 항목 하나 늘 때마다
 * 두 군데를 고쳐야 한다.
 *
 * ## 모르는 것을 받는 방식
 *
 * 성별·중성화·생일은 **안 고르면 "모름"** 이다. 필수로 하면 모르는 사람이 아무
 * 값이나 넣고, 그러면 그 값은 데이터로 못 쓴다 — 유기견을 데려온 경우 성별 말고는
 * 모르는 게 흔하다. 그래서 칩에 "모름" 자리를 따로 두지 않고 **누른 것을 다시
 * 누르면 꺼지게** 했다. 고른 적 없음과 모름이 같은 상태다.
 *
 * @param initial 고칠 강아지. null 이면 새로 등록하는 것이다.
 */
@Composable
fun PetFormScreen(
    /**
     * 보낸다. 사진은 **[PetDraft] 에 안 넣는다** — 그건 서버에 보내는 값이고 서버에는
     * 아직 사진 자리가 없다. 새로 등록할 때는 id 가 서버에서 오므로, 부르는 쪽이
     * 등록이 끝난 뒤에 이 비트맵을 파일로 쓴다.
     */
    onSubmit: (PetDraft, Bitmap?) -> Unit,
    onCancel: (() -> Unit)?,
    busy: Boolean,
    error: String?,
    initial: Pet? = null,
    /** 이미 올려 둔 사진. 고치기로 들어왔을 때 보여 준다. */
    photo: ImageBitmap? = null,
    /** 사진을 지우고 견종 그림으로 되돌린다. null 이면 그 줄이 안 뜬다. */
    onClearPhoto: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 고른 사진. **아직 파일이 아니다** — 새로 등록할 때는 저장할 id 가 없어서,
    // 보내고 나서 부르는 쪽이 쓴다.
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        // **폼 위에 덮는다.** 화면을 하나 더 만들면 등록 도중에 뒤로 가기가 꼬인다.
        PetPhotoPicker { made ->
            picking = false
            if (made != null) picked = made
        }
        return
    }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var breed by remember { mutableStateOf(initial?.breed ?: DogBreed.ALL.first().id) }
    var sex by remember { mutableStateOf(initial?.sex) }
    var neutered by remember { mutableStateOf(initial?.neutered) }
    var weight by remember { mutableStateOf(initial?.weightKg?.let { trimZero(it) }.orEmpty()) }
    // **날짜는 고르는 것이지 쓰는 것이 아니다.** 예전에는 `2023-05-14` 로 적게 했는데,
    // 하이픈 자리를 틀리거나 자판을 숫자로 바꾸는 것부터가 번거로웠다.
    //
    // **모름을 남겨 둔다.** 필수로 하면 모르는 사람이 아무 날이나 넣는다 — 유기견을
    // 데려온 경우가 그렇다. 그래서 켜야 다이얼이 나온다.
    var dateOn by remember { mutableStateOf(initial?.birthDate != null) }
    var day by remember { mutableStateOf(initial?.birthDate ?: LocalDate.now()) }
    var dateKind by remember {
        mutableStateOf(initial?.birthDateKind ?: Pet.BirthDateKind.BIRTHDAY)
    }

    // 돌봄 (#200). 급식 시각은 시간제일 때만 서버로 간다 — 자율급식으로 바꿔도 목록은
    // 들고 있다가 다시 시간제로 돌아오면 그대로 보여 준다. 실수로 바꿨다가 되돌릴 때
    // 시각을 다시 다 넣게 하면 안 된다.
    var feedingStyle by remember { mutableStateOf(initial?.feedingStyle) }
    var feedingTimes by remember { mutableStateOf(initial?.feedingTimes.orEmpty()) }
    var healthConditions by remember { mutableStateOf(initial?.healthConditions.orEmpty()) }
    var medications by remember { mutableStateOf(initial?.medications.orEmpty()) }

    val parsedDate = day.takeIf { dateOn }
    val dateBad = false
    // 몸무게는 비워 둘 수 있는 칸이라 "빈 칸"과 "잘못 쓴 값"을 가른다 ([PetWeight]).
    val weightError = PetWeight.errorOf(weight)

    val draft = PetDraft(
        name = name,
        breed = breed,
        sex = sex,
        neutered = neutered,
        weightKg = weight.toFloatOrNull(),
        birthDate = parsedDate,
        // ⚠️ **고치는 화면이 배웅한 날을 지우면 안 된다.** 서버가 PUT 이라 안 실으면
        // null 로 덮인다 — 몸무게 한 번 고쳤다고 그 날이 사라지면 안 된다.
        farewellOn = initial?.farewellOn,
        // 날짜를 안 넣었으면 종류도 안 보낸다 — 서버가 "같이 있거나 같이 없어야
        // 한다"로 막고, 여기서 맞춰야 422 를 안 받는다.
        birthDateKind = parsedDate?.let { dateKind },
        feedingStyle = feedingStyle,
        // 시간제가 아니면 안 보낸다. 서버가 "시각은 시간제일 때만" 으로 422 를 준다.
        feedingTimes = feedingTimes.takeIf { feedingStyle == Pet.FeedingStyle.SCHEDULED && it.isNotEmpty() },
        healthConditions = healthConditions,
        medications = medications,
    )

    if (onCancel != null) BackHandler(enabled = !busy, onBack = onCancel)

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            if (initial == null) "강아지를 알려 주세요" else "강아지 정보 고치기",
            color = TextDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "모르는 건 비워 두셔도 돼요. 나중에 고칠 수 있어요.",
            color = TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        // **별표만 두지 않는다.** 별표가 무슨 뜻인지는 아는 사람만 안다.
        Row {
            Text("*", color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(" 는 꼭 넣어야 해요.", color = TextMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        // **사진이 맨 위다.** 이름보다 먼저 얼굴이 보여야 "내 아이를 등록하는 중" 으로
        // 읽힌다. 안 넣어도 되고, 안 넣으면 견종 그림이 그대로 쓰인다.
        PhotoRow(
            picked = picked,
            saved = photo,
            breed = DogBreed.ALL.firstOrNull { it.id == breed },
            onPick = { picking = true },
            onClear = if (picked != null) {
                { picked = null }
            } else {
                onClearPhoto
            },
        )

        Spacer(Modifier.height(22.dp))
        FieldLabel("이름", required = true)
        TextInput(name, { name = it }, "네옹", label = "이름")

        Spacer(Modifier.height(18.dp))
        FieldLabel("견종", required = true)
        BreedGrid(breed) { breed = it }

        Spacer(Modifier.height(18.dp))
        FieldLabel("성별")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("남아", sex == Pet.Sex.MALE) { sex = toggle(sex, Pet.Sex.MALE) }
            Chip("여아", sex == Pet.Sex.FEMALE) { sex = toggle(sex, Pet.Sex.FEMALE) }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("중성화")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("했어요", neutered == true) { neutered = toggle(neutered, true) }
            Chip("안 했어요", neutered == false) { neutered = toggle(neutered, false) }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("몸무게 (kg)")
        // **찍히는 것부터 막는다.** 예전에는 숫자와 점만 거르고 길이를 안 봐서 18자리가
        // 들어갔고, 저장을 누르면 저쪽 검증 오류가 JSON 그대로 화면에 찍혔다.
        TextInput(
            weight,
            { if (PetWeight.accepts(it)) weight = it },
            "4.2",
            KeyboardType.Decimal,
            label = "몸무게 (kg)",
        )
        // **이 칸의 잘못은 이 칸 아래에서 말한다.** 화면 맨 아래 서버 오류 줄에 섞으면
        // 어느 칸 이야기인지가 안 보인다.
        weightError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = DaengsColors.Error, fontSize = 12.sp)
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("생일")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("생일", dateKind == Pet.BirthDateKind.BIRTHDAY) {
                dateKind = Pet.BirthDateKind.BIRTHDAY
            }
            Chip("가족이 된 날", dateKind == Pet.BirthDateKind.FAMILY_DAY) {
                dateKind = Pet.BirthDateKind.FAMILY_DAY
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!dateOn) {
            Text(
                "+ 날짜 고르기",
                color = DaengPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { dateOn = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        } else {
            DateWheel(value = day, onChange = { day = it })
            Spacer(Modifier.height(6.dp))
            Text(
                "모르겠어요",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { dateOn = false }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("급식 방식")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("자율급식", feedingStyle == Pet.FeedingStyle.FREE) {
                feedingStyle = toggle(feedingStyle, Pet.FeedingStyle.FREE)
            }
            Chip("시간제", feedingStyle == Pet.FeedingStyle.SCHEDULED) {
                feedingStyle = toggle(feedingStyle, Pet.FeedingStyle.SCHEDULED)
            }
        }
        if (feedingStyle == Pet.FeedingStyle.SCHEDULED) {
            Spacer(Modifier.height(8.dp))
            FeedingTimes(
                times = feedingTimes,
                onAdd = { feedingTimes = (feedingTimes + it).distinct().sorted() },
                onRemove = { feedingTimes = feedingTimes - it },
            )
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("앓는 병")
        TextInput(
            healthConditions,
            { healthConditions = it },
            "예: 슬개골 탈구",
            label = "앓는 병",
            maxLength = PetDraft.CARE_TEXT_MAX,
        )

        Spacer(Modifier.height(18.dp))
        FieldLabel("먹는 약")
        TextInput(
            medications,
            { medications = it },
            "예: 관절 영양제, 심장사상충약",
            label = "먹는 약",
            maxLength = PetDraft.CARE_TEXT_MAX,
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = DaengsColors.Error, fontSize = 13.sp, lineHeight = 19.sp)
        }

        Spacer(Modifier.height(26.dp))
        SubmitButton(
            label = if (initial == null) "등록하기" else "저장하기",
            // 날짜를 잘못 적었으면 못 보낸다. 그대로 보내면 날짜만 조용히 빠진다.
            // 몸무게도 같다 — 보내 봐야 서버가 422 로 돌려보낸다.
            enabled = draft.valid && weightError == null && !dateBad && !busy,
            busy = busy,
        ) { onSubmit(draft, picked) }

        if (onCancel != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy, onClick = onCancel)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("취소", color = TextMuted, fontSize = 14.sp) }
        }
        if (initial == null) {
            // 첫 등록은 취소가 없어서 이 화면을 끝내기 전엔 My 화면에 못 간다.
            // 방침을 읽고 나서 등록할 수 있게 여기에도 둔다. 수정 화면에는 안 둔다 —
            // 거기서는 My 화면이 한 번 뒤로 가면 있다.
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrivacyPolicyLink()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 같은 값을 다시 누르면 "모름"으로 돌아간다. 고른 적 없음과 모름은 같은 상태다. */
private fun <T> toggle(current: T?, value: T): T? = if (current == value) null else value

/** `4.0` 을 "4" 로. 소수점 뒤 0 을 남기면 입력칸이 지저분하다. */
private fun trimZero(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

/** 못 읽으면 null. 화면이 그걸 보고 "이렇게 적어 주세요"를 띄운다. */
private fun parseDate(text: String): LocalDate? =
    if (text.isBlank()) null else try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * 사진 자리.
 *
 * 사진이 없으면 견종 그림이 미리 보인다 — **안 넣어도 이렇게 나온다**는 것을
 * 말로 설명하지 않고 보여 준다.
 */
@Composable
private fun PhotoRow(
    picked: Bitmap?,
    saved: ImageBitmap?,
    breed: DogBreed?,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val shown = picked?.asImageBitmap() ?: saved
    Row(verticalAlignment = Alignment.CenterVertically) {
        PetAvatar(
            photo = shown,
            breed = breed,
            size = 72.dp,
            modifier = Modifier.clickable(onClick = onPick),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                if (shown == null) "사진 올리기" else "사진 바꾸기",
                color = DaengPinkDeep,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPick)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            if (shown != null && onClear != null) {
                Text(
                    "기본 그림으로",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            } else {
                Text(
                    "안 넣으면 견종 그림으로 나와요",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * 칸 이름.
 *
 * @param required 꼭 넣어야 하는 칸. **[PetDraft.valid] 가 정하는 것과 같아야 한다** —
 *   갈리면 화면은 선택이라 하는데 저장 버튼이 안 눌린다. [REQUIRED_FIELDS] 참고
 */
@Composable
private fun FieldLabel(text: String, required: Boolean = false) {
    Row(Modifier.padding(bottom = 7.dp)) {
        Text(text, color = TextMuted, fontSize = 12.sp)
        if (required) {
            Text(" *", color = DaengPinkDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 시간제 급식의 시각 목록.
 *
 * 시각마다 칩 하나에 지우기가 붙고, 아래에 **다이얼이 접혀 있다.** 펼치면 시·분을 굴려
 * 고르고 [CONFIRM_FEEDING_TIME_LABEL] 로 넣는다 — 다이얼을 늘 펼쳐 두면 폼이 그만큼
 * 길어지고, 시각 셋을 넣는 사람보다 하나 넣는 사람이 많다.
 *
 * 열두 개가 상한이다 (저쪽 `max_length=12`). 차면 추가 줄이 사라진다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedingTimes(
    times: List<LocalTime>,
    onAdd: (LocalTime) -> Unit,
    onRemove: (LocalTime) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(LocalTime.of(8, 0)) }

    if (times.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            times.forEach { time ->
                val text = time.format(Pet.FEEDING_TIME)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PinkSoft)
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable { onRemove(time) }
                            .semantics { contentDescription = "$text 지우기" }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) { Text("✕", color = TextMuted, fontSize = 12.sp) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    when {
        picking -> {
            TimeWheel(value = pick, onChange = { pick = it })
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    CONFIRM_FEEDING_TIME_LABEL,
                    color = DaengPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onAdd(pick)
                            picking = false
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Text(
                    "닫기",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { picking = false }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        times.size < MAX_FEEDING_TIMES -> Text(
            ADD_FEEDING_TIME_LABEL,
            color = DaengPink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { picking = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 시각 추가 줄. 테스트가 이 글자로 찾는다. */
internal const val ADD_FEEDING_TIME_LABEL = "+ 시간 추가"

/** 다이얼에서 고른 시각을 목록에 넣는 줄. */
internal const val CONFIRM_FEEDING_TIME_LABEL = "이 시간 넣기"

/** 급식 시각 상한. 저쪽 `feeding_times` 의 `max_length` 와 같은 수다. */
private const val MAX_FEEDING_TIMES = 12

/**
 * 별표가 붙는 칸.
 *
 * **[PetDraft.valid] 가 요구하는 것과 같은 목록이다.** 나머지는 안 골라도 되고,
 * 그건 "모름" 이라는 뜻이다 — 필수로 하면 모르는 사람이 아무 값이나 넣고 그러면
 * 그 값은 데이터로 못 쓴다 (유기견을 데려온 경우가 그렇다).
 */
internal val REQUIRED_FIELDS = setOf("이름", "견종")

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboard: KeyboardType = KeyboardType.Text,
    /**
     * 칸의 이름. 위 [FieldLabel] 과 같은 말이다.
     *
     * **화면에 두 번 쓰지 않는다** — 읽어 주는 쪽에만 붙는다. 이게 없으면 스크린리더가
     * 이름 없는 입력 칸을 만나고, 테스트도 자리표시자 글자를 칸으로 착각한다.
     */
    label: String? = null,
    /** 글자 수 상한. 넘게 쳐도 안 들어간다 — 서버가 422 로 돌려주기 전에 여기서 막는다. */
    maxLength: Int = Int.MAX_VALUE,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CardWhite, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = TextMuted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onChange(it) },
            singleLine = true,
            textStyle = TextStyle(color = TextDark, fontSize = 14.sp),
            cursorBrush = SolidColor(DaengPink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (label == null) Modifier
                    else Modifier.semantics { contentDescription = label }
                ),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextDark else TextMuted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PinkSoft else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 고를 수 있는 견종 하나. [breed] 가 null 이면 믹스다 — 그림이 없다. */
data class BreedChoice(val id: String, val label: String, val breed: DogBreed?)

/**
 * 고를 수 있는 것들. **믹스가 맨 앞이다.**
 *
 * 27종에서 자기 개를 못 찾은 사람이 아무거나 고르면 그 값은 데이터로 못 쓴다.
 * 맨 앞에 두는 건 그래서고, 주석이 아니라 `BreedChoiceTest` 가 지킨다.
 *
 * 믹스는 얼굴 그림이 없어서 발자국으로 대신한다 — 그림이 오면 [DogBreed] 에 넣고
 * 이 특별 취급을 지우면 된다.
 */
fun breedChoices(): List<BreedChoice> =
    listOf(BreedChoice(MIX_BREED, "믹스", null)) +
        DogBreed.ALL.map { BreedChoice(it.id, it.label, it) }

/**
 * 견종 고르기.
 *
 * **이름을 같이 보여 준다.** 얼굴만 늘어놓으면 푸들 세 색(실버·연갈색·초코)이나
 * 닥스훈트 세 종류를 52dp 그림으로 구분해야 한다. 이름은 [DogBreed.label] 에 이미
 * 있었는데 이 화면만 안 쓰고 있었다.
 *
 * 가로 스크롤이 아니라 격자다. 28개를 옆으로 끌면서 찾으면 다섯 개쯤만 보인다.
 *
 * **다만 전부 펼치지는 않는다.** 일곱 줄이면 폼의 절반이 견종이 되고, 그 아래
 * 성별·몸무게·생일이 화면 밖으로 밀린다. 자리를 정해 두고 **그 안에서 스크롤**한다 —
 * 마지막 줄이 반쯤 걸치게 높이를 잡아서 "아래에 더 있다"가 보이게 한다.
 */
@Composable
private fun BreedGrid(current: String, onPick: (String) -> Unit) {
    val choices = remember { breedChoices() }
    val gridState = rememberLazyGridState()

    // 수정으로 들어오면 원래 견종이 여섯째 줄에 있을 수도 있다. 그대로 두면 첫 줄만
    // 보여서 **아무것도 안 고른 것처럼** 읽힌다.
    LaunchedEffect(Unit) {
        val index = choices.indexOfFirst { it.id == current }
        if (index > 0) gridState.scrollToItem(index / BREED_COLUMNS * BREED_COLUMNS)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(BREED_COLUMNS),
        state = gridState,
        modifier = Modifier
            .fillMaxWidth()
            .height(BREED_GRID_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .nestedScroll(KeepScrollInside),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(choices, key = { it.id }) { choice ->
            BreedCell(
                choice = choice,
                picked = current == choice.id,
                onClick = { onPick(choice.id) },
            )
        }
    }
}

/**
 * 한 칸.
 *
 * **선택 표시가 칸 전체로 간다.** 얼굴에만 분홍을 깔면 이름이 붙은 뒤로는 어디까지가
 * 고른 것인지 경계가 모호하다.
 */
@Composable
private fun BreedCell(
    choice: BreedChoice,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) DaengsColors.BrandPrimarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (choice.breed != null) {
            DogAvatar(choice.breed, Modifier.size(52.dp))
        } else {
            PawAvatar(size = 52.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            choice.label,
            color = if (picked) DaengPinkDeep else TextMuted,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 상자 안의 스크롤을 **밖으로 넘기지 않는다.**
 *
 * 기본 동작은 안쪽이 끝에 닿으면 남은 만큼을 부모가 받는다. 그러면 견종을 훑다가
 * 마지막 줄에서 손가락이 조금 더 가는 순간 **폼 전체가 따라 움직여서** 보고 있던
 * 견종이 화면 밖으로 나간다. 한 번의 드래그가 두 가지를 움직이면 지금 어느 쪽을
 * 만지는 중인지 알 수 없다.
 *
 * 남은 스크롤과 남은 관성을 여기서 다 먹는다. 폼은 상자 **밖**을 끌어서 움직인다.
 */
private val KeepScrollInside = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** 한 줄에 넷. 다섯이면 "래브라도 리트리버" 가 석 줄이 되고, 셋이면 폼이 너무 길어진다. */
private const val BREED_COLUMNS = 4

/**
 * 견종 칸의 높이.
 *
 * 두 줄이 온전히 보이고 **세 번째 줄이 살짝 걸친다.** 딱 두 줄로 끊으면 아래에 더
 * 있다는 게 안 보여서 스크롤할 생각을 못 한다 — 걸치는 그 조각이 유일한 신호다.
 *
 * 폼 전체가 한 화면에 들어가는 높이이기도 하다. 더 키우면 저장하기가 화면 밖으로 밀린다.
 */
private val BREED_GRID_HEIGHT = 186.dp

/** 서버로 보내는 믹스 견종 값. `DogBreed` 에 없는 값이라 문자열로 둔다. */
const val MIX_BREED = "mix"

@Composable
private fun SubmitButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (enabled) DaengPink else DaengPink.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.height(52.dp).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = CardWhite, strokeWidth = 2.dp)
            } else {
                Text(label, color = CardWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 사진 자리. **안 올린 모습과 올린 모습이 나란히** 있어야 줄이 안 흔들리는지 보인다. */
@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun PhotoRowPreview() {
    DaengsTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoRow(picked = null, saved = null, breed = DogBreed.BEAGLE, onPick = {}, onClear = null)
            PhotoRow(picked = null, saved = null, breed = null, onPick = {}, onClear = null)
        }
    }
}

@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun PetFormNewPreview() {
    DaengsTheme { PetFormScreen(onSubmit = { _, _ -> }, onCancel = null, busy = false, error = null) }
}

/** 시간제 급식에 시각 둘, 병·약이 찬 수정 화면. 칩 줄과 텍스트 칸이 어떻게 놓이는지 본다. */
@Preview(widthDp = 411, heightDp = 1400, showBackground = true)
@Composable
private fun PetFormCarePreview() {
    DaengsTheme {
        PetFormScreen(
            onSubmit = { _, _ -> }, onCancel = {}, busy = false, error = null,
            initial = Pet(
                id = "p1", name = "네옹", breed = DogBreed.BEAGLE.id,
                sex = Pet.Sex.FEMALE, neutered = true, weightKg = 4.2f,
                birthDate = LocalDate.of(2023, 5, 14), birthDateKind = Pet.BirthDateKind.BIRTHDAY,
                isPrimary = true,
                feedingStyle = Pet.FeedingStyle.SCHEDULED,
                feedingTimes = listOf(LocalTime.of(8, 0), LocalTime.of(19, 30)),
                healthConditions = "슬개골 탈구",
                medications = "관절 영양제",
            ),
        )
    }
}

@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun PetFormErrorPreview() {
    DaengsTheme {
        PetFormScreen(
            onSubmit = { _, _ -> }, onCancel = {}, busy = false,
            error = "강아지는 5마리까지 등록할 수 있습니다.",
        )
    }
}
