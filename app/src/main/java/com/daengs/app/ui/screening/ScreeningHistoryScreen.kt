package com.daengs.app.ui.screening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.screening.ScreeningHolder
import com.daengs.app.screening.ScreeningRecord
import com.daengs.app.screening.ScreeningReport
import com.daengs.app.ui.common.DaengsTextAction
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 피부 **변화 기록.**
 *
 * 진단은 대화에서 하고, 여기는 **지난 것을 나란히 놓고 보는 자리**다. 이 화면이
 * 생기기 전에는 판정이 말풍선으로 한 번 지나가고 사라져서 "지난번보다 나아졌나" 를
 * 볼 수가 없었다.
 *
 * ⚠️ **1등 병변을 크게 쓰지 않는다.** 저쪽 계약에 그런 필드가 아예 없고, 없는 것이
 *    맞다 — 사진 한 장으로 병명을 단정할 수 없다 (`ScreeningReport` 주석).
 *    여기서도 판정 세 갈래와 서버가 써 준 문장만 보여 준다.
 *
 * ⚠️ **실패한 기록도 남는다.** 판정이 안 됐을 뿐 사용자가 찍은 사진이고, 다시 찍을지
 *    정하려면 봐야 한다. 그래서 목록에서 빼지 않고 그렇게 표시한다.
 */
@Composable
fun ScreeningHistoryScreen(
    holder: ScreeningHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 이 아이의 것만 볼 때. null 이면 전부. */
    petId: String? = null,
) {
    BackHandler(onBack = onBack)

    // 열 때 한 번 받아 온다. **목록은 서버가 진짜라 기기에 안 둔다.**
    LaunchedEffect(petId) { holder.refresh(petId) }

    /** 펼친 기록. 하나만 펼친다 — 둘을 나란히 비교하는 것은 다음 카드다. */
    var opened by remember { mutableStateOf<ScreeningRecord?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("피부 변화 기록", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Spacer(Modifier.weight(1f))
                DaengsTextAction("닫기", onClick = onBack)
            }

            val records = holder.records
            when {
                records == null -> Hint(if (holder.busy) "불러오는 중이에요…" else "기록을 불러올게요.")
                records.isEmpty() -> Hint(
                    "아직 기록이 없어요.\n대화에서 사진으로 물어보면 여기에 쌓여요.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(records, key = { it.recordId }) { record ->
                        RecordRow(
                            record = record,
                            photo = holder[record.recordId],
                            expanded = opened?.recordId == record.recordId,
                            onClick = { opened = if (opened?.recordId == record.recordId) null else record },
                        )
                    }
                }
            }
        }
    }

    // 펼치면 그때 단건을 받는다 — **사진 주소가 거기서만 온다.**
    LaunchedEffect(opened?.recordId) {
        val id = opened?.recordId ?: return@LaunchedEffect
        holder.open(id)?.let { opened = it }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun RecordRow(
    record: ScreeningRecord,
    photo: androidx.compose.ui.graphics.ImageBitmap?,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    Image(
                        photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(CreamBg))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(dayOf(record.createdAt), fontSize = 14.sp, color = TextDark, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(summaryOf(record), fontSize = 12.sp, color = accentOf(record))
                }
            }

            if (expanded) {
                val report = record.report
                if (report == null) {
                    // 판정 전이거나 실패다. **사진은 보여 준다** — 다시 찍을지 정하려면 봐야 한다.
                    Text(
                        "이 사진은 판정이 안 됐어요. 다시 찍어 보실래요?",
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 19.sp,
                    )
                } else {
                    // 덩어리 경보는 여기서도 제일 위다. 안 뜨면 null 이라 안 그린다.
                    report.alert?.let { a ->
                        Text(
                            listOf(a.text, a.action).filter { it.isNotBlank() }.joinToString(" "),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentOf(record),
                            lineHeight = 20.sp,
                        )
                    }
                    Text(report.headline, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentOf(record))
                    report.group?.let {
                        Text(it.text, fontSize = 13.sp, color = TextDark, lineHeight = 20.sp)
                        // ★ 특징 한 줄 (2026-09-10). 채팅 카드와 **같은 것을 그린다** —
                        //    한쪽만 고치면 같은 판정이 화면마다 다르게 보인다.
                        //    ⚠️ 옛 기록은 `feature` 가 없다. 그때는 이름 줄만 남는다.
                        //    ⚠️ 목록은 훑어보는 화면이라 `detail`(자세히 보기)은 안 그린다.
                        if (it.feature.isNotBlank()) {
                            Text(
                                "${it.feature} 같은 모습이 보이는 상태예요.",
                                fontSize = 12.sp, color = TextDark, lineHeight = 18.sp,
                            )
                        }
                    }
                    Text(report.body, fontSize = 13.sp, color = TextDark, lineHeight = 20.sp)
                    Text(report.action, fontSize = 13.sp, color = TextDark, lineHeight = 20.sp)
                    // ★ 2026-09-08 — 6종(report.stage2) 대신 **계열 네 묶음**이다.
                    //   채팅 카드와 같은 것을 그려야 한다. 한쪽만 고치면 같은 결과가
                    //   화면마다 다르게 보이고, 갈라져도 아무도 모른다.
                    //
                    //   ⚠️ **옛 기록은 `groups` 가 없다** — 이 기능 전에 저장된 판정이다.
                    //      그때는 문장 셋만 남는다. 6종으로 물러서지 않는다: 그 이름을
                    //      안 보여 주기로 한 것이 이 변경의 이유다.
                    report.groups.forEach {
                        Text(
                            "· ${it.name}  ${"%.0f".format(it.percent)}%",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                    Text(report.disclaimer, fontSize = 11.sp, color = TextMuted, lineHeight = 17.sp)
                }
            }
        }
    }
}

/** 서버가 준 ISO 문자열에서 날짜만. **파싱이 실패해도 화면이 안 죽는다.** */
private fun dayOf(createdAt: String): String =
    createdAt.take(10).takeIf { it.length == 10 } ?: createdAt

private fun summaryOf(record: ScreeningRecord): String = when {
    record.report == null && record.status == ScreeningRecord.Status.FAILED -> "판정 실패"
    record.report == null -> "사진 올리는 중"
    else -> when (record.report.verdict) {
        ScreeningReport.Verdict.NORMAL -> "특별한 것은 안 보였어요"
        ScreeningReport.Verdict.ABNORMAL -> "살펴볼 것이 있어요"
        ScreeningReport.Verdict.RETAKE -> "다시 찍는 게 좋아요"
    }
}

private fun accentOf(record: ScreeningRecord) = when (record.report?.verdict) {
    ScreeningReport.Verdict.ABNORMAL -> DaengPinkDeep
    ScreeningReport.Verdict.NORMAL -> TextDark
    else -> TextMuted
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ScreeningHistoryEmptyPreview() {
    DaengsTheme {
        // 네트워크를 안 타는 홀더 — 토큰이 null 이라 아무 요청도 안 나간다.
        ScreeningHistoryScreen(holder = ScreeningHolder { null }, onBack = {})
    }
}
