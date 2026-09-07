package com.daengs.app.ui.gait

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.gait.GaitProgress
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitStage
import com.daengs.app.gait.GaitStageState
import com.daengs.app.miniroom.art.drawPawStamp
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
import java.time.LocalDate

/**
 * 대화 안에 서는 보행 카드들.
 *
 * **말풍선이 아니라 카드다.** 보행은 한 번 시작하면 등록 → 진행 → 결과로 이어지는데,
 * 그 셋이 말풍선이면 대화가 흘러갈 때 어느 줄이 지금 상태인지 흐려진다. 카드는
 * 폭을 꽉 채우고 테두리를 가져서 "여기서 뭘 해야 한다" 가 한눈에 남는다.
 *
 * 셋 다 [GaitCard] 를 껍데기로 쓴다 — 대화에 셋이 나란히 쌓이는 화면이라
 * 모서리와 테두리가 조금이라도 다르면 다른 기능처럼 보인다.
 */
@Composable
private fun GaitCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1) 보행 영상 등록
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 흐름의 첫 카드. "보행 분석해줘" 라고 말했을 때, 그리고 AI 기능 선택을 건너뛰고
 * 바로 들어왔을 때 이 카드가 뜬다.
 *
 * **어떻게 찍어야 하는지를 고르기 전에 말한다.** 찍고 나서 알려 주면 다시 찍어야
 * 한다 — AI 기능 선택 시트가 같은 이유로 안내 줄을 버튼 아래 달고 있다.
 */
@Composable
fun GaitIntroCard(
    onCapture: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 저장된 기록끼리 비교(B 진입). **새 영상을 올리지 않아도** 지난 기록 둘을 견준다.
     *
     * `null` 이면 줄을 아예 안 그린다 — 비교할 기록이 둘 미만일 때가 그렇다. 눌러도
     * 고를 것이 없는 버튼을 띄우면 사용자가 자기가 뭘 잘못했나 생각하게 된다.
     */
    onCompareSaved: (() -> Unit)? = null,
) {
    GaitCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("보행 영상 등록", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            PawMark(Modifier.size(18.dp))
        }
        Text(
            "뒤에서 걷는 모습이 잘 보이는 영상을 준비해주세요.\n" +
                "쉬지 않고 걷는 모습이 ${GaitRecord.MIN_WALKING_SECONDS}초 이상 담기게, " +
                    "${GaitRecord.RECOMMENDED_SECONDS}초 넘게 찍어 주세요.",
            color = TextDark,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaitActionButton(DaengsIcon.Video, "영상 촬영", onCapture, Modifier.weight(1f))
            GaitActionButton(DaengsIcon.VideoLibrary, "불러오기", onPick, Modifier.weight(1f))
        }
        GaitHintRow("뒤에서 걷는 모습 / ${GaitRecord.RECOMMENDED_SECONDS}초 넘게 권장")
        onCompareSaved?.let {
            GaitActionButton(DaengsIcon.Video, "기록 비교", it, Modifier.fillMaxWidth())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) 분석 중
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 네 단계가 하나씩 켜지는 카드.
 *
 * **끝난 줄도 안 지운다.** 진행 중인 줄만 남기면 지금 몇 번째인지, 앞으로 얼마나
 * 남았는지가 안 보인다 — 영상 길이에 따라 오래 걸릴 수 있는 일이라 남은 양이
 * 보이는 편이 기다릴 만하다.
 *
 * 상태 판단은 [GaitProgress.stateOf] 가 한다. 여기서 `ordinal` 을 다시 비교하면
 * 카드와 모델이 서로 다른 규칙을 갖게 된다.
 */
@Composable
fun GaitProgressCard(progress: GaitProgress, modifier: Modifier = Modifier) {
    GaitCard(modifier) {
        Text("분석 중", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GaitStage.entries.forEach { stage ->
                GaitStageRow(stage, progress.stateOf(stage))
            }
        }
        GaitHintRow("영상 길이에 따라 시간이 걸릴 수 있어요.")
    }
}

@Composable
private fun GaitStageRow(stage: GaitStage, state: GaitStageState) {
    // 진행 중만 분홍이다. 완료까지 분홍으로 두면 네 줄이 다 같은 색이 되어
    // 어디가 지금인지 안 보인다.
    val accent = when (state) {
        GaitStageState.Done -> DaengsColors.Success
        GaitStageState.Running -> DaengPinkDeep
        GaitStageState.Waiting -> TextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (state) {
                GaitStageState.Done ->
                    DaengsIconView(DaengsIcon.Check, Modifier.size(17.dp), tint = accent)
                // 도는 점선 원. 진행 중인 줄에만 움직임을 준다 — 정지 화면에서도
                // 어느 줄이 살아 있는지 알아야 한다.
                GaitStageState.Running -> SpinningRing(accent)
                GaitStageState.Waiting ->
                    Box(
                        Modifier
                            .size(15.dp)
                            .border(1.6.dp, DaengsColors.BorderNeutral, RoundedCornerShape(50)),
                    )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            stage.label,
            color = if (state == GaitStageState.Waiting) TextMuted else TextDark,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            state.label,
            color = accent,
            fontSize = 12.sp,
            fontWeight = if (state == GaitStageState.Running) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 진행 중 표시. 점선 원이 한 바퀴 도는 것으로 "돌아가는 중" 을 말한다. */
@Composable
private fun SpinningRing(color: Color) {
    val spin = rememberInfiniteTransition(label = "gait-stage")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "gait-stage-angle",
    )
    androidx.compose.foundation.Canvas(Modifier.size(16.dp).rotate(angle)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.8.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(3.2.dp.toPx(), 2.6.dp.toPx()),
                ),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3) 분석 결과
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 끝난 뒤 대화에 남는 카드.
 *
 * **판정 문장이 없다.** 제목은 날짜뿐이고, 배지는 이 영상이 다른 날과 나란히 놓일 수
 * 있는지만 말한다 (`CONTEXT.md` 8절: 보행은 "이상 있음/없음" 을 안 낸다). 무엇이
 * 관찰됐는지는 두 기록을 비교해야 나오는 말이라, 비교 화면으로 미룬다.
 *
 * @param canCompare 비교할 지난 기록이 있나. 없으면 비교 버튼을 감춘다 — 눌러서
 *   빈 목록을 보는 것보다 없는 편이 낫다
 */
@Composable
fun GaitResultCard(
    record: GaitRecord,
    canCompare: Boolean,
    onOpen: () -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GaitCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${record.dateLabel} 보행 기록",
                color = TextDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            GaitBadge(record)
        }
        // **영상 비율을 따른다.** 가로로 박아 두면 세로 영상이 좌우로 텅 빈 채
        // 눕는다 — 촬영 가이드가 세로라 이 기능의 영상은 대부분 세로다.
        GaitThumbnail(
            record,
            Modifier.fillMaxWidth().aspectRatio(record.displayAspect),
            onPlay = onOpen,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GaitActionButton(DaengsIcon.Chart, "결과 보기", onOpen, Modifier.weight(1f))
            if (canCompare) {
                GaitActionButton(DaengsIcon.Compare, "지난 기록과 비교", onCompare, Modifier.weight(1f))
            }
        }
    }
}

/**
 * 오른쪽 위 배지.
 *
 * 비교 가능은 분홍, 지표 부족은 회색이다. **빨강을 안 쓴다** — 빨강은 이 앱에서
 * 오류 색이고, 영상이 짧게 찍힌 것은 오류가 아니라 다시 찍으면 되는 일이다.
 */
@Composable
private fun GaitBadge(record: GaitRecord) {
    val on = record.comparable
    Surface(
        color = if (on) PinkSoft else PinkFaint,
        shape = RoundedCornerShape(9.dp),
    ) {
        Text(
            record.badgeLabel,
            color = if (on) DaengPinkDeep else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 카드끼리 나눠 쓰는 것들
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 영상 표지. 가운데 재생 단추, 오른쪽 아래 길이.
 *
 * **표지가 없을 때가 흔하다.** 표본 기록은 파일이 없고([GaitRecord.video] 주석),
 * 진짜 영상도 코덱에 따라 프레임을 못 얻는다. 그래서 없을 때가 예외가 아니라
 * 기본이고, 발바닥을 옅게 깐 자리표시로 물러선다 — 검은 네모로 두면 고장으로 읽힌다.
 */
@Composable
fun GaitThumbnail(
    record: GaitRecord,
    modifier: Modifier = Modifier,
    onPlay: (() -> Unit)? = null,
    showLength: Boolean = true,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PinkFaint)
            .then(if (onPlay != null) Modifier.clickable(onClick = onPlay) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val frame = record.thumbnail
        if (frame != null) {
            // **Crop 이 아니라 Fit 이다.** Crop 은 상자를 꽉 채우려고 넘치는 쪽을
            // 잘라내는데, 세로 프레임에서 잘려나가는 위쪽이 곧 **머리**다.
            // 실제로 결과 카드에 엉덩이와 뒷다리만 남아 "서버가 잘랐나" 를
            // 의심하게 만들었다 — 자른 것은 이 줄이었다.
            //
            // 보행에서 봐야 할 것은 네 다리와 몸 전체라, 남는 여백을 감수하더라도
            // 한 조각도 안 자르는 편이 맞다.
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "${record.dateLabel} 보행 영상",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PawMark(Modifier.fillMaxSize().padding(18.dp), alpha = 0.30f)
        }

        // 재생 단추. 흰 원 위에 분홍 삼각형이라, 사진이 밝든 어둡든 읽힌다.
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(CardWhite.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) { DaengsIconView(DaengsIcon.Play, Modifier.size(19.dp), tint = DaengPink) }

        // 길이를 모르는 기록(서버 목록에서 온 것)이면 배지를 아예 안 그린다.
        // 빈 배지가 남으면 "0초" 로 읽힌다.
        val clock = record.clockLabel
        if (showLength && clock != null) {
            Surface(
                color = TextDark.copy(alpha = 0.55f),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(
                    clock,
                    color = CardWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * 카드 안의 버튼. 분홍 테두리 + 옅은 분홍 바탕.
 *
 * [DaengsWideButton][com.daengs.app.ui.common.DaengsWideButton] 을 안 쓰는 이유:
 * 그건 지도 카드용이라 아이콘 자리가 없고 높이가 38dp 다. 여기 버튼은 아이콘을
 * 달고 대화 흐름 안에서 눌리는 것이라 손가락이 닿을 만큼 더 높아야 한다.
 */
@Composable
fun GaitActionButton(
    icon: DaengsIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (accent) DaengPink else PinkFaint)
            .border(
                1.dp,
                if (accent) DaengPink else PinkSoft,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (accent) CardWhite else DaengPinkDeep
        DaengsIconView(icon, Modifier.size(17.dp), tint = fg)
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** 전구 + 한 줄. 카드마다 같은 모양이라 안내라는 것이 위치로 읽힌다. */
@Composable
fun GaitHintRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DaengsIconView(DaengsIcon.Bulb, Modifier.size(14.dp), tint = DaengPink)
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** 표지가 없을 때 깔리는 발바닥. 방 아트가 쓰는 것과 같은 도장이다. */
@Composable
fun PawMark(modifier: Modifier = Modifier, alpha: Float = 1f) {
    androidx.compose.foundation.Canvas(modifier) {
        drawPawStamp(
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
            r = size.minDimension / 2.6f,
            color = DaengPink.copy(alpha = alpha),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0, widthDp = 380)
@Composable
private fun GaitCardsPreview() {
    DaengsTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GaitIntroCard(onCapture = {}, onPick = {})
            GaitProgressCard(GaitProgress(1))
            GaitResultCard(
                record = GaitRecord("p", LocalDate.of(2026, 8, 31), seconds = 12),
                canCompare = true,
                onOpen = {},
                onCompare = {},
            )
            GaitResultCard(
                record = GaitRecord("q", LocalDate.of(2026, 8, 20), seconds = 6, comparable = false),
                canCompare = false,
                onOpen = {},
                onCompare = {},
            )
        }
    }
}
