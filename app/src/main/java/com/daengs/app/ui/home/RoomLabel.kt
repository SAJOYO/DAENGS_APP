package com.daengs.app.ui.home

/**
 * 방 앞 이름표에 걸 이름.
 *
 * 사용자가 정한 이름이 있으면 그것이고, 없으면 대표 강아지 이름으로 짓는다.
 * **서버가 지어 주지 않는다** — 받침에 따라 "이네"/"네" 가 갈리는 것은 한국어 규칙이라
 * 앱의 것이고, 서버가 같이 지으면 규칙이 두 벌이 되어 언젠가 갈라진다.
 */
fun roomLabel(chosen: String?, primaryPetName: String?): String =
    chosen?.trim()?.takeIf(String::isNotEmpty) ?: defaultRoomLabel(primaryPetName)

/**
 * 대표 강아지 이름으로 지은 이름표.
 *
 * `네옹` → `네옹이네`, `댕댕` → `댕댕네`. **받침이 있으면 "이네"** 다 — "댕댕이네"는
 * 소리가 안 맞는다.
 *
 * 한글이 아닌 이름(`Max`)은 "네" 만 붙인다. 영문의 받침을 우리가 알 수 없고,
 * 소리를 흉내 내려다 틀리면 자기 개 이름이 이상해 보인다.
 *
 * 등록한 강아지가 없으면 [FALLBACK] 이다. 아무 이름이나 지어내지 않는다.
 */
fun defaultRoomLabel(primaryPetName: String?): String {
    val name = primaryPetName?.trim()?.takeIf(String::isNotEmpty) ?: return FALLBACK
    return name + if (hasFinalConsonant(name.last())) "이네" else "네"
}

/** 아직 아무것도 없을 때. 남의 강아지 이름을 걸어 두는 것보다 낫다. */
const val FALLBACK = "우리집"

/**
 * 한글 음절의 받침 여부.
 *
 * 유니코드에서 한글 음절은 `초성 * 21 * 28 + 중성 * 28 + 종성` 로 배열돼 있다.
 * 그래서 28 로 나눈 나머지가 0 이면 받침이 없다.
 */
private fun hasFinalConsonant(last: Char): Boolean {
    val code = last.code
    if (code !in 0xAC00..0xD7A3) return false
    return (code - 0xAC00) % 28 != 0
}
