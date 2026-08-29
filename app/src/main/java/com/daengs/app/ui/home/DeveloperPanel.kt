package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.miniroom.DogHerd
import com.daengs.app.miniroom.MiniRoomState
import com.daengs.app.miniroom.OutsideView
import com.daengs.app.miniroom.RoomSpec
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DogAvatar

// ---------------------------------------------------------------------------
// 개발자 패널
//
// 방이 그림이 되면서 **좌표가 어긋나도 눈에 잘 안 띄게** 됐다. 소품이 살짝 이상한
// 자리에 놓일 뿐이라 원인을 못 찾는다. 그래서 격자를 덧그려 보는 스위치를 둔다.
// (저쪽 목업에도 같은 것이 있고, 거기서도 바닥 캘리브레이션에 썼다)
//
// 견종 고르기도 여기 붙였다. 16종을 방에 넣고 하나씩 확인하려면 어차피 도구가
// 필요하고, 나중에 사용자용 견종 선택 화면을 만들 때 그대로 옮기면 된다.
//
// **저장하지 않는다.** 앱을 다시 켜면 꺼진 상태로 시작한다 — 개발 중에만 쓰는
// 스위치라 저장하면 실수로 켠 채 배포될 수 있다.
// ---------------------------------------------------------------------------

private val PanelBg = Color(0xE6101820)
private val PanelText = Color(0xFFDDE6EE)
private val PanelDim = Color(0xFF7C8B99)
private val PanelPick = Color(0xFF00E5FF)

/** DEV 스위치. 방 오른쪽 위에 작게 붙는다. */
@Composable
fun DeveloperToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) PanelPick.copy(alpha = 0.85f) else PanelBg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("DEV", color = if (on) Color.Black else PanelDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(if (on) "ON" else "OFF", color = if (on) Color.Black else PanelText, fontSize = 9.sp)
    }
}

/**
 * 켰을 때 뜨는 패널.
 *
 * 방 위에 겹치므로 **낮게** 둔다 — 자를 대려고 켠 것인데 패널이 방을 가리면 아무
 * 소용이 없다. 처음엔 소품 목록까지 넣었다가 방을 절반 가려서 뺐다.
 * 소품 좌표는 [drawDeveloperOverlay] 가 방 위에 직접 라벨로 그린다.
 */
@Composable
fun DeveloperPanel(
    state: MiniRoomState,
    herd: DogHerd?,
    breedOverride: DogBreed?,
    onPickBreed: (DogBreed?) -> Unit,
    profileBreed: DogBreed,
    onPickProfile: (DogBreed) -> Unit,
    outside: OutsideView,
    onPickOutside: (OutsideView) -> Unit,
    signedIn: Boolean = false,
    onSignOut: (() -> Unit)? = null,
    /**
     * 누끼 실험실. 카드 기능을 만드는 동안만 쓰는 입구다 —
     * 세그멘테이션이 쓸 만한지 보려면 실기기에서 사진을 넣어 봐야 한다.
     */
    onOpenCutoutLab: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PanelBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "격자 ${RoomSpec.GRID}x${RoomSpec.GRID} · 소품 ${state.items.size} · 강아지 ${herd?.dogs?.size ?: 0}",
                color = PanelText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            // 로그아웃 자리가 여기인 이유: `마이` 탭이 아직 껍데기다. 진짜 화면이
            // 생기면 그리로 옮긴다.
            if (signedIn && onSignOut != null) {
                Text(
                    "로그아웃",
                    color = Color.Black,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PanelPick)
                        .clickable(onClick = onSignOut)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            if (onOpenCutoutLab != null) {
                Text(
                    "누끼",
                    color = Color.Black,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PanelPick)
                        .clickable(onClick = onOpenCutoutLab)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        // 소품 목록은 여기 안 넣는다. 좌표는 이미 방 위에 라벨로 그려지고 있어서
        // 중복인데, 개수만큼 패널이 길어져서 **방을 절반이나 가렸다.**
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BreedChip("섞기", breedOverride == null) { onPickBreed(null) }
            DogBreed.ALL.forEach { b ->
                BreedChip(b.label, breedOverride == b) { onPickBreed(b) }
            }
        }

        // 프로필은 방 안 견종과 **따로** 고른다. 상단바 얼굴만 바꿔 보고 싶을 때가
        // 있고, 반대로 방에 시바를 풀어둔 채 프로필은 비글로 두고 볼 때도 있다.
        //
        // 글자 칩을 한 줄 더 붙이지 않고 얼굴을 늘어놓는다. 25개를 글자로 훑으면
        // 원하는 걸 찾기까지 한참 밀어야 하는데, 얼굴은 한눈에 보인다. 어차피
        // 여기서 고르는 것이 그 얼굴이라 미리보기를 겸한다.
        // 창밖·문밖. **실제 시각·날씨가 붙어도 이 줄은 남긴다** — 밤·눈을 보려고
        // 밤에 눈이 오길 기다릴 수는 없다. 여섯 벌을 여기서 강제로 넘긴다.
        Text("창밖  ${outside.label}", color = PanelDim, fontSize = 9.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutsideView.entries.forEach { v ->
                BreedChip(v.label, outside == v) { onPickOutside(v) }
            }
        }

        Text("프로필  ${profileBreed.label}", color = PanelDim, fontSize = 9.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            DogBreed.ALL.forEach { b ->
                ProfilePick(b, profileBreed == b) { onPickProfile(b) }
            }
        }
    }
}

/** 얼굴 하나. 고른 것은 뒤에 깔린 원이 테를 두른 것처럼 보인다. */
@Composable
private fun ProfilePick(breed: DogBreed, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) PanelPick else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        DogAvatar(breed, Modifier.size(26.dp))
    }
}

@Composable
private fun BreedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else PanelText,
        fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) PanelPick else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
