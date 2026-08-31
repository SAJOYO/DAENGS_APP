package com.daengs.app.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.BuildConfig
import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.pet.Pet
import com.daengs.app.ui.theme.PinkFaint
import com.daengs.app.ui.DaengsIcon
import com.daengs.app.ui.DaengsIconView
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted

/**
 * 마이 탭.
 *
 * **[Screen.My][com.daengs.app.MainActivity] 로 밀지 않고 홈 안에서 탭만 바꾼다.**
 * 하단 바는 홈의 `Scaffold` 가 그리므로, 밀어서 열면 마이 탭에서 하단 바가
 * 사라진다 — 자기 탭에서 탭 바가 없어지는 건 고장으로 읽힌다. 도감·산책기록이
 * 밀어서 여는 화면인 것은 그 둘이 전체화면 목적지라서다.
 *
 * 여기 있는 것은 **계정에 관한 것**뿐이다. 강아지 정보(이름·나이 같은)는 아직
 * 받는 항목이 정해지지 않아서 보여 주기만 하고 고치는 칸은 안 만든다 — 무엇을
 * 받을지 정해지기 전에 입력칸부터 만들면 그 모양에 끌려간다.
 *
 * @param signedIn 카카오로 로그인한 상태인가. **출시 빌드에서는 늘 true 다** —
 *   로그인이 필수라 랜딩을 건너뛸 길이 없다. 디버그의 "둘러보기"로 들어왔을 때만
 *   false 이고, 그때는 계정 항목 대신 로그인 버튼이 뜬다.
 * @param onSignIn 둘러보기 상태에서 로그인하러 갈 때. 랜딩으로 되돌리면 기존
 *   카카오 경로를 그대로 쓴다.
 */
@Composable
fun MyScreen(
    breed: DogBreed,
    /** 내 강아지. null 이면 아직 못 받아 온 것이고, 빈 목록과 다르다. */
    pets: List<Pet>?,
    canAddMore: Boolean,
    onAddPet: () -> Unit,
    onEditPet: (Pet) -> Unit,
    onPickPrimary: (Pet) -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onWithdraw: () -> Unit,
    withdrawBusy: Boolean,
    withdrawError: String?,
    onDismissWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ProfileHead(breed)
        Spacer(Modifier.height(20.dp))

        if (signedIn) {
            Section {
                MyRow("로그아웃", onClick = onSignOut)
                RowDivider()
                MyRow("회원 탈퇴", onClick = { confirming = true }, tint = DaengsColors.Error)
            }
        } else {
            // 눌러도 아무 일 없는 버튼을 두지 않는다 — 로그인 안 한 사람에게
            // "로그아웃"은 비활성이 아니라 **없는 것**이 맞다
            // (LandingScreen 의 canLogin 안내와 같은 규칙).
            Section {
                MyRow("카카오로 로그인", onClick = onSignIn, tint = DaengPink)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "둘러보는 중이에요. 로그인하면 기록이 저장돼요.",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            color = TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(18.dp))
    }

    if (confirming) {
        WithdrawDialog(
            busy = withdrawBusy,
            error = withdrawError,
            onConfirm = onWithdraw,
            onDismiss = {
                confirming = false
                onDismissWithdraw()
            },
        )
    }
}

/**
 * 탈퇴 확인.
 *
 * **취소가 오른쪽이고 기본이다.** 되돌릴 수 없는 쪽이 엄지가 반사적으로 닿는
 * 자리에 있으면 안 된다.
 *
 * 진행 중에는 밖을 눌러 닫지 못하게 한다 — 요청이 날아가는 중에 창이 사라지면
 * 사용자는 무슨 일이 벌어졌는지 알 수 없다.
 */
@Composable
private fun WithdrawDialog(
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("정말 탈퇴할까요?", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "계정과 서버에 저장된 기록이 지워지고 되돌릴 수 없어요. " +
                        "이 기기에 꾸며둔 방과 소품 배치도 함께 지워집니다.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = DaengsColors.Error, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(18.dp))
                if (busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = DaengPink, strokeWidth = 2.dp)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DialogAction("탈퇴", DaengsColors.Error, FontWeight.Normal, onConfirm)
                        Spacer(Modifier.width(6.dp))
                        DialogAction("취소", DaengPink, FontWeight.Bold, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, tint: Color, weight: FontWeight, onClick: () -> Unit) {
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

/** 줄 사이 가는 선. */
@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(DaengsColors.BorderNeutral),
    )
}

@Composable
private fun ProfileHead(breed: DogBreed) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DogAvatar(breed, Modifier.size(88.dp))
        Spacer(Modifier.height(10.dp))
        Text(HomeDemoData.DOG_NAME, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(HomeDemoData.ROOM_LABEL, color = TextMuted, fontSize = 13.sp)
    }
}

/**
 * 내 강아지 카드 목록.
 *
 * **대표를 여기서 고른다.** 상단바·챗봇 얼굴이 대표를 따르는데, 어차피 내가
 * 키우는 아이 중에서 고르는 것이라 견종 목록을 늘어놓는 것보다 이쪽이 맞다.
 *
 * 카드를 누르면 고치기, 대표 자리를 누르면 대표가 바뀐다. 대표는 이미 대표인
 * 카드에서는 눌러도 아무 일이 없어야 해서 표시만 한다.
 */
@Composable
private fun PetSection(
    pets: List<Pet>?,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onEdit: (Pet) -> Unit,
    onPickPrimary: (Pet) -> Unit,
) {
    Text("내 강아지", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

    // null 은 "아직 못 받아 왔다" 다. 빈 목록과 다르게 다뤄야, 잠깐 뜨는 사이에
    // "등록된 강아지가 없어요" 가 번쩍이지 않는다.
    if (pets == null) {
        Section {
            Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = DaengPink, strokeWidth = 2.dp)
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pets.forEach { pet -> PetCard(pet, onEdit = { onEdit(pet) }, onPickPrimary = { onPickPrimary(pet) }) }
        if (canAddMore) {
            Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+ 강아지 추가", color = DaengPink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PetCard(pet: Pet, onEdit: () -> Unit, onPickPrimary: () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onEdit).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetFace(pet, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.name, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(petSubtitle(pet), color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (pet.isPrimary) {
                Text("대표", color = DaengPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    "대표로",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onPickPrimary)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * 강아지 얼굴. **모르는 견종(믹스 등)이면 발자국으로 대신한다.**
 *
 * 서버가 견종 어휘를 검사하지 않아서, 우리 그림에 없는 값이 올 수 있다.
 * 아무 얼굴이나 골라 보여 주면 사용자는 자기 개가 아닌 얼굴을 보게 된다.
 */
@Composable
private fun PetFace(pet: Pet, size: androidx.compose.ui.unit.Dp) {
    val art = pet.breedArt
    if (art != null) {
        DogAvatar(art, Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(50)).background(PinkFaint),
            contentAlignment = Alignment.Center,
        ) { DaengsIconView(DaengsIcon.Paw, Modifier.size(size * 0.5f), tint = DaengPink) }
    }
}

/** 아는 것만 적는다. 모르는 항목은 줄에서 빠진다 — 빈 자리를 "-" 로 채우지 않는다. */
private fun petSubtitle(pet: Pet): String {
    val parts = buildList {
        pet.breedArt?.label?.let { add(it) } ?: add("믹스")
        pet.sex?.let { add(if (it == Pet.Sex.MALE) "남아" else "여아") }
        pet.weightKg?.let { add("${it}kg") }
    }
    return parts.joinToString(" · ")
}

/** 카드 한 장. 안의 줄들이 같은 흰 바탕을 나눠 쓴다. */
@Composable
private fun Section(content: @Composable () -> Unit) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = { content() })
    }
}

/**
 * 누르는 줄 하나.
 *
 * Material3 `Button` 을 안 쓴다 — 이 저장소는 `Surface`·`Box` 에 `.clickable` 을
 * 붙여 직접 짠다 (`LandingScreen` 과 같은 결).
 */
@Composable
private fun MyRow(
    label: String,
    onClick: () -> Unit,
    tint: Color = TextDark,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tint, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        DaengsIconView(DaengsIcon.ChevronRight, Modifier.size(16.dp), tint = TextMuted)
    }
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true)
@Composable
private fun MyScreenSignedInPreview() {
    DaengsTheme {
        MyScreen(
            HomeDemoData.DOG_BREED, pets = emptyList(), canAddMore = true,
            onAddPet = {}, onEditPet = {}, onPickPrimary = {}, signedIn = true, onSignIn = {}, onSignOut = {},
            onWithdraw = {}, withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
        )
    }
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true)
@Composable
private fun MyScreenBrowsingPreview() {
    DaengsTheme {
        MyScreen(
            HomeDemoData.DOG_BREED, pets = null, canAddMore = false,
            onAddPet = {}, onEditPet = {}, onPickPrimary = {}, signedIn = false, onSignIn = {}, onSignOut = {},
            onWithdraw = {}, withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
        )
    }
}
