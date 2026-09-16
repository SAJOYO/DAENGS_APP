package com.daengs.app.miniroom

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import com.daengs.app.miniroom.art.DoorSpec

/**
 * 홈 첫 진입 연출의 **한 프레임 값.**
 *
 * 세 컷이 한 줄로 이어진다 — 문밖 풍경이 화면을 채운 채 시작해 카메라가 뒤로 빠지고(문으로
 * 들어오기), 방이 드러나면 불이 켜지고(불 켜기), 불이 켜지면 강아지들이 문 앞으로 달려온다
 * (현관 마중). 값은 전부 0..1 이고, 무엇을 얼마나 확대할지 같은 **화면 기하는 여기 없다** —
 * 그건 캔버스가 자기 크기로 푼다.
 *
 * @param pull 카메라가 문 안에 들어가 있는 정도. 1 = 문짝이 화면을 채움, 0 = 제자리
 * @param doorOpen 문 열림. 연출 중에는 [MiniRoomCanvas] 의 문 애니메이션 대신 이 값을 쓴다
 * @param dark 방 위에 덮는 어둠 막의 알파
 * @param veil 로딩 화면과 잇는 크림 막의 알파. 로딩 마지막 프레임과 홈 첫 프레임이 같아야 한다
 * @param greet 이 시각에는 마중 신호를 보냈어야 한다. 신호 자체는 [RoomIntro.takeGreet] 가 한 번만 꺼낸다
 * @param active 아직 연출 중인가. false 면 캔버스는 평소처럼 그린다
 */
data class IntroFrame(
    val pull: Float,
    val doorOpen: Float,
    val dark: Float,
    val veil: Float,
    val greet: Boolean,
    val active: Boolean = true,
) {
    companion object {
        /** 연출이 끝났거나 애초에 없을 때. 모든 값이 평소 그대로다. */
        val DONE = IntroFrame(pull = 0f, doorOpen = 0f, dark = 0f, veil = 0f, greet = true, active = false)
    }
}

/**
 * 연출의 **시간표.** 경과 ms 를 넣으면 그 순간의 [IntroFrame] 이 나온다. 순수 함수라
 * 테스트가 잠근다.
 *
 * 길이는 1초. 매일 여는 앱이라 이보다 길면 스킵 버튼을 찾게 된다 — 이 값을 늘리려면
 * 하루 첫 실행만 길게 하는 식으로 갈래를 나눠야 한다.
 */
object IntroTimeline {
    // 처음엔 전체 1초로 잡았는데 실기기에서 "전혀 티가 안 난다" 였다 (2026-09-14). 콜드
    // 스타트 5초 끝에 붙는 1초라 로딩의 일부로 묻힌다. 첫 컷을 잠깐 보여 주고 카메라를
    // 1초 동안 빼는 것으로 늘렸다. 그래도 2초 안쪽 — 매일 여는 앱이다.

    /** 크림 막이 걷히는 데 걸리는 시간. 로딩 화면(같은 그림)에서 넘어오는 이음새다. */
    const val VEIL_END_MS = 300L

    /** 카메라가 문 안에서 제자리로 빠지는 구간. 막이 걷힌 뒤 첫 컷을 잠깐 보여 주고 시작한다. */
    const val CAMERA_START_MS = 400L
    const val CAMERA_END_MS = 1400L

    /** 카메라가 자리를 잡은 뒤 문이 닫히는 구간. 들어와서 문을 닫는 순서다. */
    const val DOOR_CLOSE_START_MS = 1300L
    const val DOOR_CLOSE_END_MS = 1700L

    /** 스위치 딸깍. 이 순간 어둠이 **단번에** [NIGHT_AFTER_CLICK] 로 떨어지고 나머지는 서서히 걷힌다. */
    const val LIGHTS_CLICK_MS = 1400L
    const val LIGHTS_END_MS = 1800L

    /** 강아지들이 달려오기 시작하는 시각. 불이 켜지는 순간과 같다 — 불이 켜져서 돌아보는 그림. */
    const val GREET_AT_MS = 1400L

    /** 연출 전체 길이. 이 뒤로는 [IntroFrame.DONE] 이다. */
    const val TOTAL_MS = 1800L

    /** 밤의 어둠. 1 이면 방이 안 보여서 "켜지는" 게 아니라 "나타나는" 것이 된다. */
    const val NIGHT_DARK = 0.72f

    /** 딸깍 직후. 여기서 0 까지는 형광등이 달아오르듯 서서히다. */
    const val NIGHT_AFTER_CLICK = 0.30f

    /**
     * 낮에는 막을 안 씌운다. 옅은 회색을 올려 봤더니 크림색 화면 한가운데 방만 흐린
     * 네모가 되어 지저분했다 (Pixel 3 XL). 커튼 그림이 생기면 그때 "커튼 걷기" 로.
     */
    const val DAY_DARK = 0f

    /**
     * 한 프레임에 시계를 이만큼까지만 민다. 홈이 처음 그려질 때 프레임이 수백 ms 밀리는데,
     * 실제 시각을 그대로 쓰면 카메라가 첫 컷에서 멈춰 있다가 한 번에 제자리로 튄다 —
     * 실기기에서 그대로 보였다. [com.daengs.app.miniroom.DogHerd.update] 의 dt 상한과 같은 이유.
     */
    const val MAX_STEP_MS = 50L

    fun at(elapsedMs: Long, night: Boolean): IntroFrame {
        if (elapsedMs >= TOTAL_MS) return IntroFrame.DONE
        val e = elapsedMs.coerceAtLeast(0L)
        val veil = 1f - ramp(e, 0L, VEIL_END_MS)
        val pull = 1f - easeInOut(ramp(e, CAMERA_START_MS, CAMERA_END_MS))
        val doorOpen = 1f - easeOut(ramp(e, DOOR_CLOSE_START_MS, DOOR_CLOSE_END_MS))
        val dark = if (night) {
            if (e < LIGHTS_CLICK_MS) NIGHT_DARK
            else NIGHT_AFTER_CLICK * (1f - ramp(e, LIGHTS_CLICK_MS, LIGHTS_END_MS))
        } else {
            DAY_DARK
        }
        return IntroFrame(
            pull = pull,
            doorOpen = doorOpen,
            dark = dark,
            veil = veil,
            greet = e >= GREET_AT_MS,
        )
    }

    /** [from]..[to] 를 0..1 로. 밖은 잘린다. */
    private fun ramp(e: Long, from: Long, to: Long): Float =
        ((e - from).toFloat() / (to - from).toFloat()).coerceIn(0f, 1f)

    /** 빠르게 시작해 천천히 멎는다. 문이 닫힐 때 쓴다. */
    private fun easeOut(p: Float): Float {
        val q = 1f - p
        return 1f - q * q * q
    }

    /**
     * 천천히 시작해 천천히 멎는다. 카메라에 쓴다 — easeOut 은 처음 몇 프레임에 대부분을
     * 가 버려서 눈에 안 들어왔다. 뒤로 물러나는 것이 보이려면 중간이 길어야 한다.
     */
    private fun easeInOut(p: Float): Float = p * p * (3f - 2f * p)
}

/**
 * 연출의 **실행 상태.** 한 번 무장하면([arm]) 처음 그려지는 프레임부터 시간을 재고,
 * [IntroTimeline.TOTAL_MS] 가 지나거나 [skip] 되면 끝난다.
 *
 * Compose state 가 아니다 — 캔버스가 어차피 매 프레임 다시 그리므로 draw 람다 안에서
 * 읽기만 하면 된다. 값을 state 로 두면 60fps 재구성이 생긴다 (`DogActor` 와 같은 이유).
 *
 * **[MainActivity] 가 들고 있어야 한다.** [com.daengs.app.ui.home.HomeScreen] 안에서
 * `remember` 하면 도감·산책을 갔다 올 때마다 다시 튼다.
 *
 * @param previewElapsedMs null 이 아니면 시계를 무시하고 그 시각에 얼어붙는다 (@Preview 용)
 */
@Stable
class RoomIntro(private val previewElapsedMs: Long? = null) {
    private var armed = previewElapsedMs != null
    private var lastNow = -1L
    private var elapsed = 0L
    private var done = false
    private var greeted = false

    /** 아직 연출 중인가. */
    val active: Boolean get() = armed && !done

    /** 다음에 그려지는 프레임부터 튼다. */
    fun arm() {
        armed = true
        lastNow = -1L
        elapsed = 0L
        done = false
        greeted = false
    }

    /** 사용자가 화면을 만졌다. 남은 컷은 건너뛰고 평소 그림으로 간다. 마중 신호는 그대로 나간다. */
    fun skip() {
        if (armed) done = true
    }

    /**
     * [nowMs] 의 프레임. 처음 불린 시각이 곧 시작 시각이다 — 무장한 시점이 아니라
     * **실제로 화면에 나타난 시점**부터 재야 로딩이 길어도 첫 컷을 안 놓친다.
     *
     * 시계는 **프레임마다 [IntroTimeline.MAX_STEP_MS] 까지만** 간다. 한 프레임이 오래
     * 걸려도 연출은 그만큼만 진행되므로, 밀린 프레임 뒤에 카메라가 튀지 않는다.
     * 같은 프레임에서 두 번 불려도(캔버스와 크림 막) 같은 시각이라 두 번 가지 않는다.
     */
    fun frameAt(nowMs: Long, night: Boolean): IntroFrame {
        if (previewElapsedMs != null) return IntroTimeline.at(previewElapsedMs, night)
        if (!active) return IntroFrame.DONE
        // **0 은 시계가 아직 안 돈 것이다.** [com.daengs.app.miniroom.sprite.rememberFrameClock] 는
        // 첫 프레임 콜백이 오기 전까지 0 을 준다. 그걸 시작 시각으로 잡으면 다음 프레임의
        // 진짜 시각(수십억 ms)이 "경과" 가 되어 연출이 한 프레임 만에 끝난다 — Pixel 3 XL
        // 에서 그대로 겪었다. 시계가 돌기 전에는 첫 컷을 그리고 기다린다.
        if (nowMs <= 0L) return IntroTimeline.at(0L, night)
        if (lastNow > 0L) {
            elapsed += (nowMs - lastNow).coerceIn(0L, IntroTimeline.MAX_STEP_MS)
        }
        lastNow = nowMs
        val frame = IntroTimeline.at(elapsed, night)
        if (!frame.active) done = true
        return frame
    }

    /** 크림 막의 알파만. 방 밖(홈 전체를 덮는 막)에서 쓴다. */
    fun veilAt(nowMs: Long): Float = frameAt(nowMs, night = false).veil

    /**
     * 마중 신호를 **한 번만** 꺼낸다. [frame] 이 마중 시각을 지났고 아직 안 꺼냈으면 true.
     * 건너뛰어도 나간다 — 연출을 안 봤다고 강아지가 안 반기면 그게 더 이상하다.
     */
    fun takeGreet(frame: IntroFrame): Boolean {
        if (previewElapsedMs != null) return false
        if (!armed || greeted || !frame.greet) return false
        greeted = true
        return true
    }
}

/**
 * 문 바로 안쪽 바닥의 격자 좌표. 강아지들이 마중 나와 앉는 자리다.
 *
 * 문틀 밑변 한가운데를 격자로 풀면 바닥 왼쪽 모서리 언저리(대략 col 0, row 10)가 나온다.
 * 거기 그대로 세우면 벽에 코를 박으므로 **방 안쪽으로 한 걸음** 민다.
 */
fun RoomGeometry.doorstep(): Offset {
    val frame = DoorSpec.rectOf(this, DoorSpec.frame)
    val (c, r) = toGridF(Offset(frame.center.x, frame.bottom))
    val lo = 0.6f
    val hi = RoomSpec.GRID - 0.6f
    return Offset((c + 1.3f).coerceIn(lo, hi), (r - 0.9f).coerceIn(lo, hi))
}
