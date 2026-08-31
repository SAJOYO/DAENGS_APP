package com.daengs.app.ui.walk

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.pet.Pet
import com.daengs.app.ui.DogAvatar
import com.daengs.app.ui.PawAvatar
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
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
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PinkSoft else CardWhite)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 얼굴 그림이 없는 견종(믹스)은 발자국이다. **아무 얼굴이나 갖다 쓰지 않는다** —
        // 그러면 사용자는 자기 개가 아닌 얼굴을 보게 된다.
        val breed = pet?.breedArt
        if (breed != null) {
            DogAvatar(breed, Modifier.size(28.dp))
        } else {
            PawAvatar(size = 28.dp)
        }
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            color = if (selected) TextDark else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * 데리고 나갈 아이 고르기. **여러 마리를 고를 수 있고 기본은 전부**다.
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
) {
    if (pets.isEmpty()) return
    Column(modifier) {
        Text(
            "누구와 나갈까요?",
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
