package com.daengs.app.gait

import android.content.Context
import android.content.SharedPreferences

/**
 * 보행 기록 제목의 **임시 로컬 override.**
 *
 * ### 왜 로컬인가
 *
 * 서버에는 기록을 고치는 API 가 없다 (`POST /analyze` · confirm · list · detail · delete ·
 * compare 뿐). 처음 정한 제목은 `/analyze` 의 `note` 로 실어 보내 서버에 남지만, **나중에
 * 고친 제목은 갈 곳이 없어** 여기 둔다. 화면은 `로컬 수정본 → 서버 note → "보행 기록"`
 * 순으로 읽는다 ([GaitRecord.displayTitle]).
 *
 * ### 한계 — 반드시 알고 쓸 것
 *
 * 1. **기기 안에만 있다.** 앱을 지우거나 다른 기기에서 로그인하면 고친 제목은 사라지고
 *    서버 note(처음 제목)로 돌아간다.
 * 2. **후속 과제 (DAENGS_dev):** `gait_records` 에 `title` 컬럼과
 *    `PATCH /app/gait/records/{id}` (title 수정) 가 생겨야 한다. 그때 이 저장소는 캐시가
 *    되거나 없어진다.
 * 3. 지금은 서버 `note` 를 제목 용도로 쓴다. **별도 메모 기능이 생기면 title/note 를
 *    갈라야 한다** — 그때 note 에 든 옛 제목을 title 로 옮기는 마이그레이션이 필요하다.
 *
 * `WalkStyleStore` 와 같은 `SharedPreferences` 래퍼다. Room 을 쓰지 않는 이유는 값이
 * `id → 문자열` 하나라서다.
 */
class GaitTitleStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gait-titles", Context.MODE_PRIVATE)

    /** 로컬 수정본. 없으면 null — 그때 화면은 서버 note 나 기본값으로 물러난다. */
    fun get(recordId: String): String? = prefs.getString(recordId, null)?.ifBlank { null }

    /**
     * 제목을 고친다. 빈 값이면 **지운다** — 빈 문자열을 저장해 두면 "제목 없음" 과
     * "기본값" 을 화면이 구분해야 한다. 길이는 [normalize] 가 자른다.
     */
    fun set(recordId: String, title: String?) {
        val value = normalize(title)
        prefs.edit().apply {
            if (value == null) remove(recordId) else putString(recordId, value)
        }.apply()
    }

    /** 지운 기록의 제목도 같이 지운다. 남겨 두면 id 가 재사용될 일은 없어도 쓰레기가 쌓인다. */
    fun remove(recordId: String) {
        prefs.edit().remove(recordId).apply()
    }

    companion object {
        /** 제목 최대 길이. 카드 한 줄에 날짜와 같이 놓이는 자리라 길면 잘린다. */
        const val MAX_LENGTH = 20

        /**
         * 앞뒤 공백을 떼고 [MAX_LENGTH] 로 자른다. 비면 null — "기본값을 쓴다" 는 뜻이다.
         * 입력 다이얼로그와 저장소가 같은 규칙을 쓰도록 한 곳에 둔다.
         */
        fun normalize(title: String?): String? =
            title?.trim()?.take(MAX_LENGTH)?.ifBlank { null }
    }
}
