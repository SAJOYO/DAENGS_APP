package com.daengs.app.gait

/**
 * 비교 화면이 쓰는 관절 여섯 개와 그 상태.
 *
 * ### 왜 앱이 문장을 짓나
 *
 * D-058 은 "문장을 앱이 짓지 않는다" 였다 — 서버 `message_for_ui` 를 그대로 띄우는
 * 규칙이었고, 그때는 그게 맞았다. 저쪽이 실제 계산에서 유도한 한 줄이었기 때문이다.
 *
 * **그 규칙이 여기서 뒤집힌다.** 서버 문장은 관절을 통틀어 "일부 지표에서 차이가
 * 관찰됩니다" 한 줄뿐이라, 어느 다리에서 무엇이 달라졌는지를 말하지 못한다. 사용자가
 * 알고 싶은 것은 그 한 줄이 아니라 **어느 쪽 다리인가**다. 그래서 제목·보조문구를
 * 관절 결과에서 앱이 파생한다 ([verdictOf]).
 *
 * 뒤집은 것은 문장뿐이다. **계산은 그대로 저쪽 것을 쓴다** — `comparison_note` 의
 * x·y 판정을 읽기만 하고, 임계값도 관절별 비교도 앱은 건드리지 않는다.
 * `reliability_note` · `version_warning` 도 서버 문장을 그대로 띄운다.
 */
enum class GaitLeg(val label: String) {
    Left("왼쪽 다리"),
    Right("오른쪽 다리"),
}

/**
 * 화면에 그리는 관절 여섯 개.
 *
 * [key] 는 서버(AP-10K) 이름이고 **화면에 안 나간다** — `L_B_Paw` 를 그대로 띄우면
 * 사용자가 읽을 수 없다. 선언 순서가 곧 화면 순서다 (왼쪽 위에서 아래, 그다음 오른쪽).
 * 서버 `gait_v4/config.py` 의 `priority` 와 같은 순서다.
 */
enum class GaitJoint(val key: String, val leg: GaitLeg, val label: String) {
    LeftHip("L_Hip", GaitLeg.Left, "고관절"),
    LeftKnee("L_Knee", GaitLeg.Left, "무릎"),
    LeftBackPaw("L_B_Paw", GaitLeg.Left, "뒷발"),
    RightHip("R_Hip", GaitLeg.Right, "고관절"),
    RightKnee("R_Knee", GaitLeg.Right, "무릎"),
    RightBackPaw("R_B_Paw", GaitLeg.Right, "뒷발"),
}

/**
 * 한 축(좌우 또는 위아래)의 판정. 서버 `comparison_note.x` · `.y` 가 이것이다.
 *
 * **모르는 문자열을 [Similar] 로 떨어뜨리지 않는다.** "차이 관찰됨" 을 놓쳐 "비슷함"
 * 이 되면 없는 안심을 주게 된다.
 */
enum class GaitAxis {
    Similar,
    Changed,
    Unknown,
    ;

    companion object {
        fun of(note: String?): GaitAxis = when (note) {
            "비슷함" -> Similar
            "차이 관찰됨" -> Changed
            else -> Unknown
        }
    }
}

/**
 * 관절 하나를 한 줄로 말한 것. **x·y 두 줄을 여기서 합친다.**
 *
 * 예전에는 관절 하나가 `L_Hip (좌우)` · `L_Hip (상하)` 두 줄이어서 표가 열두 줄이었다.
 * 축을 나눠 두면 정보는 안 잃지만, 열두 줄을 읽고 나서 사용자가 스스로 관절별로
 * 다시 묶어야 했다. 합치되 **어느 축이 움직였는지는 문구에 남긴다** — 그게 축을
 * 나눠 뒀던 이유였다.
 */
enum class GaitJointChange(val label: String) {
    /** 두 축 다 비슷하다. */
    None("비슷함"),

    /** 좌우(x)만 달라졌다. 앞뒤로 옮겨 딛는 폭이 달라진 쪽이다. */
    Horizontal("좌우 움직임 변화 감지"),

    /** 위아래(y)만 달라졌다. 흔들림의 높낮이가 달라진 쪽이다. */
    Vertical("위아래 움직임 변화 감지"),

    /** 두 축 다 달라졌다. **나쁘다는 뜻이 아니라** 두 방향 모두 달라졌다는 표시다. */
    Both("좌우·위아래 움직임 변화 감지"),

    /** 이 관절은 못 쟀다. 두 영상 중 한쪽에 관절이 안 잡힌 경우다. */
    Unknown("측정 부족"),
    ;

    /** 변화가 잡힌 줄인가. 다리별 판정([verdictOf])이 이걸 센다. */
    val changed: Boolean get() = this == Horizontal || this == Vertical || this == Both

    /** 재기는 했나. **[Unknown] 을 "변화 없음" 으로 세지 않으려고 따로 둔다.** */
    val measured: Boolean get() = this != Unknown

    companion object {
        /**
         * 두 축의 판정을 한 상태로 합친다.
         *
         * **한쪽 축을 못 쟀을 때가 규칙에 없어서 여기서 정한다.**
         *  - 둘 다 모름 → [Unknown]
         *  - 한쪽만 모르고 다른 쪽이 "비슷함" → [Unknown]. 못 잰 것을 비슷하다고 하면
         *    없는 안심을 준다
         *  - 한쪽만 모르고 다른 쪽이 "차이 관찰됨" → 그 축의 변화. 잡힌 변화를
         *    모른다는 이유로 감추지 않는다
         */
        fun of(x: GaitAxis, y: GaitAxis): GaitJointChange = when {
            x == GaitAxis.Changed && y == GaitAxis.Changed -> Both
            x == GaitAxis.Changed -> Horizontal
            y == GaitAxis.Changed -> Vertical
            x == GaitAxis.Similar && y == GaitAxis.Similar -> None
            else -> Unknown
        }
    }
}

/** 표의 한 줄. */
data class GaitJointState(val joint: GaitJoint, val change: GaitJointChange)

/**
 * 비교 결과 한 줄 요약.
 *
 * 갈래는 넷이고 **넷 다 중립이다.** 좋아졌다·나빠졌다가 없다 (`CONTEXT.md` 8절).
 * "변화가 있어요" 는 나빠졌다는 뜻이 아니라 **이전 기록과 달라졌다**는 뜻이고,
 * 그 말을 지키는 자리가 [detail] 과 화면 아래 진단아님 문구다.
 */
enum class GaitVerdict(val title: String, val detail: String) {
    NoClearDifference(
        "뚜렷한 차이는 관찰되지 않았어요",
        "이전 기록과 비교했을 때 주요 관절 움직임이 전반적으로 비슷해요.",
    ),

    OneSide(
        "보행 움직임에 변화가 있어요",
        "이전 기록과 비교해 한쪽 다리의 여러 관절에서 움직임 변화가 함께 관찰됐어요.",
    ),

    BothSides(
        "보행 움직임에 변화가 있어요",
        "이전 기록과 비교해 양쪽 다리의 여러 관절에서 움직임 변화가 함께 관찰됐어요.",
    ),

    /**
     * 잰 것이 모자라 말할 수 없다. **영상 문제이지 강아지 문제가 아니다.**
     *
     * 이 갈래가 없으면 측정 부족이 "뚜렷한 차이 없음" 으로 흘러든다 — 못 잰 것을
     * 변화가 없다고 말하는 것이라 제일 나쁜 오답이다.
     */
    NotEnough(
        "비교할 수 있는 관절 지표가 부족해요",
        "두 영상에서 같은 관절을 충분히 재지 못해 변화가 있었는지 말하기 어려워요.",
    ),
    ;

    /** 안내 영역([GaitVerdict.OneSide] · [GaitVerdict.BothSides])을 띄울 갈래인가. */
    val changed: Boolean get() = this == OneSide || this == BothSides
}

/**
 * 다리 하나가 "여러 관절이 함께 달라졌다" 로 보이는 기준.
 *
 * **비율이 아니라 개수다.** 예전 구현은 60% 였는데, 관절이 셋이라 60% 는 결국
 * "2개 이상" 이면서 읽는 사람에게는 그게 안 보였다. 셋 중 둘이라고 적으면 규칙이
 * 코드에서 바로 읽힌다.
 */
const val CHANGED_JOINTS_FOR_LEG = 2

/**
 * 관절 상태에서 판정을 끌어낸다.
 *
 * **[GaitJointChange.Unknown] 은 "변화 없음" 쪽으로 세지 않는다.** 못 잰 관절이 많아
 * 한쪽 다리에서 [CHANGED_JOINTS_FOR_LEG] 개를 재지도 못했다면, 변화가 없다고 말할
 * 근거가 없는 것이라 [GaitVerdict.NotEnough] 로 간다.
 *
 * 반대로 변화가 이미 기준을 채웠으면 못 잰 관절이 있어도 그대로 말한다 — 잡힌 변화는
 * 빠진 데이터로 흐려지지 않는다.
 */
fun verdictOf(states: List<GaitJointState>): GaitVerdict {
    if (states.isEmpty()) return GaitVerdict.NotEnough

    fun countIn(leg: GaitLeg, predicate: (GaitJointChange) -> Boolean) =
        states.count { it.joint.leg == leg && predicate(it.change) }

    val legsWithChange = GaitLeg.entries.count {
        countIn(it) { change -> change.changed } >= CHANGED_JOINTS_FOR_LEG
    }
    if (legsWithChange >= GaitLeg.entries.size) return GaitVerdict.BothSides
    if (legsWithChange > 0) return GaitVerdict.OneSide

    // 변화가 기준에 못 미쳤다. "비슷해요" 라고 하려면 **실제로 잰 것이 있어야 한다.**
    val enoughMeasured = GaitLeg.entries.all {
        countIn(it) { change -> change.measured } >= CHANGED_JOINTS_FOR_LEG
    }
    return if (enoughMeasured) GaitVerdict.NoClearDifference else GaitVerdict.NotEnough
}
