package com.daengs.app.ui.storage

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.ExtractionStatus
import com.daengs.app.care.MAX_HOSPITAL_ADDRESS
import com.daengs.app.care.MAX_HOSPITAL_NAME
import com.daengs.app.care.MAX_REASON_DETAIL
import com.daengs.app.care.MAX_TOTAL_KRW
import com.daengs.app.care.ReceiptEdits
import com.daengs.app.care.ReceiptItem
import com.daengs.app.care.ReceiptStep
import com.daengs.app.care.UnreadableReason
import com.daengs.app.care.VetReasonOption
import com.daengs.app.care.VetVisitDraft
import com.daengs.app.care.phoneLooksValid
import com.daengs.app.chat.ChatApiError
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.common.DateWheel
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * 영수증을 읽은 결과를 **사람이 확정하는** 화면. 기계가 채우고 사람이 고친다.
 *
 * ⚠️ **`no_amount` 에서 화면을 비우지 않는다.** `extraction_status="unreadable"` +
 *    `unreadable_reason="no_amount"` 는 "못 읽었다" 가 아니라 **"읽었는데 합계 줄이
 *    없다"** 다 — 병원·주소·전화·날짜·항목이 다 실려 온다. 사진 아래가 잘리는 것이 이
 *    기능에서 제일 흔한 실패라, 그때 빈 폼을 주면 거의 다 읽은 영수증을 통째로 버린다.
 *    `blurry`·`not_a_receipt` 는 정말로 비어 있다.
 *
 * ⚠️ **제안을 자동 수용하지 않는다.** `suggestedReasonCode` 는 믿을 만하지 않다 — 실제
 *    영수증 하나로 3회 돌렸을 때 `vaccination` 1 / `skin` 2 가 나왔다(정답은
 *    `vaccination`). 미리 골라 두기만 하고, `null` 이면 아무것도 안 고르며 유저가 고르기
 *    전까지 [확인] 이 잠긴다.
 *
 * ⚠️ **병원 전화는 `tel:` 이 된다.** OCR 이 숫자를 뒤집으면 모르는 사람에게 전화가 걸린다 —
 *    금액 오류보다 조용히 틀리는 실패라 서버와 같은 모양([phoneLooksValid])으로 막는다.
 *
 * ⚠️ **`isOncology` 는 유저만 켠다.** 영수증에 찍힌 글자가 아니라 임상 판단이라 저쪽
 *    추출 스키마에 칸 자체가 없다 — 여기서도 기계가 미리 켜 주지 않는다.
 *
 * 영수증 그림은 **방금 찍은 로컬 비트맵**이다. 응답의 `receipt_image_url` 을 안 쓴다 —
 * 그 그림이 이미 우리 손에 있어서 받아 올 이유가 없다.
 *
 * **사유를 접힌 드롭다운이 아니라 칩으로 편다.** 제안이 못 믿을 값이라 유저가 실제로
 * 다시 고르는 것이 이 화면의 목적인데, 접어 두면 "이미 골라져 있다" 로 읽혀 그냥
 * 넘어간다. 칩이면 대안이 눈에 보이고 한 번에 눌린다. 집에서 쓰던 `FlowRow` + 칩
 * (`PetFormScreen` 의 견종 고르기) 과 같은 모양이라 새 관용구도 아니다.
 */
@Composable
fun ReceiptConfirmScreen(
    photo: Bitmap?,
    draft: VetVisitDraft?,
    options: List<VetReasonOption>,
    step: ReceiptStep,
    error: ChatApiError?,
    modifier: Modifier = Modifier,
    onConfirm: (ReceiptEdits) -> Unit = {},
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("진료비 확인", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DaengsTextAction("닫기", onDismiss, tint = TextMuted)
        }

        photo?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "찍은 영수증",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        when {
            // 초안이 아직 없다 — 올리는 중이거나 읽는 중이거나, 거기서 끊겼다.
            draft == null && error != null -> ReceiptNotice(error.message ?: READ_FAILED, DaengsColors.Error) {
                DaengsTextAction("다시 시도", onRetry)
            }
            draft == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = DaengPink, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("영수증을 읽고 있어요", color = TextMuted, fontSize = 14.sp)
            }
            else -> ReceiptForm(draft, options, step, error, onConfirm, onRetry, today)
        }
    }
}

@Composable
private fun ReceiptForm(
    draft: VetVisitDraft,
    options: List<VetReasonOption>,
    step: ReceiptStep,
    error: ChatApiError?,
    onConfirm: (ReceiptEdits) -> Unit,
    onRetry: () -> Unit,
    today: LocalDate,
) {
    // 초안이 바뀌면(재추출) 다시 채운다. 유저가 고치는 동안에는 안 건드린다.
    var visitedOn by remember(draft) { mutableStateOf(draft.visitedOn ?: today) }
    var total by remember(draft) { mutableStateOf(draft.totalKrw?.toString().orEmpty()) }
    var name by remember(draft) { mutableStateOf(draft.hospitalName.orEmpty()) }
    var address by remember(draft) { mutableStateOf(draft.hospitalAddress.orEmpty()) }
    var phone by remember(draft) { mutableStateOf(draft.hospitalPhone.orEmpty()) }
    var detail by remember(draft) { mutableStateOf("") }
    var emergency by remember(draft) { mutableStateOf(draft.isEmergency) }
    // **미리 켜 주지 않는다.** 영수증의 글자가 아니라 임상 판단이라 유저만 켠다.
    var oncology by remember(draft) { mutableStateOf(false) }
    // 제안은 미리 골라 두되, 목록에 없는 코드가 오면 아무것도 안 고른다.
    var reason by remember(draft) {
        mutableStateOf(draft.suggestedReasonCode?.takeIf { code -> options.any { it.code == code } })
    }

    val phoneOk = phoneLooksValid(phone)
    val amount = total.toIntOrNull()
    val valid = reason != null && amount != null && amount in 0..MAX_TOTAL_KRW && phoneOk

    draft.noticeText()?.let { ReceiptNotice(it, TextMuted) }
    if (draft.possibleDuplicate) {
        ReceiptNotice("같은 날 같은 금액의 기록이 이미 있어요.", DaengsColors.Error)
    }
    // 초안은 받았는데 확정이 실패했다. **여기는 재추출이 아니라 [확인] 을 다시 누르는
    // 자리다** — 재추출하면 아래에서 고친 값이 초안 미리 채움으로 되돌아간다.
    if (error != null) ReceiptNotice(error.message ?: SAVE_FAILED, DaengsColors.Error)

    FieldLabel("방문 날짜")
    DateWheel(visitedOn, { visitedOn = it })

    FieldLabel("총액")
    TextInput(total, { total = it.filter(Char::isDigit).take(9) }, "숫자만", KeyboardType.Number, "총액")

    FieldLabel("병원")
    TextInput(name, { name = it }, "병원 이름", label = "병원 이름", maxLength = MAX_HOSPITAL_NAME)
    TextInput(address, { address = it }, "주소", label = "병원 주소", maxLength = MAX_HOSPITAL_ADDRESS)
    TextInput(phone, { phone = it }, "02-000-0000", KeyboardType.Phone, "병원 전화", maxLength = 32)
    if (!phoneOk) Text("전화번호 모양이 올바르지 않아요.", color = DaengsColors.Error, fontSize = 13.sp)

    FieldLabel("무엇 때문에 갔나")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Chip(option.label, option.code == reason) { reason = option.code }
        }
    }
    TextInput(detail, { detail = it }, "한 줄 메모 (선택)", label = "메모", maxLength = MAX_REASON_DETAIL)

    ToggleRow("야간·응급·공휴일이었어요", emergency) { emergency = !emergency }
    ToggleRow("종양 진료였어요", oncology) { oncology = !oncology }

    if (draft.items.isNotEmpty()) {
        FieldLabel("영수증에서 읽은 항목")
        ReceiptItems(draft.items)
    }

    Spacer(Modifier.height(2.dp))
    DaengsWideButton(
        label = "확인",
        onClick = {
            onConfirm(
                ReceiptEdits(
                    reasonCode = reason ?: return@DaengsWideButton,
                    reasonDetail = detail.blankToNull(),
                    visitedOn = visitedOn,
                    totalKrw = amount ?: return@DaengsWideButton,
                    hospitalName = name.blankToNull(),
                    hospitalAddress = address.blankToNull(),
                    hospitalPhone = phone.blankToNull(),
                    isEmergency = emergency,
                    isOncology = oncology,
                ),
            )
        },
        enabled = valid,
        busy = step == ReceiptStep.CONFIRMING,
        accent = true,
    )
}

/** 못 읽은 이유를 유저의 말로. **`no_amount` 만 "총액을 적어 달라" 다.** */
private fun VetVisitDraft.noticeText(): String? = when {
    status == ExtractionStatus.FAILED -> "영수증을 읽지 못했어요. 손으로 적거나 다시 시도해 주세요."
    unreadableReason == UnreadableReason.NO_AMOUNT -> "합계 줄을 못 읽었어요. 총액만 적어 주세요."
    unreadableReason == UnreadableReason.BLURRY -> "사진이 흐려서 못 읽었어요. 손으로 적거나 다시 찍어 주세요."
    unreadableReason == UnreadableReason.NOT_A_RECEIPT -> "영수증이 아닌 것 같아요. 손으로 적거나 다시 찍어 주세요."
    else -> null
}

@Composable
private fun ReceiptNotice(
    message: String,
    tint: androidx.compose.ui.graphics.Color,
    action: @Composable (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(message, color = tint, fontSize = 13.sp)
        action?.invoke()
    }
}

@Composable
private fun ReceiptItems(items: List<ReceiptItem>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = TextDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(wonOf(item.amountKrw), color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * `PetFormScreen` 의 것과 같은 칸이다. **[label] 은 읽어 주는 쪽에만 붙는다** — 화면에
 * 두 번 쓰지 않고, 이게 없으면 테스트가 자리표시자 글자를 칸으로 착각한다.
 */
@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboard: KeyboardType = KeyboardType.Text,
    label: String? = null,
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
                .then(if (label == null) Modifier else Modifier.semantics { contentDescription = label }),
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

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) PinkSoft else CardWhite)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (on) TextDark else TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (on) "켜짐" else "꺼짐", color = if (on) DaengPink else TextMuted, fontSize = 13.sp)
    }
}

private val WON = NumberFormat.getIntegerInstance(Locale.KOREA)

private fun wonOf(amount: Int): String = "${WON.format(amount)}원"

private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

private const val READ_FAILED = "영수증을 읽지 못했어요. 잠시 뒤 다시 시도해 주세요."
private const val SAVE_FAILED = "기록을 저장하지 못했어요. 다시 시도해 주세요."

// -- 미리보기 ---------------------------------------------------------------

private val PREVIEW_OPTIONS = listOf(
    VetReasonOption("skin", "피부"),
    VetReasonOption("vaccination", "예방접종"),
    VetReasonOption("ear", "귀"),
)

private fun previewDraft() = VetVisitDraft(
    draftId = "d1", petId = "p1", status = ExtractionStatus.OK, unreadableReason = null,
    visitedOn = LocalDate.of(2026, 9, 10), totalKrw = 61_700,
    hospitalName = "압구정동물병원", hospitalAddress = "서울 강남구 압구정로",
    hospitalPhone = "02-543-0075",
    items = listOf(ReceiptItem("초진료", 5_500), ReceiptItem("주사-비오칸엠", 46_200)),
    suggestedReasonCode = "vaccination", isEmergency = false, possibleDuplicate = false,
    reasonOptions = PREVIEW_OPTIONS,
)

@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun ReceiptConfirmReadyPreview() = DaengsTheme {
    ReceiptConfirmScreen(
        photo = null, draft = previewDraft(), options = PREVIEW_OPTIONS,
        step = ReceiptStep.READY, error = null, today = LocalDate.of(2026, 9, 10),
    )
}

/** 사진 아래가 잘린 영수증. **총액 한 칸만 비고 나머지는 채워져 있어야 한다.** */
@Preview(widthDp = 411, heightDp = 900, showBackground = true)
@Composable
private fun ReceiptConfirmNoAmountPreview() = DaengsTheme {
    ReceiptConfirmScreen(
        photo = null,
        draft = previewDraft().copy(
            status = ExtractionStatus.UNREADABLE,
            unreadableReason = UnreadableReason.NO_AMOUNT,
            totalKrw = null,
            possibleDuplicate = true,
        ),
        options = PREVIEW_OPTIONS,
        step = ReceiptStep.READY, error = null, today = LocalDate.of(2026, 9, 10),
    )
}

@Preview(widthDp = 411, showBackground = true)
@Composable
private fun ReceiptConfirmReadingPreview() = DaengsTheme {
    ReceiptConfirmScreen(
        photo = null, draft = null, options = emptyList(),
        step = ReceiptStep.EXTRACTING, error = null,
    )
}
