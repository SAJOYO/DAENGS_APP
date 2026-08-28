package com.daengs.app.ui.home

import com.daengs.app.miniroom.art.DogBreed
import com.daengs.app.ui.DaengsIcon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 홈 화면에 뿌리는 값 전부. 백엔드 연동이 없으므로 여기 하드코딩이 유일한 출처다.
 * 나중에 실제 API 를 붙일 때 이 파일만 걷어내면 된다.
 */
object HomeDemoData {

    const val DOG_NAME = "네옹"

    /**
     * 대표 견종. 프로필 얼굴이 이걸 따른다.
     *
     * 사용자가 견종을 고르는 화면이 아직 없어서 여기 하나로 박아둔다.
     * [DogBreed.ALL] 첫 항목이라 방에 먼저 들어오는 개와도 같다 —
     * 상단바 얼굴과 방 안 강아지가 따로 놓지 않는다.
     */
    val DOG_BREED = DogBreed.TOY_POODLE_LIGHT_BROWN
    const val ROOM_LABEL = "네옹이네"

    const val TODAY_NOTE = "산책 가기 좋은 날!"
    const val CHAT_TITLE = "댕스 AI 챗봇"
    const val CHAT_PLACEHOLDER = "무엇이든 물어보세요!"
    const val CHAT_MORE = "추천 질문 보기"

    const val WALK_TITLE = "오늘의 산책 요약"
    const val DAILY_WORD_TITLE = "오늘의 한 마디"
    val DAILY_WORD_LINES = listOf("바람이 좋아서", "산책하기 딱 좋은 날이댕!")

    val SUGGESTIONS = listOf(
        "오늘 산책 언제가 좋을까?",
        "강아지 더위 주의사항 알려줘!",
        "간식 추천해줘!",
    )

    data class WalkStat(val icon: DaengsIcon, val value: String, val label: String)

    val WALK_STATS = listOf(
        WalkStat(DaengsIcon.Paws, "1회", "횟수"),
        WalkStat(DaengsIcon.Clock, "32분", "시간"),
        WalkStat(DaengsIcon.Pin, "2.3km", "거리"),
    )

    /** 시안에 박힌 날짜. @Preview 를 결정적으로 만들 때 쓴다. */
    const val MOCK_DATE = "05.20 (화)"

    private val FORMAT = DateTimeFormatter.ofPattern("MM.dd (E)", Locale.KOREAN)

    /** 기기의 오늘 날짜. 로컬 값이라 백엔드 없이도 진짜 날짜가 뜬다. */
    fun todayLabel(date: LocalDate = LocalDate.now()): String = date.format(FORMAT)
}
