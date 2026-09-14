package com.daengs.app.pet

import androidx.compose.runtime.Immutable
import com.daengs.app.miniroom.art.DogBreed
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 등록한 강아지 하나. 계약은 저쪽 `DAENGS_dev` 의 `schemas/pet.py` 다.
 *
 * **모르는 것은 null 이다.** 성별·중성화·생일이 그렇고, **null 을 false 나 기본값으로
 * 바꾸지 않는다** — 안 물어본 것과 아니라고 답한 것은 다르다. 저쪽 스키마 주석이
 * 같은 말을 하고 있다.
 */
@Immutable
data class Pet(
    val id: String,
    val name: String,
    /**
     * 견종. **[DogBreed] 가 아니라 문자열이다.**
     *
     * 서버가 어휘를 검사하지 않고("어휘의 주인은 앱"), 우리 목록에 없는 값
     * — 믹스(`mix`) 나 앱을 업데이트하기 전에 저쪽에서 늘어난 값 — 이 올 수 있다.
     * enum 으로 받으면 그때 파싱이 통째로 깨진다. 얼굴을 그릴 때만 [breedArt] 로
     * 옮기고, 못 옮기면 대체 얼굴을 쓴다.
     */
    val breed: String,
    val sex: Sex?,
    val neutered: Boolean?,
    val weightKg: Float?,
    val birthDate: LocalDate?,
    val birthDateKind: BirthDateKind?,
    /**
     * 배웅한 날. **null 이면 아직 함께 있는 아이다.**
     *
     * 삭제와 다른 값이다 — 이 날짜가 차도 아이는 목록에 남고 함께한 산책도 카드도 남는다.
     */
    val farewellOn: LocalDate? = null,
    val isPrimary: Boolean,
    /** 이 아이의 대표 보호자인가. false 면 공동 돌봄으로 참여한 아이이며 프로필 변경은 못 한다. */
    val isOwner: Boolean = true,
    /**
     * 이 아이가 속한 **논리 그룹**의 주보호자인가.
     *
     * **[isOwner] 와 다른 값이다.** 내가 등록한 아이라도 남의 아이와 연결되면 그룹의
     * 주보호자는 초대한 쪽이고, 나는 내 행의 대표이면서 그룹에서는 공동 보호자다.
     * 연결이 없는 아이에서는 둘이 **언제나 같다**.
     *
     * 그룹 관리 UI(공통 정보 수정·삭제·초대·내보내기·승계)는 **이 값**으로 가른다.
     * [isOwner] 로 가르면 연결된 공동 보호자에게 버튼이 뜨고, 누르면 서버가 409
     * (`not_group_owner`) 를 낸다.
     *
     * **기본값이 [isOwner] 인 것이 중요하다.** 그냥 `true` 로 두면 돌보미 아이를 코드로
     * 만들 때 `isOwner=false, isGroupOwner=true` 라는 있을 수 없는 객체가 나온다 —
     * 연결이 없으면 두 값은 언제나 같다.
     */
    val isGroupOwner: Boolean = isOwner,
    /** 이 아이의 정보가 마지막으로 바뀐 시각. **아래 [photoUpdatedAt] 과 다른 값이다.** */
    val updatedAt: String? = null,
    /**
     * 서버에 프로필 사진이 있나.
     *
     * **주소가 아니라 있다/없다만 온다.** 목록에 주소를 실으면 서버가 아이마다 저장소를
     * 두드리게 되고, 화면이 안 그리는 아이 것까지 만든다. 주소가 필요하면 그때 따로 받는다.
     */
    val hasPhoto: Boolean = false,
    /**
     * **사진이** 마지막으로 바뀐 시각. 캐시 열쇠다.
     *
     * ⚠️ [updatedAt] 과 헷갈리면 안 된다 — 그쪽은 이름·몸무게를 고쳐도 바뀌고,
     * 이쪽은 **사진을 바꿔야** 바뀐다. 이름만 고쳤는데 사진을 다시 받으면 안 된다.
     *
     * 기기에 받아 둔 사진 옆에 이 값을 적어 두고(`PetPhotos.stamp`), 같으면 다시 안
     * 받는다. **시각으로 파싱하지 않는다** — 우리가 계산할 값이 아니라 서버 문자열을
     * 그대로 비교하는 열쇠라, 파싱하면 형식이 바뀌는 날 조용히 안 맞게 된다.
     */
    val photoUpdatedAt: String? = null,
    // ── 돌봄 (#200 · 저쪽 #331) ─────────────────────────────────────────
    // 넷 다 **null 이 '모름'** 이다. 약 칸이 비어 있다고 "약 안 먹는 아이" 가 아니다.
    // 옛 서버(#331 전)는 이 칸을 아예 안 주므로 기본값이 있어야 파싱이 안 깨진다.
    /** 자율급식인지 시간제인지. 비서가 "밥 몇 번 줘요?" 류에 이 아이 기준으로 답한다. */
    val feedingStyle: FeedingStyle? = null,
    /**
     * 시간제일 때의 급식 시각. **시간제가 아니면 null 이다** — 서버 CHECK 가 그렇다.
     * 시간제인데 아직 안 정했으면 null 이고, 빈 목록은 서버가 422 로 막는다 (`min_length=1`).
     */
    val feedingTimes: List<LocalTime>? = null,
    /** 앓는 병. 자유 텍스트, 비서 프롬프트에 그대로 실린다. */
    val healthConditions: String? = null,
    /** 정기 복용 약. 비서에는 이름이 아니라 **복약 여부만** 간다 (저쪽 `dog_context.py`). */
    val medications: String? = null,
) {
    /** 이 견종의 얼굴 그림. 모르는 견종(믹스 등)이면 null 이고, 화면이 대체 얼굴을 쓴다. */
    val breedArt: DogBreed? get() = DogBreed.byId(breed)

    /**
     * 미니룸에서 이 아이가 설 모습.
     *
     * 얼굴([breedArt])과 달리 **null 이 없다.** 방 강아지는 걷고 앉는 전신 시트라
     * 대체할 중립 그림이 없어서, 모르는 견종은 [DogBreed.roomStandIn] 이 대역을 준다 —
     * 안 세우면 자기 강아지가 방에서 사라진다.
     */
    val roomBreed: DogBreed get() = breedArt ?: DogBreed.roomStandIn(id)

    enum class Sex { MALE, FEMALE }

    /**
     * 생일 칸에 든 날짜가 무슨 날인가.
     *
     * 태어난 날을 모르면 가족이 된 날로 대신 받는데, **어느 쪽인지를 같이 두어야**
     * "세 살이에요"와 "함께한 지 2년이에요"를 구분할 수 있다.
     */
    enum class BirthDateKind { BIRTHDAY, FAMILY_DAY }

    /** 급식 방식. `free` 자율급식 · `scheduled` 시간제 (`feedingTimes` 에 시각). */
    enum class FeedingStyle { FREE, SCHEDULED }

    /**
     * 지금 값을 그대로 담은 초안.
     *
     * **서버가 PUT 이라 필요하다.** 한 칸만 바꾸려 해도 나머지를 다 실어 보내야 하는데,
     * 부르는 쪽마다 손으로 옮겨 적으면 언젠가 한 칸을 빠뜨리고 그 칸이 null 로 덮인다.
     */
    fun toDraft(): PetDraft = PetDraft(
        name = name,
        breed = breed,
        sex = sex,
        neutered = neutered,
        weightKg = weightKg,
        birthDate = birthDate,
        birthDateKind = birthDateKind,
        farewellOn = farewellOn,
        feedingStyle = feedingStyle,
        feedingTimes = feedingTimes,
        healthConditions = healthConditions,
        medications = medications,
    )

    companion object {
        fun parse(json: JSONObject): Pet = Pet(
            id = json.getString("id"),
            name = json.getString("name"),
            breed = json.getString("breed"),
            sex = when (json.optStringOrNull("sex")) {
                "male" -> Sex.MALE
                "female" -> Sex.FEMALE
                else -> null
            },
            neutered = if (json.isNull("neutered")) null else json.getBoolean("neutered"),
            // ⚠️ **문자열로 온다.** 저쪽이 `Decimal` 이라 pydantic 이 정밀도를 지키려고
            //    "12.3" 처럼 따옴표를 씌워 내보낸다. `optDouble` 로 읽으면 NaN 이다.
            weightKg = json.optStringOrNull("weight_kg")?.toFloatOrNull(),
            birthDate = json.optStringOrNull("birth_date")?.let(LocalDate::parse),
            birthDateKind = when (json.optStringOrNull("birth_date_kind")) {
                "birthday" -> BirthDateKind.BIRTHDAY
                "family_day" -> BirthDateKind.FAMILY_DAY
                else -> null
            },
            farewellOn = json.optStringOrNull("farewell_on")?.let(LocalDate::parse),
            isPrimary = json.optBoolean("is_primary"),
            // #388 전 서버에는 필드가 없다. 배포 순서 동안 기존 소유 아이를 돌보미로 오인하지 않는다.
            isOwner = json.optBoolean("is_owner", true),
            // 다중 초대 전 서버에는 없다. **기본값을 `is_owner` 로 둔다** — 연결이 없으면
            // 둘이 같은 값이라, 구 서버에서도 그룹 판정이 지금까지와 똑같이 나온다.
            isGroupOwner = json.optBoolean("is_group_owner", json.optBoolean("is_owner", true)),
            updatedAt = json.optStringOrNull("updated_at"),
            hasPhoto = json.optBoolean("has_photo"),
            photoUpdatedAt = json.optStringOrNull("photo_updated_at"),
            feedingStyle = when (json.optStringOrNull("feeding_style")) {
                "free" -> FeedingStyle.FREE
                "scheduled" -> FeedingStyle.SCHEDULED
                else -> null
            },
            feedingTimes = json.optJSONArray("feeding_times")?.let { arr ->
                // 서버가 `HH:MM` 만 받으므로 못 읽는 값은 없어야 하지만, 하나 때문에 목록이
                // 통째로 안 뜨느니 그 하나만 버린다.
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        LocalTime.parse(arr.getString(i), FEEDING_TIME)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }.takeIf { it.isNotEmpty() }
            },
            healthConditions = json.optStringOrNull("health_conditions"),
            medications = json.optStringOrNull("medications"),
        )

        /** 급식 시각의 모양. 저쪽 `_FEEDING_TIME` (`HH:MM`) 과 같다 — 초는 안 보낸다. */
        val FEEDING_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** `optString` 은 JSON null 에도 빈 문자열을 준다. 0 과 "없음"을 구분해야 한다. */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}

/** 등록·수정에 보내는 값. 수정은 **전체를 다시 보낸다** (서버가 PUT 만 받는다). */
@Immutable
data class PetDraft(
    val name: String,
    val breed: String,
    val sex: Pet.Sex? = null,
    val neutered: Boolean? = null,
    val weightKg: Float? = null,
    val birthDate: LocalDate? = null,
    val birthDateKind: Pet.BirthDateKind? = null,
    /**
     * 배웅한 날.
     *
     * ⚠️ **수정 화면도 이 값을 그대로 실어 보내야 한다.** 서버가 PATCH 가 아니라 PUT
     * 이라 안 보낸 칸은 null 로 덮인다 — 몸무게 한 번 고쳤다고 배웅한 날이 지워지면
     * 안 된다.
     */
    val farewellOn: LocalDate? = null,
    // 돌봄 (#200). 넷 다 null 이 모름이다 — [Pet] 의 같은 칸 주석 참고.
    val feedingStyle: Pet.FeedingStyle? = null,
    /** 빈 목록은 null 로 보낸다 — 서버가 `min_length=1` 이라 `[]` 는 422 다. */
    val feedingTimes: List<LocalTime>? = null,
    val healthConditions: String? = null,
    val medications: String? = null,
) {
    /**
     * 보낼 수 있는 상태인가.
     *
     * **날짜와 종류는 같이 있거나 같이 없어야 한다.** 서버도 422 로 막지만, 보내기
     * 전에 알면 화면에서 바로 말해 줄 수 있다.
     *
     * **급식 시각은 시간제일 때만** 있을 수 있고, 병·약은 [CARE_TEXT_MAX] 자까지다 —
     * 둘 다 서버가 같은 규칙으로 422 를 준다.
     */
    val valid: Boolean
        get() = name.isNotBlank() &&
            breed.isNotBlank() &&
            (birthDate == null) == (birthDateKind == null) &&
            (feedingTimes.isNullOrEmpty() || feedingStyle == Pet.FeedingStyle.SCHEDULED) &&
            healthConditions.orEmpty().trim().length <= CARE_TEXT_MAX &&
            medications.orEmpty().trim().length <= CARE_TEXT_MAX

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name.trim())
        put("breed", breed)
        put("sex", sex?.let { if (it == Pet.Sex.MALE) "male" else "female" } ?: JSONObject.NULL)
        put("neutered", neutered ?: JSONObject.NULL)
        put("weight_kg", weightKg ?: JSONObject.NULL)
        put("birth_date", birthDate?.toString() ?: JSONObject.NULL)
        put("farewell_on", farewellOn?.toString() ?: JSONObject.NULL)
        put(
            "birth_date_kind",
            when (birthDateKind) {
                Pet.BirthDateKind.BIRTHDAY -> "birthday"
                Pet.BirthDateKind.FAMILY_DAY -> "family_day"
                null -> JSONObject.NULL
            },
        )
        put(
            "feeding_style",
            when (feedingStyle) {
                Pet.FeedingStyle.FREE -> "free"
                Pet.FeedingStyle.SCHEDULED -> "scheduled"
                null -> JSONObject.NULL
            },
        )
        put(
            "feeding_times",
            feedingTimes?.takeIf { it.isNotEmpty() }
                ?.let { times -> JSONArray(times.map { it.format(Pet.FEEDING_TIME) }) }
                ?: JSONObject.NULL,
        )
        // 공백만 적은 것은 **모름** 이다. 빈 문자열이 저장되면 비서가 "복약 중" 으로 읽는다.
        put("health_conditions", healthConditions?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        put("medications", medications?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
    }

    companion object {
        /** 병·약 텍스트의 상한. 저쪽 `CARE_TEXT_MAX` 와 같은 수 — 비서 프롬프트에 실리는 값이라 무한정 안 받는다. */
        const val CARE_TEXT_MAX = 200
    }
}
