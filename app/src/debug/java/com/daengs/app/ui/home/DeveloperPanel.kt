package com.daengs.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.daengs.app.pet.DEV_PET_COUNTS
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.dogcard.CARD_TEMPLATES
import com.daengs.app.ui.dogcard.CardTemplate
import androidx.compose.ui.tooling.preview.Preview

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
    // 위 여백을 **여기서** 챙긴다. 호출부에 두면 릴리스 스텁이 아무것도 안 그려도
    // 그 Spacer 만 남아 인벤토리 버튼 아래에 설명할 수 없는 9dp 가 생긴다.
    Spacer(Modifier.height(9.dp))
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
    /**
     * 카드 실험실. 카드 기능을 만드는 동안만 쓰는 입구다 — 얼굴이 구멍에 잘 앉는지
     * 보려면 실기기에서 사진을 넣어 봐야 한다.
     */
    onOpenCutoutLab: (() -> Unit)? = null,
    /**
     * 야채를 지정해서 카드를 만든다. **뽑기로는 원하는 것이 안 나온다** — 12종
     * 균등에 중복도 허용이고 하루 세 번이라, 이머시브가 있는 셋(배추·고구마·상추)이나
     * 팝아웃이 있는 토마토 한 장을 보려면 며칠이 걸린다.
     */
    onMakeCard: ((CardTemplate) -> Unit)? = null,
    /**
     * 빌려 쓸 얼굴이 있나. **없으면 만들지 않는다.**
     *
     * [onMakeCard] 는 이미 뽑아 둔 카드에서 얼굴을 빌리는데, 한 장도 없으면 얼굴 없는
     * 카드가 조용히 만들어진다. 그런 카드는 `composed` 가 false 라 무대·창틀에 우리
     * 것이 하나도 안 얹혀서, 정작 보려던 것을 못 본다 — 실제로 그렇게 헤맸다.
     * 도감 칸만 차지하고 뽑기 횟수도 안 먹어서 지운 티도 안 난다.
     */
    canMakeCard: Boolean = false,
    /**
     * 프로필 사진을 올려 본다.
     *
     * **로그인해야만 볼 수 있던 것을 여기서 본다.** 사진은 등록한 강아지에 딸리는데
     * (`pet-photos/<id>.jpg`), 이 패널은 강아지 없이 얼굴을 보는 자리다 — 견종을
     * 갈아끼우는 줄이 있는 것과 같은 이유다. **저장하지 않는다.** 앱을 끄면 사라진다.
     */
    onPickProfilePhoto: (() -> Unit)? = null,
    /** 올려 본 사진을 지우고 견종 그림으로 되돌린다. */
    onClearProfilePhoto: (() -> Unit)? = null,
    /** 지금 올려 본 사진이 있나. 글씨를 가르는 데만 쓴다. */
    hasProfilePhoto: Boolean = false,
    /**
     * 가짜 강아지를 몇 마리 넣어 볼까.
     *
     * **강아지에 딸린 화면은 전부 로그인해야만 보인다** — 마이의 강아지 카드,
     * 삭제·배웅 확인창, 방에 서는 아이, 방 구성 고르기. 계정을 못 쓰는 기기에서
     * 그것들을 보려면 여기밖에 길이 없다 (`pet/DevPets.kt`).
     *
     * **저장하지 않는다.** 앱을 끄면 사라지고, 서버에도 안 간다.
     */
    onPickDevPets: ((Int) -> Unit)? = null,
    /** 지금 넣어 둔 마릿수. `0` 이면 원래대로다 */
    devPetCount: Int = 0,
    /**
     * **빈 방으로 보기.** 로그인한 사람에게 강아지가 한 마리도 없는 상태를 흉내 낸다.
     *
     * **이 폰의 디버그 빌드로는 카카오 로그인이 안 돼서, 그 상태에 닿을 길이 여기밖에
     * 없다.** 둘러보기는 로그인 전이라 방에 데모가 서고(`roomRoster` ②), 로그인해야만
     * 나오는 빈 방(③)과 「강아지 데려오기」와 기능 앞의 문(`PetGate.kt`)을 그대로 지나친다.
     * 없으면 그것들을 **릴리스를 뽑아야 처음 본다.**
     *
     * **저장하지 않는다.** 앱을 끄면 사라진다 (패널의 다른 스위치와 같은 규칙).
     */
    onToggleEmptyRoom: (() -> Unit)? = null,
    /** 지금 빈 방으로 보고 있나 */
    emptyRoom: Boolean = false,
    /**
     * 방 가로 늘림 ([com.daengs.app.miniroom.RoomSpec.H_STRETCH], 기본 1.18).
     *
     * **한계는 기기마다 다르다.** `RoomGeometry.of` 가 상자 폭에서 자르므로 어느
     * 지점부터는 올려도 방이 더 안 넓어진다 — Pixel 3 XL(방 상자 335dp)은 1.53,
     * Pixel 7(452dp)은 1.14 에서 한계다. 그래서 범위를 넉넉히 1.6 까지 둔다.
     *
     * **저장하지 않는다.** 값을 정하면 사람이 상수를 고친다.
     */
    hStretch: Float = com.daengs.app.miniroom.RoomSpec.H_STRETCH,
    onPickHStretch: ((Float) -> Unit)? = null,
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

        // **방 가로 늘림.** 「각 폰에서 방을 최대한 크게」 를 눈으로 고르는 자리다.
        // 정사각에 가까운 폰(Pixel 3 XL)만 한계까지 모자라고, 길쭉한 폰은 기본값에서
        // 이미 한계라 올려도 안 변한다. 대가는 러그가 아니라 **모양**이다 —
        // 아이소메트릭이 옆으로 퍼지고 가로 도트가 굵어지고 개가 상대적으로 작아진다.
        if (onPickHStretch != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("가로", color = PanelDim, fontSize = 9.sp)
                Slider(
                    value = hStretch,
                    onValueChange = onPickHStretch,
                    valueRange = 1f..1.6f,
                    // 0.01 단위로 멈춘다. 연속이면 같은 값으로 돌아올 수가 없어서
                    // "1.35 가 나았다" 를 확인할 방법이 없다.
                    steps = 59,
                    colors = SliderDefaults.colors(
                        thumbColor = PanelPick,
                        activeTrackColor = PanelPick,
                        inactiveTrackColor = PanelDim,
                    ),
                    modifier = Modifier.width(140.dp).height(18.dp),
                )
                Text(
                    String.format("%.2f", hStretch),
                    color = PanelPick,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (onPickDevPets != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("강아지", color = PanelDim, fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
                DEV_PET_COUNTS.forEach { count ->
                    Text(
                        if (count == 0) "원래대로" else "${count}마리",
                        color = if (count == devPetCount) Color.Black else PanelText,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (count == devPetCount) PanelPick else Color.Transparent)
                            .clickable { onPickDevPets(count) }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }

        if (onToggleEmptyRoom != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("방", color = PanelDim, fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
                Text(
                    if (emptyRoom) "빈 방으로 보는 중" else "빈 방으로 보기",
                    color = if (emptyRoom) Color.Black else PanelText,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (emptyRoom) PanelPick else Color.Transparent)
                        .clickable(onClick = onToggleEmptyRoom)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        if (onPickProfilePhoto != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (hasProfilePhoto) "사진 바꿔보기" else "사진 올려보기",
                    color = Color.Black,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(PanelPick)
                        .clickable(onClick = onPickProfilePhoto)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                if (hasProfilePhoto && onClearProfilePhoto != null) {
                    Text(
                        "사진 지우기",
                        color = PanelDim,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onClearProfilePhoto)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }

        // **맨 아래에 두지 않는다.** 패널 아래쪽은 방 이름표("우리집")가 덮는데,
        // 열두 개를 다 밀면 토마토가 정확히 그 아래에 서서 누를 수가 없었다.
        // 위쪽은 비어 있으므로 여기 둔다.
        if (onMakeCard != null) {
            Text(
                if (canMakeCard) "카드 만들기" else "카드 만들기 · 먼저 한 장 뽑기",
                color = PanelDim,
                fontSize = 9.sp,
            )
            CardMakeRow(onMakeCard, enabled = canMakeCard)
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

/**
 * 야채 열두 개. 누르면 그 카드가 한 장 생긴다.
 *
 * **고르기가 아니라 누르는 것이다.** 칩에 선택 표시를 안 두는 이유고, 그래서 같은
 * 야채를 두 번 누르면 두 장이 생긴다 — 도감은 종류별로 한 칸이라 표지만 바뀐다.
 */
@Composable
private fun CardMakeRow(onMakeCard: (CardTemplate) -> Unit, enabled: Boolean) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CARD_TEMPLATES.forEach { t ->
            BreedChip(t.label, selected = false, enabled = enabled) { onMakeCard(t) }
        }
    }
}

/** 패널이 어두워서 미리보기도 같은 바탕에 둔다 — 흰 바탕에서는 글자가 안 보인다. */
@Preview(showBackground = true, backgroundColor = 0xFF101820)
@Composable
private fun CardMakeRowPreview() {
    CardMakeRow(onMakeCard = {}, enabled = true)
}

/** 빌릴 얼굴이 없을 때. 눌러도 안 되는 것이 보여야 한다. */
@Preview(showBackground = true, backgroundColor = 0xFF101820)
@Composable
private fun CardMakeRowDisabledPreview() {
    CardMakeRow(onMakeCard = {}, enabled = false)
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
private fun BreedChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = when {
            selected -> Color.Black
            enabled -> PanelText
            else -> PanelDim
        },
        fontSize = 9.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) PanelPick else Color(0x33FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
