package com.daengs.app.ui.gait

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitSampleRecords
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 지난 기록 하나를 고르는 시트.
 *
 * **바텀 시트다.** 이 저장소에는 `NavHost` 가 없고 화면 전환이 [Screen] 하나로
 * 갈리는데(`MainActivity`), 기록 고르기 하나 때문에 화면을 새로 만들면 대화가
 * 통째로 사라졌다 돌아온다 — 고르다 말고 취소하는 일이 흔한 화면이라 대화는 그대로
 * 뒤에 남아 있어야 한다. 시트는 대화 위에 얹혀서 그 조건을 그냥 만족한다.
 *
 * `material3` 의 `ModalBottomSheet` 를 안 쓴다. 이 저장소는 M3 부품 대신 `Surface`
 * 에 `.clickable` 을 붙여 직접 짠다 (`DaengsControls.kt` 첫 주석) — M3 시트는 자기
 * 모서리·자기 손잡이 색을 들고 와서 크림·핑크 사이에서 혼자 튄다.
 *
 * 쓰는 자리가 둘이다:
 *  - **비교 상대 고르기** (기본값). `comparable` 인 것만 고를 수 있다
 *  - **지난 기록 보기** (`requireComparable = false`). 어느 기록이든 열어 본다 —
 *    비교 지표가 없어도 영상은 볼 수 있으니까
 *
 * @param records 고를 수 있는 기록. 방금 찍은 것은 부르는 쪽이 빼고 넘긴다
 * @param requireComparable false 면 비교 지표가 없는 기록도 고를 수 있다
 */
@Composable
fun GaitPickSheet(
    records: List<GaitRecord>,
    onDismiss: () -> Unit,
    onConfirm: (GaitRecord) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "지난 보행 기록 선택",
    confirmLabel: String = "선택 완료",
    requireComparable: Boolean = true,
) {
    // 고른 것은 시트 안에서만 산다. 확인을 눌러야 밖으로 나간다 — 줄을 눌렀다고
    // 바로 다음으로 넘어가면 잘못 누른 것을 되돌릴 자리가 없다.
    var picked by remember {
        mutableStateOf(records.firstOrNull { !requireComparable || it.comparable }?.id)
    }
    val chosen = records.firstOrNull { it.id == picked }

    SheetFrame(title = title, onDismiss = onDismiss, modifier = modifier) {
        if (records.isEmpty()) {
            EmptyLine(
                if (requireComparable) {
                    "아직 비교할 지난 기록이 없어요.\n보행 영상을 한 편 더 남기면 나란히 볼 수 있어요."
                } else {
                    "아직 남긴 보행 기록이 없어요."
                },
            )
        } else {
            LazyColumn(
                Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    val enabled = !requireComparable || record.comparable
                    GaitPickRow(
                        record = record,
                        selected = record.id == picked,
                        enabled = enabled,
                        // 비교 지표가 없는 기록은 못 고른다. 고르게 두면 다음 화면이
                        // "부족합니다" 만 띄우게 되는데, 그건 여기서 이미 알 수 있는 일이다.
                        onClick = { if (enabled) picked = record.id },
                    )
                }
            }
        }
        ConfirmButton(
            label = confirmLabel,
            enabled = chosen != null,
            onClick = { chosen?.let(onConfirm) },
            modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
        )
    }
}

/**
 * 비교할 기록 **둘을 한 시트에서** 고른다.
 *
 * 예전에는 같은 시트를 두 번 열었다(기준 → 상대). 두 번째 시트에서 첫 번째로 고른 것을
 * 빼는 것까지는 맞았는데, 사용자 입장에서는 "방금 골랐는데 또 고르라고?" 가 됐다.
 * 두 개를 체크하고 한 번에 넘어가는 편이 하려는 일(둘을 나란히 놓기)과 같은 모양이다.
 *
 * **셋째를 누르면 먼저 고른 것이 빠진다.** 셋을 막고 "둘까지만" 을 띄우는 것보다,
 * 마지막에 누른 둘이 남는 쪽이 손이 덜 간다. 고른 것을 다시 누르면 빠진다.
 *
 * 순서는 넘기지 않는다 — 어느 쪽이 최근인지는 날짜가 정한다 (`GaitHolder.compare`).
 */
@Composable
fun GaitPairPickSheet(
    records: List<GaitRecord>,
    onDismiss: () -> Unit,
    onConfirm: (GaitRecord, GaitRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 고른 순서대로 든다. 셋째가 오면 앞(먼저 고른 것)을 민다.
    var picked by remember { mutableStateOf<List<String>>(emptyList()) }
    val chosen = picked.mapNotNull { id -> records.firstOrNull { it.id == id } }
    val comparable = records.count { it.comparable }

    SheetFrame(
        title = "비교할 기록 2개 선택",
        subtitle = "${picked.size} / $PAIR 선택",
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        if (comparable < PAIR) {
            // 부르는 쪽이 막고 있지만(둘 미만이면 [기록 비교] 줄을 안 그린다), 시트가
            // 스스로도 말할 수 있어야 한다.
            EmptyLine("비교할 수 있는 기록이 둘은 있어야 해요.\n보행 영상을 한 편 더 남기면 나란히 볼 수 있어요.")
        } else {
            LazyColumn(
                Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    GaitPickRow(
                        record = record,
                        selected = record.id in picked,
                        enabled = record.comparable,
                        onClick = {
                            if (!record.comparable) return@GaitPickRow
                            picked = when {
                                record.id in picked -> picked - record.id
                                else -> (picked + record.id).takeLast(PAIR)
                            }
                        },
                    )
                }
            }
        }
        ConfirmButton(
            label = "비교하기",
            enabled = chosen.size == PAIR,
            onClick = { if (chosen.size == PAIR) onConfirm(chosen[0], chosen[1]) },
            modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
        )
    }
}

/** 한 번에 고르는 수. 비교는 둘을 나란히 놓는 일이라 둘이다. */
private const val PAIR = 2

/**
 * 시트의 틀 — 어둠 · 손잡이 · 제목 · 닫기. 두 시트가 같은 틀을 쓴다.
 *
 * @param subtitle 제목 오른쪽 작은 글자. 몇 개 골랐는지 같은 것
 */
@Composable
private fun SheetFrame(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(modifier.fillMaxSize()) {
        // 바깥을 눌러도 닫힌다. 물결(ripple)은 끈다 — 시트 뒤 어둠이 눌린 것처럼
        // 번쩍이면 그쪽에 뭔가 있는 줄 안다.
        Box(
            Modifier
                .fillMaxSize()
                .background(TextDark.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Surface(
            color = CardWhite,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 시트가 화면을 다 먹지 않게 막는다. 뒤에 대화가 조금이라도
                // 보여야 "위에 얹힌 것" 으로 읽힌다.
                .heightIn(max = 560.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Column(Modifier.padding(horizontal = 18.dp)) {
                // 손잡이. 끌어 내릴 수 있어 보이라고 두는 것이 아니라, 여기가
                // 시트의 위쪽이라는 표시다.
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DaengsColors.BorderNeutral),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        color = TextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (subtitle != null) {
                        Text(subtitle, color = DaengPinkDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { DaengsIconView(DaengsIcon.Close, Modifier.size(16.dp), tint = TextMuted) }
                }
                content()
            }
        }
    }
}

/** 고를 것이 없을 때의 한 줄. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 28.dp),
    )
}

/**
 * 목록의 한 줄. 썸네일 · 날짜 · 길이 · 고름 표시.
 *
 * 못 고르는 줄은 **지우지 않고 흐리게 남긴다.** 목록에서 빼 버리면 "07.15 에 찍은 게
 * 있었는데 어디 갔지" 가 된다. 남겨 두고 왜 못 고르는지를 그 자리에 적는다.
 *
 * @param enabled 고를 수 있나. 비교 상대로는 `comparable` 만, 지난 기록 보기로는 전부
 */
@Composable
private fun GaitPickRow(record: GaitRecord, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PinkSoft.copy(alpha = 0.55f) else PinkFaint)
            .border(
                1.dp,
                if (selected) DaengPink else DaengsColors.BorderNeutral,
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GaitThumbnail(
            record,
            Modifier.size(54.dp),
            showLength = false,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${record.dateLabel} 보행 기록",
                color = if (enabled) TextDark else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (record.comparable) record.lengthLabel else "${record.lengthLabel} · 비교 지표 부족",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        // 고름 표시. 라디오 버튼을 안 쓰고 원 안에 체크를 그린다 — M3 라디오는
        // 자기 색(보라)을 들고 온다.
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) DaengPink else CardWhite)
                .border(
                    1.5.dp,
                    if (selected) DaengPink else DaengsColors.BorderNeutral,
                    RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) DaengsIconView(DaengsIcon.Check, Modifier.size(13.dp), tint = CardWhite)
        }
    }
}

/** 시트를 닫는 한 줄짜리 버튼. 고른 게 없으면 눌리지 않는다. */
@Composable
private fun ConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) DaengPinkDeep else PinkFaint)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) CardWhite else TextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun GaitPickSheetPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(com.daengs.app.ui.theme.CreamBg)) {
            GaitPickSheet(GaitSampleRecords.of(), onDismiss = {}, onConfirm = {})
        }
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun GaitPairPickSheetPreview() {
    DaengsTheme {
        Box(Modifier.fillMaxSize().background(com.daengs.app.ui.theme.CreamBg)) {
            GaitPairPickSheet(GaitSampleRecords.of(), onDismiss = {}, onConfirm = { _, _ -> })
        }
    }
}
