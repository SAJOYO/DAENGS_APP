package com.daengs.app.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.pet.PetDraft
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate
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
    onSubmit: (PetDraft) -> Unit,
    onCancel: (() -> Unit)?,
    busy: Boolean,
    error: String?,
    initial: Pet? = null,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var breed by remember { mutableStateOf(initial?.breed ?: DogBreed.ALL.first().id) }
    var sex by remember { mutableStateOf(initial?.sex) }
    var neutered by remember { mutableStateOf(initial?.neutered) }
    var weight by remember { mutableStateOf(initial?.weightKg?.let { trimZero(it) }.orEmpty()) }
    var dateText by remember { mutableStateOf(initial?.birthDate?.toString().orEmpty()) }
    var dateKind by remember {
        mutableStateOf(initial?.birthDateKind ?: Pet.BirthDateKind.BIRTHDAY)
    }

    val parsedDate = remember(dateText) { parseDate(dateText) }
    val dateBad = dateText.isNotBlank() && parsedDate == null

    val draft = PetDraft(
        name = name,
        breed = breed,
        sex = sex,
        neutered = neutered,
        weightKg = weight.toFloatOrNull(),
        birthDate = parsedDate,
        // 날짜를 안 넣었으면 종류도 안 보낸다 — 서버가 "같이 있거나 같이 없어야
        // 한다"로 막고, 여기서 맞춰야 422 를 안 받는다.
        birthDateKind = parsedDate?.let { dateKind },
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

        Spacer(Modifier.height(22.dp))
        FieldLabel("이름")
        TextInput(name, { name = it }, "네옹")

        Spacer(Modifier.height(18.dp))
        FieldLabel("견종")
        BreedRow(breed) { breed = it }

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
        TextInput(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, "4.2", KeyboardType.Decimal)

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
        TextInput(dateText, { dateText = it }, "2023-05-14", KeyboardType.Number)
        if (dateBad) {
            Spacer(Modifier.height(6.dp))
            Text("2023-05-14 처럼 적어 주세요.", color = DaengsColors.Error, fontSize = 12.sp)
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = DaengsColors.Error, fontSize = 13.sp, lineHeight = 19.sp)
        }

        Spacer(Modifier.height(26.dp))
        SubmitButton(
            label = if (initial == null) "등록하기" else "저장하기",
            // 날짜를 잘못 적었으면 못 보낸다. 그대로 보내면 날짜만 조용히 빠진다.
            enabled = draft.valid && !dateBad && !busy,
            busy = busy,
        ) { onSubmit(draft) }

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

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboard: KeyboardType = KeyboardType.Text,
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
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextDark, fontSize = 14.sp),
            cursorBrush = SolidColor(DaengPink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
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

/**
 * 견종 고르기.
 *
 * **믹스가 맨 앞이다.** 27종에서 자기 개를 못 찾은 사람이 아무거나 고르면 그 값은
 * 데이터로 못 쓴다. 얼굴 그림이 없어서 발자국으로 대신한다 — 그림이 오면
 * [DogBreed] 에 넣고 이 특별 취급을 지우면 된다.
 */
@Composable
private fun BreedRow(current: String, onPick: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BreedFace(picked = current == MIX_BREED, onClick = { onPick(MIX_BREED) }) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(PinkFaint),
                contentAlignment = Alignment.Center,
            ) { Text("믹스", color = TextMuted, fontSize = 11.sp) }
        }
        DogBreed.ALL.forEach { b ->
            BreedFace(picked = current == b.id, onClick = { onPick(b.id) }) {
                DogAvatar(b, Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun BreedFace(picked: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (picked) DaengsColors.BrandPrimarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) { content() }
}

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

@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun PetFormNewPreview() {
    DaengsTheme { PetFormScreen(onSubmit = {}, onCancel = null, busy = false, error = null) }
}

@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun PetFormErrorPreview() {
    DaengsTheme {
        PetFormScreen(
            onSubmit = {}, onCancel = {}, busy = false,
            error = "강아지는 5마리까지 등록할 수 있습니다.",
        )
    }
}
