package com.daengs.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.care.MAX_REASON_DETAIL
import com.daengs.app.ui.common.KeepScrollInside
import com.daengs.app.care.ReceiptBlock
import com.daengs.app.care.ReceiptItem
import com.daengs.app.care.VetReasonOption
import com.daengs.app.care.remainderKrw
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate

/**
 * 영수증 한 장을 아이별로 나누는 자리. **금액이 아니라 "누구의 얼마" 를 묻는 화면이다.**
 *
 * 왜 따로 떼었나 — [ReceiptConfirmScreen] 은 이 섹션 없이도 이미 길다. 블록마다 아이·금액·
 * 사유·메모·종양이 붙으면 폼 한 함수가 두 화면치가 되어, 고칠 때 어디가 영수증 단위이고
 * 어디가 아이 단위인지 눈으로 못 가른다. **그 경계가 이 기능의 전부다.**
 *
 * 산수는 여기 없다 — `care/ReceiptSplit.kt` 에 있다. 돈이 틀리는 것은 Compose 없이
 * 재야 한다.
 */

/**
 * 블록 한 줄에서 유저가 고른 것. 화면이 들고 있는 **글자 그대로의** 상태다 —
 * 금액이 `String` 인 것은 유저가 지우는 도중(빈 칸)을 0 으로 읽지 않기 위해서다.
 */
data class SplitRow(
    val patientIndex: Int,
    val petId: String?,
    val amount: String,
    val reasonCode: String?,
    val detail: String,
    val oncology: Boolean,
)

/**
 * 블록을 화면의 줄로 옮긴다. **아이도 사유도 미리 안 고른다.**
 *
 * 아이는 영수증에 안 적혀 있다 — 동물명은 읽었지만 우리 강아지 id 와 잇는 길이 없다.
 *
 * ⚠️ **사유를 미리 채우면 안 된다.** 제안(`suggested_reason_code`)은 영수증 하나에
 *    하나뿐인데 블록마다 사유는 다르다. 모든 줄에 같은 값을 깔아 두면 유저가 그대로
 *    넘겨 **둘째 아이의 병력에 첫째 아이의 사유가** 남는다. 실기기에서 실제로 그렇게
 *    눌렸다 — 예방접종을 맞은 아이의 기록이 "귀" 로 저장됐다. 이 화면이 막으려던
 *    바로 그 실패가 자리만 옮겨 다시 난 것이다.
 */
fun initialSplitRows(blocks: List<ReceiptBlock>): List<SplitRow> =
    blocks.map { block ->
        SplitRow(
            patientIndex = block.patientIndex,
            petId = null,
            amount = block.suggestedKrw?.toString().orEmpty(),
            reasonCode = null,
            detail = "",
            oncology = false,
        )
    }

/** 금액이 다 숫자로 읽히나. 하나라도 비어 있으면 `null` 이다 — 0 으로 읽지 않는다. */
fun List<SplitRow>.amountsOrNull(): List<Int>? {
    val parsed = map { it.amount.toIntOrNull() ?: return null }
    return parsed
}

/** 아이와 사유를 다 골랐나. 금액은 [amountsOrNull] 이 따로 본다. */
fun List<SplitRow>.allChosen(): Boolean =
    isNotEmpty() && all { it.petId != null && it.reasonCode != null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiptSplitSection(
    rows: List<SplitRow>,
    blocks: List<ReceiptBlock>,
    pets: List<Pet>,
    options: List<VetReasonOption>,
    totalKrw: Int?,
    onRows: (List<SplitRow>) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { at, row ->
            SplitRowCard(
                row = row,
                number = at + 1,
                items = blocks.firstOrNull { it.patientIndex == row.patientIndex }?.items.orEmpty(),
                pets = pets,
                // **이미 고른 아이는 다른 블록에서 못 고른다.** 한 아이에게 두 줄을 만들면
                // 한 줄에 합쳐 적은 것보다 나쁘다 — 같은 진료가 두 건으로 세어진다.
                takenPetIds = rows.filterIndexed { i, _ -> i != at }.mapNotNull { it.petId }.toSet(),
                options = options,
                removable = rows.size > 1,
                onChange = { changed -> onRows(rows.toMutableList().also { it[at] = changed }) },
                onRemove = { onRows(rows.filterIndexed { i, _ -> i != at }) },
            )
        }

        if (totalKrw != null) SplitTotals(rows, totalKrw)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitRowCard(
    row: SplitRow,
    number: Int,
    items: List<ReceiptItem>,
    pets: List<Pet>,
    takenPetIds: Set<String>,
    options: List<VetReasonOption>,
    removable: Boolean,
    onChange: (SplitRow) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "블록 $number",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (removable) {
                Text(
                    "빼기",
                    color = DaengsColors.Error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRemove)
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .semantics { contentDescription = "블록 $number 빼기" },
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pets.forEach { pet ->
                PetChip(
                    name = pet.name,
                    selected = pet.id == row.petId,
                    taken = pet.id in takenPetIds,
                    description = "블록 $number 아이 ${pet.name}",
                    onClick = { onChange(row.copy(petId = pet.id)) },
                )
            }
        }

        TextInput(
            value = row.amount,
            onChange = { onChange(row.copy(amount = it.filter(Char::isDigit).take(9))) },
            hint = "이 아이 몫",
            keyboard = KeyboardType.Number,
            label = "블록 $number 금액",
        )

        /*
         * **높이를 묶고 그 안에서 스크롤한다.** 사유는 17개라 411dp 폭에서 네 줄이 되고,
         * 블록마다 네 줄이면 두 마리 영수증에서만 여덟 줄이 [확인] 위에 쌓인다 —
         * 실기기에서 폼이 화면 네 개 길이가 됐다. 영수증 단위 칩이 같은 이유로 이미
         * 높이를 묶어 뒀다 (`REASON_ROWS_HEIGHT`).
         */
        FlowRow(
            Modifier
                .fillMaxWidth()
                .height(REASON_ROWS_HEIGHT)
                .verticalScroll(rememberScrollState())
                // 안쪽이 끝에 닿아도 바깥 폼이 따라 움직이지 않게 남은 스크롤을 먹는다.
                .nestedScroll(KeepScrollInside),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                ReasonChip(
                    label = option.label,
                    selected = option.code == row.reasonCode,
                    // **블록마다 다른 이름을 준다.** 안 그러면 같은 라벨이 블록 수만큼
                    // 생겨서, 읽어 주는 쪽도 테스트도 어느 블록의 칩인지 못 가린다.
                    description = "블록 $number 사유 ${option.label}",
                ) { onChange(row.copy(reasonCode = option.code)) }
            }
        }

        TextInput(
            value = row.detail,
            onChange = { onChange(row.copy(detail = it)) },
            hint = "한 줄 메모 (선택)",
            label = "블록 $number 메모",
            maxLength = MAX_REASON_DETAIL,
        )

        ToggleRow("종양 진료였어요", row.oncology) { onChange(row.copy(oncology = !row.oncology)) }

        if (items.isNotEmpty()) ReceiptItems(items)
    }
}

/**
 * 합계와, 안 맞을 때 얼마가 남았는지.
 *
 * **원 단위로 말한다.** "합이 맞지 않아요" 로는 유저가 어느 칸을 얼마나 고쳐야 하는지
 * 모른 채 숫자를 만지게 된다. 서버도 422 로 막지만 그건 [확인] 을 누른 뒤다.
 */
@Composable
private fun SplitTotals(rows: List<SplitRow>, totalKrw: Int) {
    val amounts = rows.amountsOrNull()
    val remainder = amounts?.let { remainderKrw(it, totalKrw) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("아이별 합계", color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                amounts?.let { wonOf(it.sum()) } ?: "—",
                color = TextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("영수증 총액", color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                wonOf(totalKrw),
                color = if (remainder == 0) DaengsColors.Success else TextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (remainder != null && remainder != 0) {
            Text(
                if (remainder > 0) {
                    "${wonOf(remainder)}이 남았어요. 아이별 금액의 합이 영수증 총액과 같아야 확정할 수 있어요."
                } else {
                    "${wonOf(-remainder)}이 넘어요. 아이별 금액의 합이 영수증 총액과 같아야 확정할 수 있어요."
                },
                color = DaengsColors.Error,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * 아이 칩 하나. **이미 다른 블록이 가져간 아이는 못 고른다.**
 *
 * 감추지 않고 흐리게 두는 이유는, 사라지면 유저가 "내 아이가 왜 없지" 를 묻게 되기
 * 때문이다. 자리에 있으면 다른 블록에 있다는 것이 보인다.
 */
/** 사유 칩 하나. [PetChip] 과 같은 이유로 블록 이름을 달고 다닌다. */
@Composable
private fun ReasonChip(
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) TextDark else TextMuted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DaengsColors.BrandPrimarySoft else CardWhite)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun PetChip(
    name: String,
    selected: Boolean,
    taken: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        name,
        color = when {
            selected -> TextDark
            taken -> DaengsColors.BorderNeutral
            else -> TextMuted
        },
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DaengsColors.BrandPrimarySoft else CardWhite)
            .selectable(selected, enabled = !taken, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = description },
    )
}

// -- 미리보기 ---------------------------------------------------------------

private val PREVIEW_PETS = listOf(
    Pet("p1", "초코", "poodle", null, null, null, LocalDate.of(2020, 3, 1), null, isPrimary = true),
    Pet("p2", "보리", "maltese", null, null, null, LocalDate.of(2021, 7, 9), null, isPrimary = false),
)

private val PREVIEW_BLOCKS = listOf(
    ReceiptBlock(0, listOf(ReceiptItem("진료-초진", 109_200, 0)), 109_200),
    ReceiptBlock(1, listOf(ReceiptItem("종합백신 5차", 82_100, 1)), 82_100),
)

private val PREVIEW_REASONS = listOf(
    VetReasonOption("ear", "귀"),
    VetReasonOption("vaccination", "예방접종"),
)

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SplitBalancedPreview() = DaengsTheme {
    Column(Modifier.padding(16.dp)) {
        ReceiptSplitSection(
            rows = listOf(
                SplitRow(0, "p1", "109200", "ear", "", false),
                SplitRow(1, "p2", "82100", "vaccination", "", false),
            ),
            blocks = PREVIEW_BLOCKS,
            pets = PREVIEW_PETS,
            options = PREVIEW_REASONS,
            totalKrw = 191_300,
            onRows = {},
        )
    }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun SplitShortPreview() = DaengsTheme {
    Column(Modifier.padding(16.dp)) {
        ReceiptSplitSection(
            rows = listOf(
                SplitRow(0, "p1", "100000", "ear", "", false),
                SplitRow(1, "p2", "82100", "vaccination", "", false),
            ),
            blocks = PREVIEW_BLOCKS,
            pets = PREVIEW_PETS,
            options = PREVIEW_REASONS,
            totalKrw = 191_300,
            onRows = {},
        )
    }
}
