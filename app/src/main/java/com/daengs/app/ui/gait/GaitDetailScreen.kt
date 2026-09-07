package com.daengs.app.ui.gait

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.daengs.app.gait.summaryLines
import com.daengs.app.gait.GaitStage
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.time.LocalDate

/**
 * 기록 한 편의 상세.
 *
 * **요약이 "무엇을 관찰했나" 가 아니라 "무엇을 했나" 다.** 지금 서버가 없어서 관절을
 * 실제로 뽑은 것이 아니고, 없는 관찰을 문장으로 지어내면 그게 곧 판정이 된다
 * (`CONTEXT.md` 8절). 그래서 이 화면이 지금 말할 수 있는 것은 **처리한 단계와 영상의
 * 사실**(길이·날짜·비교 가능 여부)뿐이고, 관찰 문장은 두 기록을 나란히 놓는 비교
 * 화면에서만 나온다.
 *
 * 계약이 붙으면 [GaitAnalysisSummary] 자리에 저쪽 문장이 들어온다.
 */
@Composable
fun GaitDetailScreen(
    record: GaitRecord,
    canCompare: Boolean,
    onBack: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 열자마자 영상을 틀까.
     *
     * **비교 화면에서 카드를 눌러 온 경우에만 참이다.** 그때 사용자가 원한 것은
     * "이 기록의 분석 영상을 크게 보는 것" 하나뿐이라, 도착해서 재생을 한 번 더
     * 눌러야 하면 누른 이유가 화면에서 이어지지 않는다. 대화 카드에서 들어온
     * 경우는 상세를 읽으러 온 것이라 그대로 멈춰 둔다.
     */
    autoPlay: Boolean = false,
) {
    BackHandler(onBack = onBack)

    // 지우기는 두 번 묻는다. 영상은 다시 못 찍는 날의 기록이라, 한 번 눌러서
    // 사라지면 되돌릴 길이 없다.
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        GaitTopBar("${record.dateLabel} 보행 기록", onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 상세는 **영상을 보러 오는 화면**이라 표지가 아니라 재생기를 놓는다.
            // 컨트롤을 켜서 되감기·일시정지를 손으로 할 수 있게 한다 — 걸음 한
            // 주기를 다시 보려면 되감기가 있어야 한다.
            GaitVideoPlayer(
                record,
                Modifier.fillMaxWidth().aspectRatio(record.displayAspect),
                playing = autoPlay,
            )

            DetailSection("분석 상태") {
                // 네 단계 다 끝난 뒤에만 열리는 화면이라 전부 완료로 그린다.
                // 진행 중인 것을 보는 자리는 대화의 진행 카드다.
                GaitStage.entries.forEach { stage ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DaengsIconView(DaengsIcon.Check, Modifier.size(15.dp), tint = DaengsColors.Success)
                        Spacer(Modifier.width(9.dp))
                        Text(stage.label, color = TextDark, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("완료", color = DaengsColors.Success, fontSize = 12.sp)
                    }
                }
            }

            DetailSection("요약") { GaitAnalysisSummary(record) }

            Text(
                "보행 영상은 같은 아이의 시점별 변화를 나란히 보기 위한 기록이에요.\n" +
                    "건강 상태를 판단하거나 진단하지 않아요.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (canCompare) {
                GaitActionButton(
                    DaengsIcon.Compare,
                    "지난 기록과 비교하기",
                    onCompare,
                    Modifier.fillMaxWidth(),
                    accent = true,
                )
            }
            DeleteAction(
                confirming = confirming,
                onAsk = { confirming = true },
                onCancel = { confirming = false },
                onConfirm = onDelete,
            )
        }
    }
}

/**
 * 요약. **관찰 문장이 아니라 영상의 사실이다.**
 *
 * 못 쓰는 영상이면 그 사실을 말해 준다 — 비교에서 왜 지표가 안 나오는지가 여기서
 * 미리 설명돼야, 비교 화면의 "지표가 부족합니다" 가 갑작스럽지 않다.
 *
 * **이유는 서버 문장을 그대로 쓴다** ([GaitRecord.qualityReason]). 예전에는 여기서
 * "10초보다 짧게 찍혀서" 라고 단정했는데, `comparable` 의 뜻이 길이에서 서버의
 * `quality.status` 로 바뀐 뒤에도 이 문장만 옛 뜻에 남아 **1분짜리 영상에도
 * "짧게 찍혀서" 가 떴다.** 이유를 아는 쪽은 저쪽이고, 앱은 옮기기만 한다.
 *
 * 길이를 말하는 것은 **앱이 실제로 재서 아는 경우뿐이다** — 기기에서 읽은 초가
 * 권장보다 짧을 때. 서버 목록에서 온 기록은 길이를 모르므로 아무 말도 안 한다.
 */
@Composable
private fun GaitAnalysisSummary(record: GaitRecord) {
    val lines = record.summaryLines()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("·", color = DaengPink, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(line, color = TextDark, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = TextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            color = CardWhite,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PinkSoft),
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * 지우기.
 *
 * 다이얼로그를 안 띄우고 **그 자리에서 두 갈래로 벌어진다.** 다이얼로그는 화면을
 * 덮어서 무엇을 지우려던 것인지가 안 보이는데, 이 화면은 뒤에 그 기록이 떠 있는
 * 것 자체가 확인의 일부다.
 */
@Composable
private fun DeleteAction(
    confirming: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!confirming) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .border(1.dp, DaengsColors.BorderNeutral, RoundedCornerShape(13.dp))
                .clickable(onClick = onAsk),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaengsIconView(DaengsIcon.Trash, Modifier.size(16.dp), tint = TextMuted)
            Spacer(Modifier.width(7.dp))
            Text("기록 삭제", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "이 기록을 지우면 되돌릴 수 없어요.",
            color = DaengsColors.Error,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(PinkFaint)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Text("그대로 두기", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(DaengsColors.ErrorSoft)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text("지우기", color = DaengsColors.Error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitDetailScreenPreview() {
    DaengsTheme {
        GaitDetailScreen(
            record = GaitRecord("p", LocalDate.of(2026, 8, 31), seconds = 12),
            canCompare = true,
            onBack = {},
            onCompare = {},
            onDelete = {},
        )
    }
}

/**
 * 못 쓰는 영상. **1분짜리인데 비교 불가**인 경우를 일부러 띄운다 — 여기가
 * "10초보다 짧게 찍혀서" 가 잘못 뜨던 자리라, 서버 사유가 그대로 나오는지 본다.
 */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitDetailUnusablePreview() {
    DaengsTheme {
        GaitDetailScreen(
            record = GaitRecord(
                "q",
                LocalDate.of(2026, 9, 1),
                seconds = 62,
                comparable = false,
                qualityReason = "걷는 구간이 충분히 잡히지 않았어요.",
                qualityAdvice = "강아지가 쉬지 않고 걷는 장면으로 다시 찍어 주세요.",
            ),
            canCompare = false,
            onBack = {},
            onCompare = {},
            onDelete = {},
        )
    }
}

/** 사유 없이 비교 불가일 때. 앱이 원인을 짚지 않고 사실만 말해야 한다. */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitDetailNoReasonPreview() {
    DaengsTheme {
        GaitDetailScreen(
            record = GaitRecord("r", LocalDate.of(2026, 7, 15), seconds = 6, comparable = false),
            canCompare = false,
            onBack = {},
            onCompare = {},
            onDelete = {},
        )
    }
}
