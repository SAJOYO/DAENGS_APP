package com.daengs.app.ui.home

import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈 화면의 **고정 문구**와 @Preview 용 값.
 *
 * 원래는 "홈 화면에 뿌리는 값 전부" 였다. 지금은 아니다 — 산책 숫자(`WalkDayTotals`),
 * 방 이름표(`roomLabel`), 날씨 문구(`homeWeatherWords`), 강아지 이름·얼굴(`Pet`)이
 * 하나씩 진짜 값으로 나갔다.
 *
 * **여기 남은 것은 데이터가 아니라 라벨이다.** 사람이 정한 말이고 서버가 주는 값이
 * 아니라서 상수인 것이 맞다. 화면에 뜨는 사실(숫자·이름·날씨)을 여기 새로 넣지 말 것 —
 * 그러면 카드가 또 거짓말을 시작한다.
 */
object HomeDemoData {

    /**
     * 대표 견종. 프로필 얼굴이 이걸 따른다.
     *
     * 사용자가 견종을 고르는 화면이 아직 없어서 여기 하나로 박아둔다.
     * [DogBreed.ALL] 첫 항목이라 방에 먼저 들어오는 개와도 같다 —
     * 상단바 얼굴과 방 안 강아지가 따로 놓지 않는다.
     */
    val DOG_BREED = DogBreed.TOY_POODLE_LIGHT_BROWN
    /** @Preview 전용. 진짜 이름표는 계정에 저장되고 [roomLabel] 이 정한다. */
    const val ROOM_LABEL = "네옹이네"

    const val CHAT_TITLE = "댕스 AI 챗봇"
    const val CHAT_PLACEHOLDER = "무엇이든 물어보세요!"

    const val WALK_TITLE = "오늘의 산책 요약"
    /** 제목만 고정이다. 내용은 날씨를 따른다 ([homeWeatherWords]). */
    const val DAILY_WORD_TITLE = "오늘의 한 마디"

    /** 카드의 한 칸. 값은 [com.daengs.app.walk.WalkDayTotals] 에서 오지 여기 박혀 있지 않다. */
    data class WalkStat(val icon: DaengsIcon, val value: String, val label: String)

    /** 시안에 박힌 날짜. @Preview 를 결정적으로 만들 때 쓴다. */
    const val MOCK_DATE = "05.20 (화)"

    private val FORMAT = DateTimeFormatter.ofPattern("MM.dd (E)", Locale.KOREAN)

    /** 기기의 오늘 날짜. 로컬 값이라 백엔드 없이도 진짜 날짜가 뜬다. */
    fun todayLabel(date: LocalDate = LocalDate.now()): String = date.format(FORMAT)
}
