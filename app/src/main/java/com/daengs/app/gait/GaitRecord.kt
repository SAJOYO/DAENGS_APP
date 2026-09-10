package com.daengs.app.gait

import android.graphics.Bitmap
import android.net.Uri
import com.daengs.app.member.MemberIdentity
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
     * 영상 길이(초).
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
    /**
     * 분석 결과(스켈레톤) 영상 주소. 서버가 만든 것으로, 원본 위에 관절 점·선이 그려져 있다.
     * 있으면 재생 화면이 [video](기기 원본) 대신 이걸 튼다 — 사용자가 보려는 것이 그 점·선이다.
     * 없으면(저장소 미설정·구 기록) null 이고 원본으로 물러난다. 서버 `overlay_url` 이 원본이다.
     */
    val overlay: Uri? = null,
    /** 목록·카드에 그릴 한 장. 없으면 화면이 발바닥 자리표시를 그린다. */
    val thumbnail: Bitmap? = null,
    /** 다른 기록과 나란히 볼 수 있나. 관절이 안 잡힌 영상은 false 다. */
    val comparable: Boolean = true,
    /**
     * [comparable] 이 false 인 **이유**. 저쪽 `quality.reason` 이 그대로 온다.
     *
     * **앱이 이유를 넘겨짚지 않는다.** 예전에는 이 자리가 없어서 상세 화면이
     * "10초보다 짧게 찍혀서" 라고 단정했는데, `comparable` 의 뜻이 길이에서
     * 서버의 `quality.status` 로 바뀐 뒤에도 문장만 옛 뜻에 남아 있었다 —
     * 1분짜리 영상에도 "짧게 찍혀서" 가 떴다. 이유를 아는 쪽은 서버뿐이다.
     */
    val qualityReason: String? = null,
    /** 그래서 뭘 하면 되나. 저쪽 `quality.recommendation`. */
    val qualityAdvice: String? = null,
    /**
     * 영상의 가로/세로 비. 회전을 반영한 값이다 ([PreparedVideo.aspect]).
     *
     * **모르면 세로로 친다.** 촬영 가이드가 세로 프레임이라 이 기능으로 찍힌
     * 영상은 세로다. 서버 목록 응답에는 크기가 안 담겨서 지난 기록은 모르는데,
     * 그때 가로로 가정하면 세로 영상이 좌우로 텅 빈 채 눕는다 — 실제로 그랬다.
     */
    val aspect: Float? = null,
    /**
     * 저쪽 `quality_tier` (good / ok / low). **`null` 이면 분석 불가(unavailable)이거나
     * 서버가 없는 표본**이다 — 둘은 [comparable] 로 가른다. 목록·상세 둘 다에 온다.
     */
    val qualityTier: GaitQualityTier? = null,
    /**
     * 저쪽에 스켈레톤 영상이 **있나** (`has_overlay`). 받을 주소([overlay])는 상세에만 오므로,
     * 목록에서 온 기록은 "있는데 아직 주소를 못 받은" 동안이 있다 — 그때 요약이
     * "재생 안 됨" 이라고 잘못 말하지 않게 이 값을 본다.
     */
    val hasOverlay: Boolean = false,
    /**
     * 사용자가 정한 제목. `null` 이면 기본값([displayTitle]).
     *
     * 출처가 둘이다 — 처음 정한 것은 서버 `note`(목록 응답)로 오고, 나중에 고친 것은
     * [GaitTitleStore](로컬)가 덮어쓴다. **날짜와는 독립**이다: 제목을 고쳐도 [date] 는
     * 그대로다. 날짜를 제목 문자열에 섞어 저장하지 않는다.
     */
    val title: String? = null,
    /** 서버가 판정한 현재 사용자의 권한. null은 이 필드가 없던 서버 응답이다. */
    val createdBy: MemberIdentity? = null,
    val canConfirm: Boolean? = null,
    val canDelete: Boolean? = null,
) {
    /** 화면에 그릴 제목. 제목이 없으면 [DEFAULT_TITLE]. 날짜는 여기 안 섞는다. */
    val displayTitle: String get() = title?.ifBlank { null } ?: DEFAULT_TITLE

    /** `09.07 · 24초`. 길이를 모르면 날짜만 — "· 길이 미상" 을 굳이 붙이지 않는다. */
    val dateAndLength: String get() = seconds?.let { "$dateLabel · ${it}초" } ?: dateLabel

    /**
     * 틀 영상이 하나라도 있나. 서버 오버레이(지난 기록의 유일한 재생본)든 기기 원본이든.
     * 재생기·비교 화면이 "재생할 게 있나" 를 이 하나로 판단한다 — [overlay] 우선은
     * [com.daengs.app.ui.gait.GaitVideoPlayer] 안에 있고, 여기서는 존재 여부만 본다.
     */
    val playable: Boolean get() = overlay != null || video != null

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

    /**
     * 영상 자리를 잡을 때 쓸 비율.
     *
     * 실제 비를 그대로 쓰되 **너무 길쭉한 것은 자른다.** 9:16 을 대화 카드에
     * 폭 맞춰 넣으면 카드 하나가 화면을 다 먹어서 위의 말풍선이 밀려난다.
     */
    val displayAspect: Float
        get() = (aspect ?: PORTRAIT_ASPECT).coerceIn(MIN_ASPECT, MAX_ASPECT)

    companion object {
        /**
         * 저쪽이 요구하는 최소 **유효 프레임** (API.md §21).
         *
         * 영상 길이가 아니다. 강아지가 검출되고 "걷는 중" 으로도 판정된 프레임만
         * 센다 — 59초(298프레임) 영상에서 검출 95, 유효 5 가 나온 적이 있다.
         */
        const val MIN_VALID_FRAMES = 80

        /** 분석 fps. 저쪽이 영상을 이 간격으로 훑는다(overlay 도 같은 5fps). */
        const val ANALYSIS_FPS = 5

        /** 유효 프레임 80개는 **걷는 모습 16초치**다. 영상 길이와 다르다. */
        const val MIN_WALKING_SECONDS = MIN_VALID_FRAMES / ANALYSIS_FPS

        /**
         * 사용자에게 권하는 촬영 길이.
         *
         * [MIN_WALKING_SECONDS] 보다 넉넉히 잡는다 — 걷다 서고 냄새 맡는 구간이
         * 늘 섞여서, 찍은 길이가 그대로 유효 프레임이 되지 않는다. 얼마나 새는지는
         * 그날 강아지에 달렸으므로 앱이 계산할 수 없고, 여유를 두는 수밖에 없다.
         *
         * **예전에는 10이었다.** 근거 없이 정한 숫자였고, 저쪽 기준의 16초치보다도
         * 짧아서 안내를 지켜도 떨어지는 영상이 나왔다.
         */
        const val RECOMMENDED_SECONDS = 20

        /** 모를 때의 기본. 촬영 가이드가 세로라 세로로 친다 (3:4). */
        const val PORTRAIT_ASPECT = 3f / 4f

        /** 제일 길쭉하게 허용하는 비 (9:16). 이보다 길면 잘라 담는다. */
        const val MIN_ASPECT = 9f / 16f

        /** 제일 납작하게 허용하는 비 (16:9). */
        const val MAX_ASPECT = 16f / 9f

        /** 제목을 안 정했을 때. 저장하지 않고 **그릴 때만** 쓴다 — 기본값을 저장해 두면 나중에 문구를 바꿀 수 없다. */
        const val DEFAULT_TITLE = "보행 기록"

        private val DAY = DateTimeFormatter.ofPattern("MM.dd")
    }
}

/**
 * 저쪽 `quality_tier`. 서버 `quality.py` 가 유효 프레임 구간으로 매긴다(§21) —
 * good / ok / low 셋이고, `status: unavailable` 이면 tier 자체가 없다.
 *
 * **숫자는 안 가져온다.** 사용자에게 "유효 프레임 105" 를 보여 줄 일이 없다 — 등급이
 * 사용자 문장([GaitRecord.summaryLines])으로 바뀌는 자리가 여기 하나면 된다.
 */
enum class GaitQualityTier(val server: String) {
    Good("good"),
    Ok("ok"),
    Low("low"),
    ;

    companion object {
        /**
         * 서버 값에서. `status` 가 ok 가 아니면 tier 가 와도 무시한다 — unavailable 인
         * 기록에 "충분히 확인된" 이 붙으면 안 된다. 모르는 문자열은 null.
         */
        fun of(status: String?, tier: String?): GaitQualityTier? =
            if (status != "ok") null else entries.firstOrNull { it.server == tier }
    }
}

/**
 * 비교 결과 한 벌.
 *
 * [verdict] 를 밖에서 받지 않고 관절 상태에서 **끌어낸다** ([verdictOf]). 문장과 표가
 * 따로 오면 "차이 없음" 이라고 써 놓고 표에는 차이가 있는 화면이 만들어진다.
 *
 * 갈래와 문장은 [GaitJoints.kt] 에 있다 — 왜 서버 문장 대신 앱이 짓는지도 거기 적었다.
 */
data class GaitComparison(
    val recent: GaitRecord,
    val past: GaitRecord,
    /** 관절 여섯 줄. 서버가 빠뜨린 관절은 [GaitJointChange.Unknown] 으로 채워져 온다. */
    val joints: List<GaitJointState>,
    /**
     * 두 기록의 필터 버전이 다를 때 저쪽이 붙이는 경고. **표시해야 한다** —
     * 같은 영상이라도 버전이 다르면 이동범위가 달라 보인다.
     */
    val versionWarning: String? = null,
    /**
     * 유효 프레임이 적어 참고용이라는 저쪽 문장(`reliability_note`).
     *
     * ⚠️ **화면에 그대로 띄우지 않는다.** 저쪽 문장에는 `기록 55d18f59-bc8a-…은(는) 유효
     * 프레임 수가 적어(§21 기준 80프레임 미만)` 처럼 record UUID 와 내부 규격 번호가 들어
     * 있어서 사용자에게 보여 줄 말이 아니다. 화면은 대신 [reliabilitySentence] 로 어느 쪽
     * 기록이 모자랐는지를 말한다.
     *
     * 그래도 모델에는 남긴다 — 저쪽 계약이 무엇을 주는지가 코드에서 사라지면, 나중에
     * 그 문장이 사용자용으로 다듬어져도 아무도 다시 찾아 쓰지 않는다.
     */
    val reliabilityNote: String? = null,
) {
    val verdict: GaitVerdict = verdictOf(joints)

    companion object {
        /**
         * 비교 불가 기록이 섞이면 표를 통째로 [GaitJointChange.Unknown] 으로 만든다.
         *
         * 한쪽이 못 쓰는 영상인데 나머지 관절만 "비슷함" 으로 남기면, 실제로는 재지도
         * 못한 것을 재서 같다고 한 것처럼 읽힌다.
         */
        fun of(
            recent: GaitRecord,
            past: GaitRecord,
            joints: List<GaitJointState>,
            versionWarning: String? = null,
            reliabilityNote: String? = null,
        ): GaitComparison =
            if (recent.comparable && past.comparable) {
                GaitComparison(recent, past, joints, versionWarning, reliabilityNote)
            } else {
                GaitComparison(
                    recent,
                    past,
                    joints.map { it.copy(change = GaitJointChange.Unknown) },
                    versionWarning,
                    reliabilityNote,
                )
            }
    }
}

/**
 * 비교가 얼마나 믿을 만한지 한 줄 — **어느 쪽 기록이 모자랐는지까지** 말한다.
 *
 * 저쪽 `reliability_note` 를 그대로 띄우던 자리다. 그 문장에는 record UUID 와 `§21 기준
 * 80프레임 미만` 같은 내부 표기가 들어 있어 사용자가 읽을 수 없었다
 * ([GaitComparison.reliabilityNote]).
 *
 * **"모자람" 의 기준은 저쪽과 같게 `good` 이 아닌 것**으로 둔다. 저쪽이 주의 문장을 붙이던
 * 경우와 정확히 같은 집합이라, 문구만 바뀌고 언제 주의가 뜨는지는 안 바뀐다 — 여기서
 * 기준을 느슨하게 잡으면 저쪽이 참고용이라고 본 비교에 앱이 "충분" 이라고 도장을 찍게 된다.
 *
 * 등급을 아예 모르는 기록(표본·옛 기록)은 [GaitRecord.effectiveTier] 가 `Ok` 로 보정하므로
 * 여기서도 모자란 쪽으로 샌다 — 모르는 것을 "충분" 으로 올려 말하지 않는다.
 */
val GaitComparison.reliabilitySentence: String
    get() {
        val recentShort = recent.effectiveTier != GaitQualityTier.Good
        val pastShort = past.effectiveTier != GaitQualityTier.Good
        return when {
            !recentShort && !pastShort -> "두 기록 모두 비교하기에 충분한 보행 장면이 확인됐어요."
            recentShort && pastShort -> "두 기록 모두 보행 장면이 적어 결과는 참고용으로 봐주세요."
            recentShort -> "최근 기록의 보행 장면이 적어 결과는 참고용으로 봐주세요."
            else -> "비교 기록의 보행 장면이 적어 결과는 참고용으로 봐주세요."
        }
    }

/**
 * 상세 화면 요약에 올릴 줄들 — **최대 셋**. 화면이 아니라 여기서 만든다.
 *
 * Composable 안에 두면 테스트가 못 들어와서, 문구 규칙을 사람이 눈으로만 지켜야 한다.
 * 실제로 그렇게 뚫렸다 — `comparable` 의 뜻이 길이에서 서버 판정으로 바뀌었는데 문장은
 * 옛 뜻에 남아 있었고, 빌드도 테스트도 통과했다. 순수 함수로 내려두면 빨간 줄로 잡힌다.
 *
 * ### 무엇을 말하고 무엇을 말하지 않나
 *
 * 이 화면은 **영상 한 편을 보는 자리**다. 요약이 할 일은 "이 영상이 분석에 어느 정도
 * 쓸 만했나" 를 사용자 말로 짧게 하는 것뿐이다. 그래서
 *
 *  1. `09.07 · 24초` — 날짜와 길이. 길이를 모르면 날짜만
 *  2. 등급 한 줄 — [GaitQualityTier] 를 문장으로. 숫자(유효 프레임·검출률·conf)는 안 나간다
 *  3. 다음 행동 또는 재생 안내 — 분석 불가면 저쪽 `recommendation`(다시 찍는 요령)이
 *     우선이고, 그게 없거나 분석이 됐으면 오버레이를 볼 수 있는지
 *
 * 예전에 있던 "지난 기록과 나란히 볼 수 있어요" · "표본이라 파일이 없어요" · 권장 길이는
 * 뺐다. 기능 설명이지 이 영상에 대한 말이 아니었다. 모델명·플래그·feature·진단 표현은
 * 애초에 이 모델에 자리가 없다.
 */
fun GaitRecord.summaryLines(): List<String> =
    listOfNotNull(dateAndLength, qualitySentence, summaryNote)

/**
 * 화면이 실제로 쓰는 등급. 서버가 없는 표본·옛 기록은 [qualityTier] 가 없으므로
 * [comparable] 로 보정한다 — 잴 수 있었으면 "확인할 수 있는"(Ok), 아니면 분석 불가(null).
 *
 * **모르는 것을 "충분히" 로 올려 말하지 않는다.**
 */
val GaitRecord.effectiveTier: GaitQualityTier?
    get() = qualityTier ?: if (comparable) GaitQualityTier.Ok else null

/**
 * 등급 한 줄.
 *
 * 화면은 이 문장 옆에 점을 찍고 색을 [effectiveTier] 로 정한다 — **문장과 색이 같은 값에서
 * 나와야** 초록 점 옆에 "충분하지 않았어요" 가 붙는 일이 없다.
 */
val GaitRecord.qualitySentence: String
    get() = when (effectiveTier) {
        GaitQualityTier.Good -> "관절 움직임이 충분히 확인된 영상이에요."
        GaitQualityTier.Ok -> "관절 움직임을 확인할 수 있는 영상이에요."
        GaitQualityTier.Low -> "확인된 보행 장면이 적어 결과는 참고용으로 봐주세요."
        null -> "분석 가능한 보행 장면이 충분하지 않았어요."
    }

/**
 * 마지막 한 줄 — 다음에 뭘 하면 되나, 또는 분석 영상을 볼 수 있나. 없으면 `null` 이라
 * 화면이 그 줄을 안 그린다.
 *
 * 분석 불가면 저쪽 권고가 유일한 행동 지침이라 재생 안내보다 앞선다.
 */
val GaitRecord.summaryNote: String?
    get() {
        val advice = if (effectiveTier == null) qualityAdvice?.ifBlank { null } else null
        return when {
            advice != null -> advice
            overlay != null -> "분석 영상에서 관절 위치를 직접 확인할 수 있어요."
            // 저쪽에 있다는데 주소를 아직 못 받았다 — 상세를 열면 곧 채워진다. 그 사이에
            // "지원되지 않아요" 라고 했다가 바뀌면 화면이 말을 바꾼 것으로 읽힌다.
            hasOverlay -> null
            else -> "이 기록은 분석 영상 재생이 지원되지 않아요."
        }
    }
