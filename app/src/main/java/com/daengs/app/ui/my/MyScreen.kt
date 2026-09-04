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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.home.HomeDemoData
import com.daengs.app.ui.common.SettingDivider
import com.daengs.app.ui.common.SettingRow
import com.daengs.app.ui.common.SettingSection
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengPinkDeep
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
    /** 대표 강아지의 견종. **모르면(믹스) null 이고 발자국이 뜬다.** */
    breed: DogBreed?,
    /**
     * 방 둘러보기를 다시 연다. null 이면 그 줄이 안 뜬다 — `@Preview` 와 테스트가
     * 그렇게 부른다.
     */
    onReplayTour: (() -> Unit)? = null,
    /** 방 앞 이름표와 **같은 이름**. 두 곳이 다르면 어느 쪽이 내 방인지 헷갈린다. */
    roomLabel: String,
    /** 내 강아지. null 이면 아직 못 받아 온 것이고, 빈 목록과 다르다. */
    pets: List<Pet>?,
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photoOf: (String) -> ImageBitmap? = { null },
    /**
     * 대표 아이의 프로필 사진을 바꾸러 간다. null 이면 얼굴을 눌러도 아무 일이
     * 없다 — `@Preview` 와 테스트가 그렇게 부른다.
     */
    onEditPhoto: (() -> Unit)? = null,
    canAddMore: Boolean,
    onAddPet: () -> Unit,
    onEditPet: (Pet) -> Unit,
    onPickPrimary: (Pet) -> Unit,
    /**
     * 지우기. **그 아이와만 나간 산책 기록도 같이 지워진다** — 그 말을 확인 창에서
     * 하고 나서 부른다.
     */
    onDeletePet: (Pet) -> Unit,
    /** 아이를 배웅하는 자리로 보낸다. 삭제 창에서도, 아이 카드에서도 여기로 온다 */
    onFarewell: ((Pet) -> Unit)? = null,
    /** 이미 배웅한 아이의 날짜. 없으면 아직 함께 있는 아이다 */
    farewellOf: (Pet) -> java.time.LocalDate? = { null },
    deleteBusy: Boolean,
    deleteError: String?,
    onDismissDelete: () -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onWithdraw: () -> Unit,
    withdrawBusy: Boolean,
    withdrawError: String?,
    onDismissWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    // 어느 아이를 지우려는지. **카드가 아니라 화면이 들고 있다** — 목록이 새로
    // 오면서 카드가 다시 만들어져도 창이 안 닫힌다.
    var deleting by remember { mutableStateOf<Pet?>(null) }

    // 지워지고 나면 창을 닫는다. 목록에서 사라진 것이 곧 성공이다 — 따로 신호를
    // 받지 않아서, 이걸 안 하면 지운 뒤에도 창이 그대로 떠 있다.
    LaunchedEffect(pets) {
        val target = deleting ?: return@LaunchedEffect
        if (pets?.none { it.id == target.id } == true) deleting = null
    }
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        val primary = pets?.firstOrNull { it.isPrimary }
        ProfileHead(
            breed = breed,
            photo = primary?.let { photoOf(it.id) },
            dogName = primary?.name,
            roomLabel = roomLabel,
            // 대표가 있어야 사진을 걸 자리가 있다.
            onEditPhoto = onEditPhoto?.takeIf { primary != null },
        )
        Spacer(Modifier.height(20.dp))

        if (signedIn) {
            PetSection(
                photoOf = photoOf,
                pets = pets,
                canAddMore = canAddMore,
                onAdd = onAddPet,
                onEdit = onEditPet,
                onPickPrimary = onPickPrimary,
                onDelete = { deleting = it },
                onFarewell = onFarewell,
                farewellOf = farewellOf,
            )
            Spacer(Modifier.height(14.dp))
        }

        // 로그인 여부와 무관하게 앱 안에서 언제든 찾을 수 있어야 합니다.
        Section {
            // **실수로 건너뛴 사람이 영영 못 보면 안 된다.** 방 둘러보기는 처음
            // 한 번만 뜨므로 다시 여는 길이 반드시 있어야 한다.
            if (onReplayTour != null) {
                SettingRow("방 둘러보기 다시 보기", onClick = onReplayTour)
                SettingDivider()
            }
            SettingRow("개인정보처리방침", onClick = { openPrivacyPolicy(context) })
        }
        Spacer(Modifier.height(14.dp))

        if (signedIn) {
            SettingSection {
                SettingRow("로그아웃", onClick = onSignOut)
                SettingDivider()
                SettingRow("회원 탈퇴", onClick = { confirming = true }, tint = DaengsColors.Error)
            }
        } else {
            // 눌러도 아무 일 없는 버튼을 두지 않는다 — 로그인 안 한 사람에게
            // "로그아웃"은 비활성이 아니라 **없는 것**이 맞다
            // (LandingScreen 의 canLogin 안내와 같은 규칙).
            SettingSection {
                SettingRow("카카오로 로그인", onClick = onSignIn, tint = DaengPink)
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

    deleting?.let { pet ->
        DeletePetDialog(
            pet = pet,
            busy = deleteBusy,
            error = deleteError,
            onConfirm = { onDeletePet(pet) },
            onDismiss = {
                deleting = null
                onDismissDelete()
            },
            onFarewell = onFarewell?.let {
                {
                    deleting = null
                    onDismissDelete()
                    it(pet)
                }
            },
        )
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
 * 강아지 삭제 확인.
 *
 * **무엇이 같이 지워지는지 말한다.** 그 아이와만 나간 산책은 함께 지워지고, 다른
 * 아이와 같이 나간 산책은 남는다 — 지우고 나서 알게 되면 늦다.
 *
 * 탈퇴 창과 같은 배치다. 되돌릴 수 없는 쪽(삭제)이 왼쪽이고 취소가 오른쪽이다.
 */
@Composable
private fun DeletePetDialog(
    pet: Pet,
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** 배웅하기로 보내는 자리. null 이면 안내가 안 붙는다 */
    onFarewell: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(color = CardWhite, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    "${pet.name}(을)를 지울까요?",
                    color = TextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${pet.name}(와)과만 나간 산책 기록도 함께 지워지고 되돌릴 수 없어요. " +
                        "다른 아이와 같이 나간 산책은 남아요.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                // **배웅하기가 있다고 알려 준다.** 무지개다리를 건넌 아이를 지우려고 온
                // 사람이 이 길을 모르면 산책도 카드도 같이 잃는다 — 그러고 나서 알게
                // 되면 늦다. 여기가 그 사람이 반드시 지나는 자리다.
                if (onFarewell != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(color = PinkFaint, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "무지개다리를 건넜다면",
                                color = TextDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "지우는 대신 배웅할 수 있어요. ${pet.name}(이)는 목록에 " +
                                    "그대로 있고 함께한 기록도 남아요.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "배웅하기",
                                color = DaengPink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !busy, onClick = onFarewell)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
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
                        DialogAction("삭제", DaengsColors.Error, FontWeight.Normal, onConfirm)
                        Spacer(Modifier.width(6.dp))
                        DialogAction("취소", DaengPink, FontWeight.Bold, onDismiss)
                    }
                }
            }
        }
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
private fun ProfileHead(
    breed: DogBreed?,
    photo: ImageBitmap?,
    dogName: String?,
    roomLabel: String,
    onEditPhoto: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // **모르면 발자국이다.** 바로 아래 이름 줄과 같은 규칙이다 — 모르는 것을
        // 아무 것으로나 채우면 남의 강아지가 내 프로필에 앉는다. 올린 사진이 있으면
        // 견종을 몰라도 그 사진이 앞선다.
        PetAvatar(
            photo = photo,
            breed = breed,
            size = 88.dp,
            modifier = if (onEditPhoto == null) Modifier else Modifier.clickable(onClick = onEditPhoto),
        )
        if (onEditPhoto != null) {
            Spacer(Modifier.height(6.dp))
            // **누를 수 있다는 것을 글로 말한다.** 얼굴은 버튼처럼 안 생겼다.
            Text(
                if (photo == null) "사진 올리기" else "사진 바꾸기",
                color = DaengPinkDeep,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEditPhoto)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        // **모르면 비운다.** 여기 남의 강아지 이름이 박혀 있었다 — 로그인 전이거나
        // 등록한 아이가 없으면 이름 줄이 통째로 빠진다.
        if (dogName != null) {
            Text(dogName, color = TextDark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
        }
        Text(roomLabel, color = TextMuted, fontSize = 13.sp)
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
    photoOf: (String) -> ImageBitmap?,
    pets: List<Pet>?,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onEdit: (Pet) -> Unit,
    onPickPrimary: (Pet) -> Unit,
    onDelete: (Pet) -> Unit,
    onFarewell: ((Pet) -> Unit)?,
    farewellOf: (Pet) -> java.time.LocalDate?,
) {
    Text("내 강아지", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

    // null 은 "아직 못 받아 왔다" 다. 빈 목록과 다르게 다뤄야, 잠깐 뜨는 사이에
    // "등록된 강아지가 없어요" 가 번쩍이지 않는다.
    if (pets == null) {
        SettingSection {
            Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = DaengPink, strokeWidth = 2.dp)
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pets.forEach { pet ->
            PetCard(
                pet,
                photo = photoOf(pet.id),
                // **배웅한 아이는 수정이 아니라 그 아이의 자리로.** 몸무게를 고치라고
                // 묻는 화면은 떠난 아이에게 할 말이 아니다.
                onEdit = {
                    if (farewellOf(pet) != null && onFarewell != null) onFarewell(pet)
                    else onEdit(pet)
                },
                onPickPrimary = { onPickPrimary(pet) },
                onDelete = { onDelete(pet) },
                sentOn = farewellOf(pet),
                onFarewell = onFarewell?.let { go -> { go(pet) } },
            )
        }
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
private fun PetCard(
    pet: Pet,
    /** 그 아이가 올린 프로필 사진. 없으면 견종 그림이다. */
    photo: ImageBitmap?,
    onEdit: () -> Unit,
    onPickPrimary: () -> Unit,
    onDelete: () -> Unit,
    /** 배웅한 날. 있으면 이 아이는 떠난 아이다 */
    sentOn: java.time.LocalDate? = null,
    onFarewell: (() -> Unit)? = null,
) {
    Surface(color = CardWhite, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onEdit).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetFace(pet, 46.dp, photo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pet.name, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    // 배웅한 아이. **글자가 아니라 무지개다** — "사망" 같은 말을 목록에
                    // 붙여 두면 매번 그 단어를 읽게 된다.
                    if (sentOn != null) {
                        Spacer(Modifier.width(6.dp))
                        DaengsIconView(DaengsIcon.Rainbow, Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                // 배웅한 아이는 나이·몸무게 대신 **간 날**을 적는다. 떠난 아이에게
                // "3살" 이라고 붙어 있으면 시간이 멈춘 것처럼 읽힌다.
                if (sentOn != null) {
                    Text(
                        "%d년 %d월 %d일에 배웅했어요".format(
                            sentOn.year,
                            sentOn.monthValue,
                            sentOn.dayOfMonth,
                        ),
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(petSubtitle(pet), color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            // **대표 자리는 배웅한 아이에게도 그대로 둔다.** 한 마리만 키우다 보낸
            // 경우 그 아이가 대표일 수밖에 없고, 여러 마리여도 떠난 아이를 대표로
            // 두고 싶을 수 있다. 여기서 막으면 그 선택을 못 하게 된다.
            //
            // 대신 **삭제는 아이의 자리로 옮겼다.** 떠난 아이 옆에 지우기 버튼이 매번
            // 붙어 있는 것과, 그 아이의 화면에서 조용히 고르는 것은 다르다.
            if (sentOn != null) {
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
                return@Row
            }
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
            // 지우기. **눈에 띄되 손이 먼저 가지는 않게** 옅은 글씨다 — 카드를 누르면
            // 고치기이고, 지우기는 한 번 더 묻는다.
            Text(
                "삭제",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
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
private fun PetFace(pet: Pet, size: androidx.compose.ui.unit.Dp, photo: ImageBitmap? = null) {
    PetAvatar(photo, pet.breedArt, size)
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
private fun SettingRow(
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
            HomeDemoData.DOG_BREED, roomLabel = "네옹이네", pets = emptyList(), canAddMore = true,
            onAddPet = {}, onEditPet = {}, onPickPrimary = {},
            onDeletePet = {}, deleteBusy = false, deleteError = null, onDismissDelete = {},
            signedIn = true, onSignIn = {}, onSignOut = {},
            onWithdraw = {}, withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
        )
    }
}

@Preview(widthDp = 411, heightDp = 700, showBackground = true)
@Composable
private fun MyScreenBrowsingPreview() {
    DaengsTheme {
        MyScreen(
            HomeDemoData.DOG_BREED, roomLabel = "우리집", pets = null, canAddMore = false,
            onAddPet = {}, onEditPet = {}, onPickPrimary = {},
            onDeletePet = {}, deleteBusy = false, deleteError = null, onDismissDelete = {},
            signedIn = false, onSignIn = {}, onSignOut = {},
            onWithdraw = {}, withdrawBusy = false, withdrawError = null, onDismissWithdraw = {},
        )
    }
}
