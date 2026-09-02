package com.daengs.app.pet

import androidx.compose.runtime.Immutable
import com.daengs.app.miniroom.art.DogBreed
import org.json.JSONObject
import java.time.LocalDate

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
        )

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
) {
    /**
     * 보낼 수 있는 상태인가.
     *
     * **날짜와 종류는 같이 있거나 같이 없어야 한다.** 서버도 422 로 막지만, 보내기
     * 전에 알면 화면에서 바로 말해 줄 수 있다.
     */
    val valid: Boolean
        get() = name.isNotBlank() &&
            breed.isNotBlank() &&
            (birthDate == null) == (birthDateKind == null)

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
    }
}
