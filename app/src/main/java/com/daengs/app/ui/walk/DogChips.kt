package com.daengs.app.ui.walk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PetAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.DaengPink
import com.daengs.app.ui.theme.DaengsColors
import com.daengs.app.ui.theme.DaengsTheme
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import com.daengs.app.walk.WalkSummary

/**
 * 강아지 얼굴 하나 + 이름.
 *
 * 산책을 시작할 때 **데리고 나갈 아이를 고르는 자리**와, 지난 산책을 **아이별로
 * 거르는 자리**가 같은 그림을 쓴다. 두 곳에서 같은 아이가 다르게 보이면 같은
 * 아이인 줄 모른다.
 */
@Composable
fun DogChip(
    name: String,
    pet: Pet?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 사용자가 올린 프로필 사진. 있으면 견종 그림 대신 이게 뜬다. */
    photo: ImageBitmap? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PinkSoft else CardWhite)
            // **테두리와 체크를 따로 둔다.** 예전에는 바탕색만 달랐는데
            // 연분홍(PinkSoft)과 흰색(CardWhite)이라 밝은 곳에서 거의 같아 보였다.
            // 그래서 "골라져 있다" 를 못 읽고 데려갈 아이를 눌러 **빼 버린** 사고가
            // 났다 (`WalkDogPick.kt`). 색 하나에 기대지 않는다.
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) DaengPink else DaengsColors.BorderNeutral,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 올린 사진이 있으면 그 사진, 없으면 견종 그림, 견종도 모르면(믹스) 발자국이다.
        // **아무 얼굴이나 갖다 쓰지 않는다** — 그러면 자기 개가 아닌 얼굴을 보게 된다.
        PetAvatar(photo, pet?.breedArt, 28.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            color = if (selected) TextDark else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (selected) {
            Spacer(Modifier.width(6.dp))
            // 글자로 그린다. 이 저장소는 아이콘 세트를 안 쓰고 상단바의 "＋" 도
            // 같은 방식이다 — 그림 하나를 더 들이지 않는다.
            Text("✓", color = DaengPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 데리고 나갈 아이 고르기. **여러 마리를 고를 수 있고, 기본은 마릿수로 갈린다**
 * ([walkDogPickLabel] 과 `WalkDogPick.kt`).
 *
 * 한 마리만 기르는 사람에게는 그 아이가 이미 골라진 채로 보인다 — 매번 누르게 하면
 * 문을 열 때마다 한 번씩 더 눌러야 한다.
 *
 * 아무도 안 골라도 시작을 막지 않는다. 기록은 남기되 아이가 안 붙는다 —
 * 사람이 걸은 것은 걸은 것이다.
 */
@Composable
fun DogPickRow(
    pets: List<Pet>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 그 아이가 올린 프로필 사진. 기본값은 견종 그림만 쓰는 예전 모습이다. */
    photoOf: (String) -> ImageBitmap? = { null },
) {
    if (pets.isEmpty()) return
    Column(modifier) {
        // **묻지 않고 상태를 말한다.** 예전에는 늘 "누구와 나갈까요?" 였는데, 전부
        // 골라진 채로 그렇게 물으니 아직 아무도 안 골라진 줄로 읽혔다.
        Text(
            walkDogPickLabel(pets, selected),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pets.forEach { pet ->
                DogChip(
                    name = pet.name,
                    pet = pet,
                    selected = pet.id in selected,
                    onClick = { onToggle(pet.id) },
                    photo = photoOf(pet.id),
                )
            }
        }
    }
}

/**
 * 지난 산책을 아이별로 거르기. **한 번에 한 아이**다.
 *
 * 여러 아이를 동시에 거르면 "둘 다 나간 산책"인지 "둘 중 하나라도 나간 산책"인지가
 * 애매해진다. 묻고 싶은 것은 늘 "이 아이는 얼마나 걸었나"라서 하나로 둔다.
 */
@Composable
fun DogFilterRow(
    pets: List<Pet>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** 그 아이가 올린 프로필 사진. 기본값은 견종 그림만 쓰는 예전 모습이다. */
    photoOf: (String) -> ImageBitmap? = { null },
) {
    if (pets.size < 2) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "전체",
            color = if (selectedId == null) TextDark else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selectedId == null) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selectedId == null) PinkSoft else CardWhite)
                // 옆의 [DogChip] 과 **같은 표시 규칙**을 쓴다. 한 줄에 나란히 서는데
                // 하나만 테두리가 없으면 고른 것이 무엇인지 더 헷갈린다.
                .border(
                    width = if (selectedId == null) 2.dp else 1.dp,
                    color = if (selectedId == null) DaengPink else DaengsColors.BorderNeutral,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable { onSelect(null) }
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
        pets.forEach { pet ->
            DogChip(
                name = pet.name,
                pet = pet,
                selected = pet.id == selectedId,
                // 고른 아이를 다시 누르면 전체로 돌아온다. 필터를 푸는 길이 하나 더 있다.
                onClick = { onSelect(pet.id.takeIf { it != selectedId }) },
                photo = photoOf(pet.id),
            )
        }
    }
}

/**
 * 그 산책에 나간 아이들의 **지금 이름**.
 *
 * 기록에는 id 만 있다. 이름은 바뀌는 값이라 산책에 박아 두면 개명한 뒤에도 옛 이름이
 * 뜬다.
 *
 * **모르는 id 는 지어내지 않는다.** 지운 강아지의 산책은 그 자리가 통째로 빈다 —
 * "알 수 없는 아이" 같은 말을 채우면 없는 사실을 만드는 것이다.
 */

/**
 * 이 아이와 나간 산책만. [dogId] 가 null 이면 거르지 않는다.
 *
 * **로컬에서 거른다.** 서버에 다시 묻지 않는다 — 목록은 이미 다 읽어 왔고, 아이를
 * 바꿔 누를 때마다 왕복하면 밖에서(네트워크가 제일 나쁜 데서) 느려진다.
 */
fun List<WalkSummary>.walkedWith(dogId: String?): List<WalkSummary> =
    if (dogId == null) this else filter { dogId in it.dogIds }

fun dogNames(dogIds: List<String>, pets: List<Pet>): List<String> {
    if (dogIds.isEmpty() || pets.isEmpty()) return emptyList()
    val byId = pets.associateBy { it.id }
    return dogIds.mapNotNull { byId[it]?.name }
}

@Preview(widthDp = 411, showBackground = true, backgroundColor = 0xFFFDF4F0)
@Composable
private fun DogPickRowPreview() {
    DaengsTheme {
        Column(Modifier.background(CreamBg).padding(14.dp)) {
            // **두 마리 이상일 때의 새 기본값이다** — 아무도 안 골라져 있다.
            // 여기서 볼 것: 고른 것과 안 고른 것이 한눈에 갈리는가. 예전에는 바탕색만
            // 달라서(연분홍/흰색) 안 갈렸고, 그래서 사고가 났다 (`WalkDogPick.kt`).
            DogPickRow(pets = previewPets(), selected = emptySet(), onToggle = {})
            Spacer(Modifier.padding(6.dp))
            DogPickRow(
                pets = previewPets(),
                selected = previewPets().map { it.id }.toSet(),
                onToggle = {},
            )
            Spacer(Modifier.padding(6.dp))
            // 한 마리만 골랐을 때.
            DogPickRow(pets = previewPets(), selected = setOf("p2"), onToggle = {})
            Spacer(Modifier.padding(6.dp))
            DogFilterRow(pets = previewPets(), selectedId = "p1", onSelect = {})
        }
    }
}

private fun previewPets(): List<Pet> = listOf(
    Pet(
        id = "p1",
        name = "네옹",
        breed = "dog_beagle",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = true,
    ),
    Pet(
        id = "p2",
        name = "댕댕",
        breed = "mix",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = false,
    ),
)
