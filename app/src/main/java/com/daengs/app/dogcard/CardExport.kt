package com.daengs.app.dogcard

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 내보낼 카드 파일의 이름.
 *
 * `daengs-cabbage-20260902-1830-3f9a12.png`
 *
 * **아이 이름은 안 넣는다.** 한글·이모지·`/` 가 저장 앱마다 다르게 깨진다 — 파일 이름은
 * 카드 그림 안에 이미 인쇄돼 있는 것을 되풀이하는 자리가 아니라, 파일 관리자에서
 * 다시 찾을 수 있으면 되는 자리다.
 *
 * **카드 id 앞 여섯 자를 붙인다.** 같은 분에 두 장을 저장하면 이름이 겹치는데, SAF 는
 * 겹치면 `(1)` 을 붙이거나 덮어쓴다 — 어느 쪽이 될지는 저장 앱이 정한다. 덮어쓰는 앱을
 * 만나면 방금 저장한 카드가 사라진다.
 *
 * @param at 뽑은 시각. 저장한 시각이 아니다 — 같은 카드를 두 번 저장하면 같은 이름이
 *   나오는 편이 낫다. "언제 저장했나" 보다 "어느 카드인가" 가 파일을 찾는 단서다
 */
fun cardFileName(
    templateId: String,
    cardId: String,
    at: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val stamp = STAMP.format(Instant.ofEpochMilli(at).atZone(zone))
    return "daengs-${slug(templateId)}-$stamp-${slug(cardId).take(6)}.png"
}

/** 파일 이름에 쓸 수 있는 글자만 남긴다. 빈 문자열이 되면 `card` 로 둔다. */
private fun slug(text: String): String =
    text.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-{2,}"), "-")
        .ifEmpty { "card" }

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
