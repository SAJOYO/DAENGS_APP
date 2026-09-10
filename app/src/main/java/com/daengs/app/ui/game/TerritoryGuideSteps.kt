package com.daengs.app.ui.game

/** Fixed examples, never actual ownership or a calculated award for the signed-in member. */
internal enum class GuideScenario(val label: String, val steps: List<GuideStep>) {
    EMPTY("빈 전봇대", GuideStep.entries),
    TAKEOVER("다른 주인 영역", GuideStep.entries.filter { it != GuideStep.MARKED }),
}

internal enum class GuideStep {
    APPROACH, SELECT, ACTION, MARKED, PHOTO, RESULT;

    fun title(scenario: GuideScenario): String = when (this) {
        APPROACH -> "산책을 시작하고 가까이 가요"
        SELECT -> "지도 위 전봇대를 눌러요"
        ACTION -> if (scenario == GuideScenario.EMPTY) "두부로 영역표시해요" else "사진으로 다른 주인에게 도전해요"
        MARKED -> "두부의 첫 영역, 기본 +20점"
        PHOTO -> "강아지와 전봇대를 함께 찍어요"
        RESULT -> if (scenario == GuideScenario.EMPTY) "인증 성공! 기본 점수 총 100점" else "탈취 성공! 이번에는 +120점"
    }

    fun action(scenario: GuideScenario): String = when (this) {
        APPROACH -> "가까이 가기"
        SELECT -> "전봇대 누르기"
        ACTION -> if (scenario == GuideScenario.EMPTY) "영역표시" else "영역표시 인증 촬영"
        MARKED -> "영역표시 인증 촬영"
        PHOTO -> "촬영하고 산책 계속"
        RESULT -> "처음부터 다시 보기"
    }

    fun detail(scenario: GuideScenario): String = when (this) {
        APPROACH -> "게임이 열리면 강아지와 산책을 시작하고 점령 지도를 확인해요. 전봇대를 누르거나 가까이 가면 범위가 보여요.\n\n영역표시는 20m, 사진 인증은 10m 안에서 가능해요. GPS 오차도 포함하므로 실제로 충분히 가까이 가 주세요."
        SELECT -> "예시의 두부는 전봇대 8m 안에 도착했어요. 위 전봇대를 눌러 주인과 사용할 수 있는 행동을 확인해 보세요. 범위 안이어도 산책·위치·보호 상태에 따라 행동이 제한될 수 있어요."
        ACTION -> if (scenario == GuideScenario.EMPTY)
            "주인이 없는 전봇대예요. 함께 걷는 강아지 중 두부를 선택하고 ‘영역표시’를 눌러요. 가까이 도착하는 것만으로는 점령되지 않아요.\n\n이 예시는 이번 시즌 이 장소에서 아직 기본 점수를 받지 않은 회원이에요."
        else "초코는 다른 회원의 강아지이고, 아직 미인증 주인이에요. ‘영역표시 인증 촬영’으로 도전해요. 인증된 주인도 보호 시간이 끝나면 같은 방법으로 도전해요.\n\n인증 직후 10분 동안은 보호돼요. 버튼이 열리지 않으면 남은 시간과 거리를 확인해 주세요."
        MARKED -> "서버에서 점령이 확인되면 주인이 두부로 바뀌고 기본 20점이 생겨요.\n\n이어서 10m 안에서 카메라 버튼 ‘영역표시 인증 촬영’을 눌러 인증할 수 있어요. 빈 전봇대에서는 사진 인증부터 바로 시작하는 것도 가능해요."
        PHOTO -> "촬영 화면에 두부와 전봇대 주변 모습이 함께 나오도록 담아요. 예전 사진 대신 현장에서 새로 촬영해요.\n\n‘촬영하고 산책 계속’을 누르면 사진을 보내고 결과를 기다려요. 점령·점수는 서버에서 인증 성공을 확인한 뒤 확정돼요."
        RESULT -> if (scenario == GuideScenario.EMPTY)
            "예시에서 먼저 받은 20점에 남은 80점을 더해, 두부가 이곳의 기본 점수 100점을 채웠어요. 인증 영역은 시간당 보유 점수 10점도 쌓여요.\n\n기본 한도는 회원·장소·시즌별 100점이라 강아지를 바꾸거나 다시 점령해도 새로 100점을 받지는 않아요."
        else "주인이 초코에서 두부로 바뀌었어요. 이 회원이 이번 시즌 이곳에서 기본 점수를 받은 적이 없어 기본 100점과 탈취 보너스 20점을 받는 예시예요.\n\n기본 20점을 이미 받았다면 이번에는 +100점, 100점을 채웠다면 +20점이에요. 다른 회원의 영역 인증 탈취 보너스는 성공할 때마다 받아요. 같은 회원의 다른 강아지에게는 탈취 보너스가 없어요."
    }
}
