package com.daengs.app.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.daengs.app.care.SplitEdit
import com.daengs.app.care.receiptBlocks
import com.daengs.app.care.remainderKrw
import com.daengs.app.pet.Pet
import com.daengs.app.care.ReceiptItem
import com.daengs.app.care.ReceiptStep
import com.daengs.app.care.UnreadableReason
import com.daengs.app.care.VetReasonOption
import com.daengs.app.care.VetVisitDraft
import com.daengs.app.care.phoneLooksValid
import com.daengs.app.chat.ChatApiError
import com.daengs.app.screening.PreparedPhoto
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.common.KeepScrollInside
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
 * 영수증 그림은 **방금 찍은 로컬 사진**이다. 응답의 `receipt_image_url` 을 안 쓴다 —
 * 그 그림이 이미 우리 손에 있어서 받아 올 이유가 없다.
 *
 * ⚠️ **눌러서 크게 볼 수 있어야 한다.** 실물 영수증은 세로로 길어서(1170×2532 실측)
 *    작은 미리보기에 `Fit` 으로 넣으면 폭이 100dp 밖에 안 되고 글자를 못 읽는다 — 폴드
 *    673dp 에서 특히 그렇다. **기계가 채운 값을 사람이 대조하는 화면인데 대조할 원본이
 *    장식이 된다.** 그래서 미리보기는 폭을 꽉 채워 윗부분(병원·날짜가 찍힌 자리)을 보이고,
 *    누르면 [PreparedPhoto.jpeg] 의 원본 바이트를 화면 폭에 맞춰 풀어 전체로 띄운다.
 *
 * **사유를 접힌 드롭다운이 아니라 칩으로 편다.** 제안이 못 믿을 값이라 유저가 실제로
 * 다시 고르는 것이 이 화면의 목적인데, 접어 두면 "이미 골라져 있다" 로 읽혀 그냥
 * 넘어간다. 칩이면 대안이 눈에 보이고 한 번에 눌린다.
 *
 * ⚠️ **다만 전부 펼치지는 않는다.** 실제 사유는 17개라 411dp 폭에서 다섯 줄이 되고,
 *    그만큼 [확인] 이 아래로 밀린다 — `PetFormScreen` 의 `BreedGrid` 가 견종 27종에서
 *    이미 같은 결론을 내고 "일곱 줄이면 폼의 절반이 견종이 된다" 고 적어 뒀다. 그래서
 *    높이를 묶고 그 안에서 스크롤한다. 서버가 `reason_options` 를 **이 강아지가 최근 쓴
 *    사유 먼저**로 정렬해 주므로 두세 줄이면 정답이 대개 첫 화면에 있다.
 */
@Composable
fun ReceiptConfirmScreen(
    photo: PreparedPhoto?,
    draft: VetVisitDraft?,
    options: List<VetReasonOption>,
    step: ReceiptStep,
    error: ChatApiError?,
    modifier: Modifier = Modifier,
    /**
     * 계정의 강아지들. **분할은 여기서 고른다** — 확인 화면이 아는 강아지 하나로는
     * 둘째 블록을 누구에게 붙일지 물을 수가 없다. 한 마리뿐이면 분할 자체를 안 띄운다.
     */
    pets: List<Pet> = emptyList(),
    onConfirm: (ReceiptEdits) -> Unit = {},
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    var expanded by remember(photo) { mutableStateOf(false) }

    // 전면을 덮는 화면은 back 을 잡는다 (`PetPhotoPicker` 와 같은 규칙). 크게 보는
    // 중이면 back 이 그것부터 닫는다 — 초안을 버리기 전에 한 걸음 둔다.
    BackHandler { if (expanded) expanded = false else onDismiss() }

    if (photo != null && expanded) {
        ReceiptFullScreen(photo) { expanded = false }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            // 아래 저장소 목록으로 터치가 새지 않게 (`ReceiptPicker` 와 같은 이유).
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
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

        photo?.let { prepared ->
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Image(
                    prepared.thumbnail.asImageBitmap(),
                    contentDescription = "찍은 영수증",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PREVIEW_HEIGHT)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { expanded = true },
                    // ⚠️ **자르지 않는다.** 처음엔 폭을 채우려고 `Crop` + `TopCenter` 로
                    //    윗부분을 보였는데, 실물 사진은 위아래에 **검은 여백**이 있어
                    //    미리보기가 통째로 까맣게 나왔다(실기기 실측). 사진마다 여백·회전이
                    //    달라 어디를 잘라도 안전한 자리가 없다. 미리보기는 "무슨 사진인지"
                    //    알아보는 자리이고, **대조는 눌러서 크게 보는 쪽이 한다.**
                    contentScale = ContentScale.Fit,
                )
                DaengsTextAction("눌러서 크게 보기", { expanded = true }, tint = TextMuted)
            }
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
            else -> ReceiptForm(draft, options, step, error, pets, onConfirm, onRetry, today)
        }
    }
}

/**
 * 영수증 한 장을 **읽을 수 있는 크기로** 띄운다. 아무 데나 누르면 닫힌다.
 *
 * 썸네일(512px)이 아니라 [PreparedPhoto.jpeg] 의 원본 바이트를 푼다 — 썸네일을 화면
 * 폭으로 늘리면 그게 바로 못 읽는 그림이다. 화면 폭에 맞춰 [BitmapFactory] 가
 * 건너뛰며 읽으므로(`inSampleSize`) 2400px 를 통째로 메모리에 올리지 않는다.
 */
@Composable
private fun ReceiptFullScreen(photo: PreparedPhoto, onClose: () -> Unit) {
    val widthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    val bitmap = remember(photo, widthPx) { decodeAtLeast(photo.jpeg, widthPx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .clickable(onClick = onClose),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("영수증", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            DaengsTextAction("닫기", onClose, tint = TextMuted)
        }
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "크게 본 영수증",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClose),
            // 폭을 꽉 채우고 세로는 흐르게 둔다 — 잘라내면 대조할 줄이 사라진다.
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * [target] 픽셀 폭 아래로 내려가지 않는 선에서 **건너뛰며** 읽는다.
 *
 * 원본을 통째로 풀면 2400px 영수증 한 장이 10MB 쯤 된다. 화면보다 큰 픽셀은 어차피
 * 안 보이므로 그만큼만 읽는다.
 */
private fun decodeAtLeast(jpeg: ByteArray, target: Int): android.graphics.Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    var sample = 1
    while (target > 0 && bounds.outWidth / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeByteArray(
        jpeg, 0, jpeg.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

@Composable
private fun ReceiptForm(
    draft: VetVisitDraft,
    options: List<VetReasonOption>,
    step: ReceiptStep,
    error: ChatApiError?,
    pets: List<Pet>,
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

    val blocks = remember(draft) { draft.receiptBlocks() }
    /**
     * 나눌 수 있나. **둘 다여야 한다** — 영수증이 여러 아이를 찍었고, 계정에 아이가
     * 여럿이어야 한다. `patient_count` 가 2 로 잘못 세어져도 한 마리 계정에는 물어볼
     * 이유가 없다 (카드 "꼭 지켜야 하는 것" 4).
     */
    val canSplit = draft.patientCount > 1 && pets.size > 1
    var splitting by remember(draft) { mutableStateOf(canSplit) }
    var rows by remember(draft) { mutableStateOf(initialSplitRows(blocks)) }

    val phoneOk = phoneLooksValid(phone)
    val amount = total.toIntOrNull()
    // **미래 날짜는 여기서 막는다.** 저쪽도 422 를 내지만, 유저가 [확인] 을 눌러 본 뒤에
    // 알게 하면 안 된다 — 합계 규칙과 같은 결이다. 날짜 휠은 올해까지 열려 있어서 오늘
    // 뒤를 고를 수 있다.
    val futureDate = visitedOn.isAfter(today)
    val splitAmounts = rows.amountsOrNull()
    val splitBalanced = amount != null && splitAmounts != null && remainderKrw(splitAmounts, amount) == 0
    val splitReady = rows.allChosen() && splitBalanced
    val valid = amount != null && amount in 0..MAX_TOTAL_KRW && phoneOk && !futureDate &&
        if (splitting) splitReady else reason != null

    draft.noticeText()?.let { ReceiptNotice(it, TextMuted) }
    if (draft.possibleDuplicate) {
        // **빨강이 아니다.** 막는 게 아니라 되묻는 것이라(같은 날 두 번 갈 수 있다),
        // 오류색으로 그리면 유저가 [확인] 을 안 누른다.
        ReceiptNotice("같은 날 같은 금액의 기록이 이미 있어요.", TextMuted)
    }
    // 초안은 받았는데 확정이 실패했다. **여기는 재추출이 아니라 [확인] 을 다시 누르는
    // 자리다** — 재추출하면 아래에서 고친 값이 초안 미리 채움으로 되돌아간다.
    if (error != null) ReceiptNotice(error.message ?: SAVE_FAILED, DaengsColors.Error)

    FieldLabel("방문 날짜")
    DateWheel(visitedOn, { visitedOn = it })
    if (futureDate) {
        Text(
            "영수증 날짜가 오늘보다 뒤예요. 날짜를 고쳐 주세요.",
            color = DaengsColors.Error,
            fontSize = 13.sp,
        )
    }

    FieldLabel("총액")
    TextInput(total, { total = it.filter(Char::isDigit).take(9) }, "숫자만", KeyboardType.Number, "총액")

    FieldLabel("병원")
    TextInput(name, { name = it }, "병원 이름", label = "병원 이름", maxLength = MAX_HOSPITAL_NAME)
    TextInput(address, { address = it }, "주소", label = "병원 주소", maxLength = MAX_HOSPITAL_ADDRESS)
    TextInput(phone, { phone = it }, "02-000-0000", KeyboardType.Phone, "병원 전화", maxLength = 32)
    if (!phoneOk) Text("전화번호 모양이 올바르지 않아요.", color = DaengsColors.Error, fontSize = 13.sp)
    if (amount != null && amount > MAX_TOTAL_KRW) {
        // 안 그러면 왜 [확인] 이 안 눌리는지 아무 말도 없이 죽는다.
        Text("금액이 너무 커요. 다시 확인해 주세요.", color = DaengsColors.Error, fontSize = 13.sp)
    }

    // **영수증 단위다.** 야간·응급·공휴일은 영수증에 찍힌 할증이라 아이마다 다를 수
    // 없다 — 새벽에 갔으면 두 아이 다 새벽이다. 그래서 분할 밖에 둔다.
    ToggleRow("야간·응급·공휴일이었어요", emergency) { emergency = !emergency }

    if (canSplit) {
        ToggleRow("아이별로 나누기", splitting) { splitting = !splitting }
        Text(
            "영수증에서 동물명 블록 ${draft.patientCount}개를 찾았어요.",
            color = TextMuted,
            fontSize = 13.sp,
        )
    }

    if (splitting) {
        ReceiptSplitSection(
            rows = rows,
            blocks = blocks,
            pets = pets,
            options = options,
            totalKrw = amount,
            onRows = { rows = it },
        )
    } else {
        FieldLabel("무엇 때문에 갔나")
        FlowRow(
            Modifier
                .fillMaxWidth()
                .height(REASON_ROWS_HEIGHT)
                .verticalScroll(rememberScrollState())
                // 안쪽이 끝에 닿아도 바깥 폼이 따라 움직이지 않게 남은 스크롤을 먹는다.
                // 안 붙이면 두 스크롤이 서로 밀고, 테스트의 클릭도 엉뚱한 자리에 떨어진다.
                .nestedScroll(KeepScrollInside),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                Chip(option.label, option.code == reason) { reason = option.code }
            }
        }
        TextInput(detail, { detail = it }, "한 줄 메모 (선택)", label = "메모", maxLength = MAX_REASON_DETAIL)

        ToggleRow("종양 진료였어요", oncology) { oncology = !oncology }

        if (draft.items.isNotEmpty()) {
            FieldLabel("영수증에서 읽은 항목")
            ReceiptItems(draft.items)
        }
    }

    Spacer(Modifier.height(2.dp))
    DaengsWideButton(
        label = "확인",
        onClick = {
            val receiptTotal = amount ?: return@DaengsWideButton
            val split = if (splitting) {
                rows.map { row ->
                    SplitEdit(
                        petId = row.petId ?: return@DaengsWideButton,
                        reasonCode = row.reasonCode ?: return@DaengsWideButton,
                        reasonDetail = row.detail.blankToNull(),
                        totalKrw = row.amount.toIntOrNull() ?: return@DaengsWideButton,
                        isOncology = row.oncology,
                        patientIndex = row.patientIndex,
                    )
                }
            } else {
                listOf(
                    SplitEdit(
                        // 안 나눌 때는 저쪽이 초안의 강아지를 쓴다.
                        petId = null,
                        reasonCode = reason ?: return@DaengsWideButton,
                        reasonDetail = detail.blankToNull(),
                        totalKrw = receiptTotal,
                        isOncology = oncology,
                        /*
                         * **블록이 하나일 때만 0 이다.** 여러 블록이 찍힌 영수증을 안 나누고
                         * 한 줄로 확정하면, 이 기록이 어느 블록의 것인지 우리는 모른다 —
                         * 0 이라고 하면 첫 블록의 항목만 이 기록에 붙는다. 저쪽은 `null`
                         * 이면 항목을 하나도 안 넣는데, 그게 일부러 그렇게 한 것이다.
                         */
                        patientIndex = if (draft.patientCount == 1) 0 else null,
                    ),
                )
            }
            onConfirm(
                ReceiptEdits(
                    visitedOn = visitedOn,
                    totalKrw = receiptTotal,
                    hospitalName = name.blankToNull(),
                    hospitalAddress = address.blankToNull(),
                    hospitalPhone = phone.blankToNull(),
                    isEmergency = emergency,
                    splits = split,
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
internal fun ReceiptItems(items: List<ReceiptItem>) {
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
internal fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/**
 * `PetFormScreen` 의 것과 같은 칸이다. **[label] 은 읽어 주는 쪽에만 붙는다** — 화면에
 * 두 번 쓰지 않고, 이게 없으면 테스트가 자리표시자 글자를 칸으로 착각한다.
 */
@Composable
internal fun TextInput(
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
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextDark else TextMuted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PinkSoft else CardWhite)
            // **읽어 주는 쪽이 무엇이 골라졌는지 알아야 한다.** clickable 만 두면
            // TalkBack 이 17개를 다 "버튼" 으로만 읽고, 눈으로도 옅은 색 하나가 유일한
            // 표시가 된다.
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
internal fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) PinkSoft else CardWhite)
            .toggleable(on, role = Role.Switch, onValueChange = { onToggle() })
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (on) TextDark else TextMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(if (on) "켜짐" else "꺼짐", color = if (on) DaengPink else TextMuted, fontSize = 13.sp)
    }
}

private val WON = NumberFormat.getIntegerInstance(Locale.KOREA)

internal fun wonOf(amount: Int): String = "${WON.format(amount)}원"

internal fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

/** 미리보기 높이. 여기서는 알아보기만 하고, 읽는 것은 눌러서 크게 보는 쪽이 한다. */
private val PREVIEW_HEIGHT = 200.dp

/**
 * 사유 칩 블록의 높이.
 *
 * 두 줄이 온전히 보이고 **세 번째 줄이 살짝 걸친다** — `BreedGrid` 가 같은 이유로 잡아 둔
 * 규칙이다. 딱 두 줄로 끊으면 아래에 더 있다는 게 안 보여서 스크롤할 생각을 못 한다.
 */
internal val REASON_ROWS_HEIGHT = 128.dp

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
    patientCount = 1,
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
