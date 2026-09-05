package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * **강아지가 있어야 하는 기능 앞의 문.**
 *
 * 예전에는 로그인하면 곧장 강아지 등록이었고 빠져나갈 수도 없었다. 방을 보기도 전에
 * 정보를 채우게 만드는 구조라 거기서 이탈했다. 이제 강아지 없이 방까지 들어오고,
 * **그 아이가 있어야 되는 일을 누를 때** 그 자리에서 청한다.
 *
 * ## 왜 한 벌인가
 *
 * 청하는 자리가 셋이다(산책 · 챗봇 · 카드 뽑기). 자리마다 따로 만들면 문구가 갈리고,
 * 나중에 넷째가 생길 때 어느 것을 베낄지가 사람마다 다르다. [RoomTour] 가 겹 하나로
 * 여러 자리를 가리킨 것과 같은 이유다.
 *
 * ## 여기서 안 막는 것
 *
 * **도감 보기와 산책 기록**은 안 막는다. 이미 있는 것을 보는 자리라 강아지가 없어도
 * 말이 된다 — 막으면 "뽑아 둔 카드를 못 보는" 일이 생긴다.
 */
enum class PetNeed(val title: String, val body: String) {
    /** 산책. `WalkRoute` 로 가기 전. */
    Walk(
        title = "누구랑 산책할까요?",
        body = "산책은 아이 기록으로 남아요.\n먼저 강아지를 등록해 주세요.",
    ),

    /** 챗봇. 아이를 모르면 일반론밖에 답할 수 없다. */
    Chat(
        title = "누구 이야기를 할까요?",
        body = "우리 아이를 알아야 맞는 답을 드려요.\n먼저 강아지를 등록해 주세요.",
    ),

    /** 카드 뽑기. 얼굴을 빌려올 아이가 없으면 이름 없는 카드가 나온다. */
    Card(
        title = "누구 얼굴로 만들까요?",
        body = "카드에는 우리 아이 얼굴이 들어가요.\n먼저 강아지를 등록해 주세요.",
    ),
}

/**
 * 지금 이 사람에게 **강아지를 청해야 하나.**
 *
 * ⚠️ **로그인한 사람에게만 청한다.** 둘러보기(디버그 전용)는 계정이 없어 등록할 곳이
 * 없고, 거기서는 이름 없이도 카드가 뽑히게 되어 있다(`CardDrawScreen`). 그 자리에
 * 문을 세우면 둘러보기가 아무것도 못 하는 화면이 된다.
 *
 * ⚠️ **`null` 은 아직 못 받아 온 것이라 청하지 않는다.** 목록이 오는 사이에 문이
 * 떴다가 사라지면, 사용자는 자기가 뭘 잘못 눌렀다고 읽는다.
 * [com.daengs.app.ui.home.roomRoster] 의 갈래와 같은 규칙이다.
 */
fun needsPet(signedIn: Boolean, pets: List<Pet>?): Boolean =
    signedIn && pets != null && pets.isEmpty()

/**
 * 문. **거절이 아니라 안내다** — 닫기가 있고, 등록으로 가는 길이 크게 있다.
 *
 * @param onAdd 강아지 등록으로. 부르는 쪽이 화면을 옮긴다
 * @param onDismiss 나중에 하기. **이 길이 반드시 있어야 한다** — 이 카드가 없애려는
 *   것이 바로 "빠져나갈 수 없는 등록 화면" 이다
 */
@Composable
fun PetNeededDialog(need: PetNeed, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        PetNeededContent(need = need, onAdd = onAdd, onDismiss = onDismiss)
    }
}

@Composable
private fun PetNeededContent(need: PetNeed, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(need.title, color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                need.body,
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GateButton(
                    label = "나중에",
                    fill = PinkFaint,
                    tint = TextMuted,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GateButton(
                    label = "강아지 등록하기",
                    fill = DaengPink,
                    tint = CardWhite,
                    onClick = onAdd,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun GateButton(
    label: String,
    fill: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        color = tint,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

/**
 * **빈 방 한가운데의 초대.**
 *
 * 방을 비우기로 한 것과 **한 벌이다.** `RoomRoster.roomPets` 가 적어 둔 대로 빈 방은
 * 그 자체로는 "고장 난 것" 으로 읽힌다 — 여기가 그것을 "아직 아무도 안 왔다" 로
 * 바꿔 준다. 하나만 내보내면 진짜로 고장 난 화면이 된다.
 *
 * 방 그림 위에 얹히므로 **바탕을 깐다.** 벽지가 테마마다 달라서, 글자만 두면 어떤
 * 테마에서는 안 읽힌다.
 */
@Composable
fun EmptyRoomInvite(onAddPet: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite.copy(alpha = 0.92f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("아직 방이 비었어요", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "강아지를 등록하면 여기서 뛰어놀아요.",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        GateButton(
            label = "강아지 데려오기",
            fill = DaengPink,
            tint = CardWhite,
            onClick = onAddPet,
            modifier = Modifier.width(170.dp),
        )
    }
}

// -- 프리뷰 ------------------------------------------------------------------

@Preview(name = "문 · 산책", widthDp = 340)
@Composable
private fun PetNeededWalkPreview() {
    DaengsTheme {
        Surface(color = CreamBg) {
            PetNeededContent(PetNeed.Walk, onAdd = {}, onDismiss = {})
        }
    }
}

@Preview(name = "문 · 카드", widthDp = 340)
@Composable
private fun PetNeededCardPreview() {
    DaengsTheme {
        Surface(color = CreamBg) {
            PetNeededContent(PetNeed.Card, onAdd = {}, onDismiss = {})
        }
    }
}

/** 좁은 화면에서 버튼 두 개가 붙는지 본다. 작은 폰이 실제로 있다. */
@Preview(name = "문 · 좁은 화면", widthDp = 280)
@Composable
private fun PetNeededNarrowPreview() {
    DaengsTheme {
        Surface(color = CreamBg) {
            PetNeededContent(PetNeed.Chat, onAdd = {}, onDismiss = {})
        }
    }
}

@Preview(name = "빈 방 초대", widthDp = 340, heightDp = 220)
@Composable
private fun EmptyRoomInvitePreview() {
    DaengsTheme {
        Surface(color = CreamBg) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyRoomInvite(onAddPet = {})
            }
        }
    }
}
