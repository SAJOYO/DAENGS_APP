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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.gait.GaitComparison
import com.daengs.app.gait.GaitJoint
import com.daengs.app.gait.GaitJointChange
import com.daengs.app.gait.GaitJointState
import com.daengs.app.gait.GaitLeg
import com.daengs.app.gait.GaitRecord
import com.daengs.app.gait.GaitVerdict
import com.daengs.app.gait.reliabilitySentence
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
 * 두 기록을 나란히 보는 화면.
 *
 * **이 화면에는 좋아졌다·나빠졌다가 없다.** `CONTEXT.md` 8절이 보행을 "기록·비교용"
 * 으로 못박아 뒀고, 그 원칙이 실제로 지켜지는지는 여기서 갈린다 — 두 시점을 나란히
 * 놓으면 화살표 하나만 그려도 곧바로 호전/악화 화면이 되기 때문이다. 그래서
 *
 *  - 관절 상태는 [GaitJointChange] 네 갈래(+측정 부족)이고 다 **차이의 유무와 방향**만 말한다
 *  - 어느 쪽이 낫다는 순서를 안 준다. 최근이 왼쪽인 것은 **시간 순서**일 뿐이다
 *  - 제목·보조문구는 [GaitComparison.verdict] 가 관절 결과에서 끌어낸 것을 그대로 쓴다
 *
 * ### 색을 쓰면서 진단이 되지 않게
 *
 * 관절 점에 초록·주황·빨강이 들어간다. 색은 글자보다 먼저 읽히므로 **빨강이 "위험"
 * 으로 읽힐 위험**이 실제로 있다. 그래서
 *
 *  - 요약 배너는 갈래와 **무관하게 같은 색**이다. 색이 붙는 곳은 관절 점뿐이다
 *  - 빨강의 뜻은 "두 방향 모두 달라졌다" 이지 정도가 심하다는 뜻이 아니다
 *  - 화면 아래 진단아님 문구를 **지우지 않는다.** 색이 들어왔으니 더 필요해졌다
 *
 * @param onOpenRecord 그 기록의 상세로 가서 분석 오버레이를 크게 본다.
 *   상세를 새로 만들지 않고 [GaitDetailScreen] 을 그대로 쓰되, 그 화면이 영상부터
 *   틀어 주므로 카드를 누른 직후 눈에 들어오는 것은 그 한 편의 오버레이다
 * @param onCompareAnother 비교할 과거 기록을 다시 고른다
 * @param onSaveToChat 이 비교를 대화에 남긴다. 서버에 저장하는 것이 아니다 —
 *   저장할 곳이 아직 없어서, "남긴다" 가 실제로 뜻하는 자리는 대화뿐이다
 */
@Composable
fun GaitCompareScreen(
    comparison: GaitComparison,
    onBack: () -> Unit,
    onOpenRecord: (GaitRecord) -> Unit,
    onCompareAnother: () -> Unit,
    onSaveToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    // **두 편을 한 단추로 같이 돌린다.** 따로 돌리게 두면 한쪽을 보는 동안 다른
    // 쪽이 멈춰 있어서, 나란히 놓은 뜻이 없어진다 — 이 화면이 하려는 일이
    // "같은 순간의 두 시점" 이라 둘이 같이 움직여야 눈이 비교를 한다.
    var playing by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        GaitTopBar("보행 기록 비교", onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 두 기둥과 그 사이의 VS. 폭을 반씩 나눠서 크기로 순서를 안 준다 —
            // 한쪽을 크게 그리면 그쪽이 기준처럼 보인다.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComparePillar(
                    comparison.recent,
                    "최근 기록",
                    accent = true,
                    playing = playing,
                    onOpen = { onOpenRecord(comparison.recent) },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.width(46.dp).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = PinkSoft, shape = RoundedCornerShape(50)) {
                        Text(
                            "VS",
                            color = DaengPinkDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        )
                    }
                }
                ComparePillar(
                    comparison.past,
                    "비교 기록",
                    accent = false,
                    playing = playing,
                    onOpen = { onOpenRecord(comparison.past) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 둘 다 재생. 한쪽이라도 틀 것이 있어야 의미가 있다 — 서버 오버레이(지난
            // 기록의 유일한 재생본)나 기기 원본 중 하나라도. 표본끼리면 둘 다 없어 감춘다.
            //
            // **카드 터치와 역할이 다르다.** 이 단추는 둘을 동시에 보는 것이고,
            // 카드 하나를 누르는 것은 그 한 편을 크게 보는 것이다.
            if (comparison.recent.playable || comparison.past.playable) {
                GaitActionButton(
                    if (playing) DaengsIcon.Close else DaengsIcon.Play,
                    if (playing) "둘 다 멈춤" else "둘 다 재생",
                    { playing = !playing },
                    Modifier.fillMaxWidth(),
                    accent = playing,
                )
            }

            VerdictBanner(comparison.verdict)

            JointTable(comparison.joints)

            // 변화가 잡혔을 때만 나온다. 차이가 없는 화면에 "촬영 조건을 확인하세요"
            // 가 붙어 있으면 무엇을 확인하라는 것인지 알 수 없다.
            if (comparison.verdict.changed) {
                AdviceCard(
                    "먼저 촬영 조건을 확인해 주세요",
                    "반려견이 뛰었거나 걷는 속도, 촬영 각도, 바닥 환경 등이 이전 영상과 " +
                        "다르면 움직임 차이가 감지될 수 있어요.",
                )
                AdviceCard(
                    "비슷한 조건에서도 변화가 계속된다면",
                    "같은 조건으로 다시 촬영해 경과를 관찰해 주세요. 변화가 반복되거나 " +
                        "보행이 불편해 보인다면 수의사 등 전문가와 상담하는 것을 권장해요.",
                )
            }

            // 저쪽이 준 문장을 **그대로** 옮긴다. 어느 기록이 왜 참고용인지, 버전이
            // 어떻게 다른지는 서버만 안다.
            // 믿을 만한 정도는 **앱이 문장을 짓는다.** 저쪽 `reliability_note` 에는
            // record UUID 와 `§21 기준 80프레임 미만` 같은 내부 표기가 들어 있어 그대로
            // 띄울 수 없었다 ([GaitComparison.reliabilityNote]).
            NoteBox(comparison.reliabilitySentence)
            // 버전 경고는 저쪽 문장을 그대로 쓴다 — 어느 버전끼리인지는 서버만 안다.
            comparison.versionWarning?.let { NoteBox(it) }

            // 이 화면이 판정이 아니라는 말은 **화면 안에** 있어야 한다. 문서에만
            // 적어 두면 화면을 보는 사람에게는 없는 말이다. 관절 점에 색이 들어온
            // 뒤로는 더 그렇다 — 색을 정상/위험으로 읽지 않게 붙들어 주는 문장이다.
            Text(
                "이 비교는 같은 아이의 두 시점을 나란히 놓아 본 것이에요.\n" +
                    "건강 상태를 판단하거나 진단하지 않아요.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                // 위 주의 상자의 글자와 왼쪽 끝을 맞춘다. 상자가 없다고 여백을 안 주면
                // 이 문구만 왼쪽으로 튀어나온다.
                modifier = Modifier.padding(horizontal = NOTE_INSET),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 예전 자리에는 "결과 자세히 보기" 가 있었고 단일 기록 상세로 갔다.
            // 비교를 보다가 한쪽 기록의 상세로 튕겨 나가는 흐름이라 어색했다 —
            // 영상을 보고 싶으면 위의 카드를 누르는 길이 생겼으니 여기서는 뺐다.
            GaitActionButton(DaengsIcon.Compare, "다른 기록 비교하기", onCompareAnother, Modifier.weight(1f))
            GaitActionButton(DaengsIcon.Chat, "대화에 남기기", onSaveToChat, Modifier.weight(1f), accent = true)
        }
    }
}

/**
 * 한쪽 기둥. 라벨 · 날짜 · 표지 · 길이 · **누르면 크게 보기.**
 *
 * @param accent 최근 쪽만 분홍 테를 두른다. **더 중요하다는 뜻이 아니라** 둘이
 *   같은 모양이면 어느 쪽이 언제 것인지 날짜를 매번 읽어야 하기 때문이다
 * @param onOpen 이 기록의 분석 오버레이를 크게 본다
 */
@Composable
private fun ComparePillar(
    record: GaitRecord,
    label: String,
    accent: Boolean,
    playing: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (accent) PinkSoft.copy(alpha = 0.5f) else PinkFaint,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (accent) DaengPink.copy(alpha = 0.45f) else DaengsColors.BorderNeutral),
        modifier = modifier,
    ) {
        Column(
            // 카드 전체가 누르는 자리다. 영상만 누르게 하면 어디를 눌러야 하는지
            // 손가락으로 찾아야 한다.
            Modifier.clickable(onClick = onOpen).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                label,
                color = if (accent) DaengPinkDeep else TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(record.dateLabel, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            // 칸이 좁아 컨트롤을 끈다. 재생은 위의 단추가 둘을 같이 몬다.
            GaitVideoPlayer(
                record,
                // 여기만 **고정 비율을 쓴다.** 두 기둥이 서로 다른 높이면
                // 나란히 놓은 뜻이 흐려진다 — 비교는 같은 크기로 봐야 한다.
                Modifier.fillMaxWidth().aspectRatio(GaitRecord.PORTRAIT_ASPECT),
                playing = playing,
                controls = false,
            )
            Text(record.lengthLabel, color = TextMuted, fontSize = 12.sp)
            // 누를 수 있다는 것을 한 줄로 알린다. 아이콘만 얹으면 재생 단추로 읽힌다.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DaengsIconView(DaengsIcon.Video, Modifier.size(12.dp), tint = DaengPinkDeep)
                Spacer(Modifier.width(4.dp))
                Text("분석 영상 보기", color = DaengPinkDeep, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 제목 한 줄과 보조문구 한 줄.
 *
 * **갈래마다 색을 갈지 않는다.** 변화가 관찰됐다고 주황·빨강으로 칠하면 그 순간
 * 중립이던 문장이 경고가 된다 — 색은 글자보다 먼저 읽힌다. 색이 붙는 곳은
 * 관절 점뿐이고, 그건 방향을 구분하려는 것이지 정도를 매기려는 것이 아니다.
 */
@Composable
private fun VerdictBanner(verdict: GaitVerdict) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(15.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DaengsIconView(DaengsIcon.Joint, Modifier.size(19.dp), tint = DaengPink)
            Spacer(Modifier.width(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    verdict.title,
                    color = TextDark,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(verdict.detail, color = TextMuted, fontSize = 12.5.sp, lineHeight = 19.sp)
            }
        }
    }
}

/**
 * 관절 여섯 줄. **다리로 묶어서 그린다.**
 *
 * 예전에는 `L_Hip (좌우)` 같은 열두 줄이 평평하게 나열돼 있었다. 서버 key 를 그대로
 * 띄운 것이라 읽을 수 없었고, 축이 나뉘어 있어 관절 하나를 알려면 두 줄을 찾아
 * 맞춰야 했다. 지금은 다리 → 관절 순서로 묶여서 **위에서 아래로 읽으면 된다.**
 */
@Composable
private fun JointTable(joints: List<GaitJointState>) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PinkSoft),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (joints.isEmpty()) {
                Text("잴 수 있는 관절 지표가 없어요.", color = TextMuted, fontSize = 13.sp)
                return@Column
            }
            GaitLeg.entries.forEach { leg ->
                val rows = joints.filter { it.joint.leg == leg }
                if (rows.isEmpty()) return@forEach
                Text(
                    leg.label,
                    color = DaengPinkDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                rows.forEach { JointRow(it) }
            }
        }
    }
}

/**
 * 관절 한 줄.
 *
 * 오른쪽 점의 색만 갈린다 — **글자는 검다.** 문구까지 주황·빨강으로 물들이면
 * 주의보로 읽힌다. 점의 뜻은 방향의 개수이지 정도가 아니다:
 * 초록 = 두 방향 다 비슷함, 주황 = 한 방향, 빨강 = 두 방향, 회색 = 못 쟀음.
 */
@Composable
private fun JointRow(state: GaitJointState) {
    val dot = dotColorFor(state.change)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DaengsIconView(DaengsIcon.Joint, Modifier.size(16.dp), tint = TextMuted)
        Spacer(Modifier.width(10.dp))
        Text(state.joint.label, color = TextDark, fontSize = 14.sp, modifier = Modifier.width(56.dp))
        Text(
            state.change.label,
            color = if (state.change == GaitJointChange.Unknown) TextMuted else TextDark,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
    }
}

/**
 * 상태 점의 색.
 *
 * **빨강은 "위험" 이 아니다.** 좌우와 위아래가 둘 다 달라졌다는 표시이고, 그
 * 이상으로 읽지 않게 붙드는 것은 화면 아래 진단아님 문구다.
 */
private fun dotColorFor(change: GaitJointChange): Color = when (change) {
    GaitJointChange.None -> DaengsColors.Success
    GaitJointChange.Horizontal, GaitJointChange.Vertical -> DaengsColors.Warning
    GaitJointChange.Both -> DaengsColors.Error
    GaitJointChange.Unknown -> DaengsColors.BorderNeutral
}

/**
 * 변화가 잡혔을 때만 나오는 안내 한 장.
 *
 * **첫 장이 촬영 조건인 것이 순서의 전부다.** 변화가 감지되는 흔한 이유가 강아지가
 * 아니라 촬영이라서, 그 말을 먼저 하지 않으면 사용자가 두 번째 장(전문가 상담)만
 * 읽고 겁을 낸다.
 */
@Composable
private fun AdviceCard(title: String, body: String) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PinkSoft),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DaengsIconView(DaengsIcon.Bulb, Modifier.size(16.dp), tint = DaengPink)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

/**
 * 표 아래 붙는 주의 한 줄. 믿을 만한 정도(앱이 지음)와 버전 경고(서버 문장)가 같은 모양을 쓴다 —
 * 둘 다 "결과를 어떻게 받아들일지" 를 말하는 자리라 생김새가 갈리면 하나가 더 중해 보인다.
 */
@Composable
private fun NoteBox(text: String) {
    Surface(color = PinkFaint, shape = RoundedCornerShape(13.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NOTE_INSET, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 가운뎃점. 한 줄만 있으면 상자 안이 허전해서 문장이 떠 보인다. Row 로 두면
            // 여러 줄로 접힐 때 둘째 줄이 글자 앞으로 들어와 매달린 들여쓰기가 된다.
            Text("·", color = TextMuted, fontSize = 11.5.sp, lineHeight = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(text, color = TextMuted, fontSize = 11.5.sp, lineHeight = 18.sp)
        }
    }
}

/**
 * [NoteBox] 안쪽 여백. **진단아님 문구도 같은 값을 쓴다** — 상자가 없는 그 문구가 상자 안
 * 글자보다 왼쪽에서 시작하면 둘의 왼쪽 끝이 어긋나 보인다.
 */
private val NOTE_INSET = 13.dp

/** 보행 화면들이 같이 쓰는 상단바. 대화 헤더와 높이·되돌아가기 자리가 같다. */
@Composable
fun GaitTopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = TextDark, fontSize = 34.sp, lineHeight = 30.sp) }
        Text(
            title,
            color = TextDark,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        trailing()
    }
}

// ── 프리뷰 ────────────────────────────────────────────────────────────────────
//
// 표본 생성기 대신 **상태를 손으로 적는다.** 갈래마다 화면이 어떻게 달라지는지가
// 프리뷰의 요점인데, 해시로 뽑으면 어느 갈래가 나올지 읽는 사람이 알 수 없다.

private fun jointsOf(vararg changes: GaitJointChange): List<GaitJointState> =
    GaitJoint.entries.mapIndexed { index, joint -> GaitJointState(joint, changes[index]) }

private fun previewRecords(): Pair<GaitRecord, GaitRecord> =
    GaitRecord("now", LocalDate.of(2026, 8, 31), seconds = 21) to
        GaitRecord("old", LocalDate.of(2026, 7, 15), seconds = 24)

/** 한쪽 다리만 기준을 채운 경우. 안내 두 장이 붙는다. */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCompareOneSidePreview() {
    val (recent, past) = previewRecords()
    DaengsTheme {
        GaitCompareScreen(
            comparison = GaitComparison.of(
                recent,
                past,
                jointsOf(
                    GaitJointChange.None, GaitJointChange.Vertical, GaitJointChange.None,
                    GaitJointChange.Horizontal, GaitJointChange.None, GaitJointChange.Both,
                ),
            ),
            onBack = {},
            onOpenRecord = {},
            onCompareAnother = {},
            onSaveToChat = {},
        )
    }
}

/** 기준 미충족. 화면에서 제일 조용해야 한다 — 안내가 안 붙는다. */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCompareNoChangePreview() {
    val (recent, past) = previewRecords()
    DaengsTheme {
        GaitCompareScreen(
            comparison = GaitComparison.of(
                recent,
                past,
                jointsOf(
                    GaitJointChange.None, GaitJointChange.None, GaitJointChange.Horizontal,
                    GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
                ),
            ),
            onBack = {},
            onOpenRecord = {},
            onCompareAnother = {},
            onSaveToChat = {},
        )
    }
}

/** 측정 부족. **"비슷함" 으로 흘러들면 안 되는 경우다.** */
@Preview(widthDp = 411, heightDp = 891, showBackground = true)
@Composable
private fun GaitCompareNotEnoughPreview() {
    val recent = GaitRecord("now", LocalDate.of(2026, 8, 31), seconds = 6, comparable = false)
    val past = GaitRecord("old", LocalDate.of(2026, 7, 15), seconds = 9, comparable = false)
    DaengsTheme {
        GaitCompareScreen(
            comparison = GaitComparison.of(
                recent,
                past,
                jointsOf(
                    GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
                    GaitJointChange.None, GaitJointChange.None, GaitJointChange.None,
                ),
            ),
            onBack = {},
            onOpenRecord = {},
            onCompareAnother = {},
            onSaveToChat = {},
        )
    }
}
