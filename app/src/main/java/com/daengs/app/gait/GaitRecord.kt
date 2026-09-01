package com.daengs.app.gait

import android.graphics.Bitmap
import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 보행 영상 한 편.
 *
 * **판단하지 않는다.** `CONTEXT.md` 8절이 이 기능의 성격을 못박아 뒀다 — 영상은
 * "기록·비교용" 이고 "이상 있음/없음" 같은 문구를 내보내지 않는다. 그래서 이 모델에는
 * 점수·등급·정상 여부를 담을 필드가 **아예 없다.** 피부 진단이 병명 필드를 안 만드는
 * 것과 같은 방식이다 — 자리가 없으면 화면이 지어내지 못한다.
 *
 * 대신 [comparable] 하나만 둔다. 이건 건강 상태가 아니라 **이 영상이 다른 날 영상과
 * 나란히 놓일 만한가**(관절이 보이게 찍혔나)를 말한다.
 */
data class GaitRecord(
    val id: String,
    /** 찍은 날. 카드와 목록의 제목이 된다. */
    val date: LocalDate,
    /**
     * 영상 길이(초). 10초 미만이면 카드가 짧다고 말해 준다.
     *
     * **서버 목록에는 없다.** 방금 분석한 것은 기기에서 읽어 알지만(`PreparedVideo`),
     * `GET /gait/records` 응답에 길이가 담기지 않아 지난 기록은 모른다. 모르는 것을
     * 0 으로 적으면 화면이 "0초" 라고 단언하므로 null 로 둔다.
     */
    val seconds: Int? = null,
    /**
     * 원본 영상. 표본 기록은 `null` 이다 — 아직 서버가 없어서 지난 기록은
     * 화면을 채우려고 만든 것이고, 재생할 파일이 실제로는 없다.
     */
    val video: Uri? = null,
    /** 목록·카드에 그릴 한 장. 없으면 화면이 발바닥 자리표시를 그린다. */
    val thumbnail: Bitmap? = null,
    /** 다른 기록과 나란히 볼 수 있나. 관절이 안 잡힌 영상은 false 다. */
    val comparable: Boolean = true,
) {
    /** `08.31`. 카드 제목과 비교 화면의 두 기둥에 같은 모양으로 쓴다. */
    val dateLabel: String get() = date.format(DAY)

    /** `12초`. 길이를 모르면 그렇게 말한다 — 0 초라고 하지 않는다. */
    val lengthLabel: String get() = seconds?.let { "${it}초" } ?: "길이 미상"

    /** `00:12`. 썸네일 위 오른쪽 아래에 얹는다. 모르면 null 이라 화면이 안 그린다. */
    val clockLabel: String? get() = seconds?.let { "%02d:%02d".format(it / 60, it % 60) }

    /**
     * 카드 오른쪽 위 배지.
     *
     * **상태를 평가하는 말이 아니다.** "비교 가능" 은 이 영상이 쓸 만하다는 뜻이지
     * 강아지가 괜찮다는 뜻이 아니다.
     */
    val badgeLabel: String get() = if (comparable) "비교 가능한 기록" else "비교 지표 부족"

    companion object {
        /** 권장 길이. 이보다 짧아도 막지 않는다 — 찍은 것을 버리게 하지 않는다. */
        const val RECOMMENDED_SECONDS = 10

        private val DAY = DateTimeFormatter.ofPattern("MM.dd")
    }
}

/**
 * 두 기록을 나란히 본 결과.
 *
 * 세 갈래뿐이고 **셋 다 중립이다.** 좋아졌다·나빠졌다가 없는 이유는
 * [GaitRecord] 의 주석과 같다. `문구` 는 서버가 생기면 그쪽 문장으로 갈아 끼우되,
 * 갈래 자체는 여기서 고정한다 — 화면이 네 번째 갈래를 지어내면 안 된다.
 */
enum class GaitVerdict(val sentence: String) {
    /** 지표가 다 비슷하게 나왔다. */
    NoClearDifference("뚜렷한 차이는 관찰되지 않았습니다"),

    /** 한 지표라도 다르게 나왔다. **나쁘다는 뜻이 아니다.** */
    SomeDifference("일부 관절 움직임에서 차이가 관찰됩니다"),

    /** 잴 수 있는 게 모자랐다. 영상 문제이지 강아지 문제가 아니다. */
    NotEnough("비교할 수 있는 관절 지표가 부족합니다"),
}

/** 지표 하나가 두 영상 사이에서 어떻게 나왔나. */
enum class GaitDelta(val label: String) {
    Similar("유사"),
    Slight("약간의 차이"),

    /** 이 지표는 못 쟀다. 두 영상 중 한쪽에 관절이 안 잡힌 경우다. */
    Unknown("측정 부족"),
}

/** 비교표의 한 줄. 이름은 서버 계약이 생기면 그쪽에서 온다. */
data class GaitMetric(val name: String, val delta: GaitDelta)

/**
 * 비교 결과 한 벌.
 *
 * [verdict] 를 밖에서 받지 않고 [of] 가 지표에서 **끌어낸다.** 문장과 표가 따로
 * 오면 "차이 없음" 이라고 써 놓고 표에는 차이가 있는 화면이 만들어진다.
 */
data class GaitComparison(
    val recent: GaitRecord,
    val past: GaitRecord,
    val metrics: List<GaitMetric>,
    /**
     * 서버가 준 한 줄(`message_for_ui`). **있으면 이것이 [verdict] 문장을 이긴다** —
     * 저쪽이 실제 계산에서 유도한 문장이고, API.md 가 화면에 쓸 값으로 지목했다.
     * 서버가 없을 때(표본끼리 비교)는 null 이고 그때만 앱 문장이 나온다.
     */
    val serverMessage: String? = null,
    /**
     * 두 기록의 필터 버전이 다를 때 저쪽이 붙이는 경고. **표시해야 한다** —
     * 같은 영상이라도 버전이 다르면 이동범위가 달라 보인다.
     */
    val versionWarning: String? = null,
) {
    /** 화면에 그릴 한 줄. 서버 문장이 있으면 그것, 없으면 [verdict] 의 것이다. */
    val sentence: String get() = serverMessage ?: verdict.sentence

    val verdict: GaitVerdict = when {
        metrics.isEmpty() || metrics.all { it.delta == GaitDelta.Unknown } -> GaitVerdict.NotEnough
        metrics.any { it.delta == GaitDelta.Slight } -> GaitVerdict.SomeDifference
        else -> GaitVerdict.NoClearDifference
    }

    companion object {
        /**
         * 비교 불가 기록이 섞이면 표를 통째로 [GaitDelta.Unknown] 으로 만든다.
         *
         * 한쪽이 못 쓰는 영상인데 나머지 지표만 "유사" 로 남기면, 실제로는 재지도
         * 못한 것을 재서 같다고 한 것처럼 읽힌다.
         */
        fun of(
            recent: GaitRecord,
            past: GaitRecord,
            metrics: List<GaitMetric>,
            serverMessage: String? = null,
            versionWarning: String? = null,
        ): GaitComparison =
            if (recent.comparable && past.comparable) {
                GaitComparison(recent, past, metrics, serverMessage, versionWarning)
            } else {
                GaitComparison(
                    recent,
                    past,
                    metrics.map { it.copy(delta = GaitDelta.Unknown) },
                    serverMessage,
                    versionWarning,
                )
            }
    }
}
