package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.DaengsColors
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/*
 * 방 위에 얹히는 것들.
 *
 * Canvas 가 아니라 일반 Composable 로 만든다:
 *  - 셋 다 글자가 들어간다. Canvas 에 그리려면 TextMeasurer 를 써야 하고
 *    줄바꿈·글꼴 크기 설정 대응이 나빠진다.
 *  - 카메라 버튼은 눌러야 하므로 터치 영역과 접근성 정보가 필요하다.
 *  - 깊이 정렬(col+row)에 낄 이유가 없다. 항상 맨 앞이다.
 */

@Composable
fun TodayCard(
    dateLabel: String,
    note: String,
    /**
     * 날씨 아이콘.
     *
     * **기본값을 안 준다.** 기본값을 주는 순간 잊은 호출부가 조용히 해를 그린다 —
     * 비 오는 날에 해가 떠 있던 것이 정확히 그 모양이었다. 필수로 두면 컴파일러가
     * 호출부를 다 짚어 준다.
     */
    icon: DaengsIcon,
    accent: Color = DaengPink,
    modifier: Modifier = Modifier,
    /**
     * 펴져 있나. 접으면 `TODAY` 줄만 남기고 방을 비켜 준다.
     *
     * 이 카드는 방 왼쪽 위를 덮고 있어서, 미니룸을 보려면 카드를 피해 봐야 했다.
     */
    expanded: Boolean = true,
    /** 눌렀을 때. null 이면 안 접힌다 — `@Preview` 가 그렇게 쓴다. */
    onToggle: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardWhite.copy(alpha = 0.92f),
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(16.dp), clip = false)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // **접으면 `TODAY` 를 뺀다.** 바로 옆에 오늘 날짜가 붙으므로 같은 말을
                // 두 번 하는 셈이고, 접는 이유가 방을 비켜 주는 것인데 그만큼 덜 비킨다.
                // 펼친 카드에서는 제목 노릇을 하므로 그때는 남긴다.
                if (expanded) {
                    Text(
                        "TODAY",
                        color = DaengPinkDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.width(20.dp))
                }
                DaengsIconView(icon, Modifier.size(17.dp), tint = accent)
                // 접혀 있으면 날짜가 이 줄로 올라온다. 접어 놔도 오늘이 며칠인지는
                // 보여야 한다 — 그게 이 카드를 두는 이유다.
                if (!expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        dateLabel,
                        color = TextDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        dateLabel,
                        color = TextDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(note, color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = CardWhite.copy(alpha = 0.85f),
        modifier = modifier.size(46.dp).shadow(5.dp, RoundedCornerShape(50), clip = false),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DaengsIconView(DaengsIcon.Camera, Modifier.size(23.dp), tint = DaengPinkDeep)
        }
    }
}

/**
 * 방 앞쪽에 걸린 "○○이네" 이름표.
 *
 * **누르면 고친다.** [onClick] 이 null 이면 못 누른다 — 로그인 전에는 고쳐도 저장할
 * 곳이 없어서, 눌리는데 아무 일도 안 일어나는 것보다 안 눌리는 편이 낫다.
 */
@Composable
fun NamePlate(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = CardWhite,
        border = androidx.compose.foundation.BorderStroke(2.dp, PinkSoft),
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(11.dp), clip = false)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick)
                },
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            DaengsIconView(DaengsIcon.Heart, Modifier.size(13.dp), tint = DaengPink)
        }
    }
}

/**
 * 이름표 고치기.
 *
 * **비우면 되돌아간다** — 대표 강아지 이름으로 지은 이름이 다시 걸린다. 그래서 지우는
 * 버튼을 따로 두지 않는다. 지금 걸린 이름이 지어진 것이면 칸이 비어 있고, 그 자리에
 * 지어진 이름을 흐리게 보여 준다(placeholder) — 무엇으로 돌아가는지 알 수 있다.
 */
@Composable
fun RoomNameDialog(
    current: String?,
    /** 비웠을 때 걸릴 이름. 사용자가 정하지 않았을 때의 값이다. */
    fallback: String,
    busy: Boolean,
    error: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(current) { mutableStateOf(current.orEmpty()) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("이름표 바꾸기", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Surface(color = PinkFaint, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) {
                            Text(fallback, color = TextMuted, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = text,
                            // 서버가 20자까지 받는다. 넘겨 보내 놓고 422 를 받는 것보다
                            // 아예 안 들어가는 편이 낫다.
                            onValueChange = { if (it.length <= MAX_ROOM_NAME) text = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TextDark, fontSize = 15.sp),
                            cursorBrush = SolidColor(DaengPink),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "비우면 대표 강아지 이름으로 돌아가요.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = DaengsColors.Error, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(18.dp))
                if (busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = DaengPink,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DialogText("취소", TextMuted, FontWeight.Normal, onDismiss)
                        Spacer(Modifier.width(6.dp))
                        DialogText("저장", DaengPink, FontWeight.Bold) {
                            onConfirm(text.trim().takeIf(String::isNotEmpty))
                        }
                    }
                }
            }
        }
    }
}

/** 이름표 글자 수. 서버 `app_users.room_name` 이 VARCHAR(20) 이다. */
const val MAX_ROOM_NAME = 20

@Composable
private fun DialogText(
    label: String,
    tint: Color,
    weight: FontWeight,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = tint,
        fontSize = 14.sp,
        fontWeight = weight,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF3D8D2)
@Composable
private fun RoomNameDialogPreview() {
    DaengsTheme {
        RoomNameDialog(
            current = null,
            fallback = "네옹이네",
            busy = false,
            error = null,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "TODAY · 접힘/펴짐", showBackground = true, backgroundColor = 0xFFF3D8D2)
@Composable
private fun TodayCardFoldPreview() {
    DaengsTheme {
        Column(Modifier.padding(12.dp)) {
            TodayCard(HomeDemoData.MOCK_DATE, "산책 가기 좋은 날!", DaengsIcon.Sun, expanded = true)
            Spacer(Modifier.height(10.dp))
            // 접으면 날짜가 첫 줄로 올라온다. 방을 가리지 않으면서 오늘은 알려 준다.
            TodayCard(HomeDemoData.MOCK_DATE, "산책 가기 좋은 날!", DaengsIcon.Sun, expanded = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3D8D2)
@Composable
private fun TodayCardPreview() {
    DaengsTheme {
        Column(Modifier.padding(12.dp)) {
            // 나란히 둔다 — 새 아이콘을 눈으로 보는 자리다.
            TodayCard(HomeDemoData.MOCK_DATE, "산책 가기 좋은 날!", DaengsIcon.Sun)
            Spacer(Modifier.height(10.dp))
            TodayCard(HomeDemoData.MOCK_DATE, "흐리지만 선선해요", DaengsIcon.Cloud)
            Spacer(Modifier.height(10.dp))
            TodayCard(HomeDemoData.MOCK_DATE, "날씨를 보고 있어요", DaengsIcon.Sun)
            Spacer(Modifier.height(10.dp))
            TodayCard(HomeDemoData.MOCK_DATE, "비가 오고 있어요", DaengsIcon.CloudRain)
            Spacer(Modifier.height(10.dp))
            TodayCard(HomeDemoData.MOCK_DATE, "눈이 내리고 있어요", DaengsIcon.CloudSnow)
            Spacer(Modifier.height(10.dp))
            TodayCard(HomeDemoData.MOCK_DATE, "선선한 밤이에요", DaengsIcon.Moon)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3D8D2, fontScale = 1.5f)
@Composable
private fun NamePlateLargeFontPreview() {
    DaengsTheme {
        Column(Modifier.padding(12.dp)) {
            NamePlate(HomeDemoData.ROOM_LABEL)
            Spacer(Modifier.height(10.dp))
            CameraButton(onClick = {})
        }
    }
}
