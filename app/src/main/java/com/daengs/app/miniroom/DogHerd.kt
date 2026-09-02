package com.daengs.app.miniroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import com.daengs.app.miniroom.art.DogBreed
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * 방을 돌아다니는 강아지 한 마리.
 *
 * **격자를 쓰지 않는다.** 위치가 실수 좌표(예: 2.7, 3.4)라 칸 개념이 없고,
 * 그래서 러그 위에 걸치거나 여러 마리가 서로 안 부딪힌다.
 * 격자는 **막힌 칸을 물어볼 때만** 쓴다 ([DogHerd.update] 의 `blocked`).
 *
 * 필드가 Compose state 가 아닌 이유: 캔버스는 강아지 애니메이션 때문에
 * 어차피 매 프레임 다시 그린다. 위치까지 state 로 두면 10마리 x 60fps 만큼
 * 쓸데없는 스냅샷 쓰기가 생긴다. 그리는 순간에 최신 값만 읽으면 된다.
 */
class DogActor(
    val id: Int,
    /** 형태. 카탈로그 키(`breed.id`)이기도 하다. */
    var breed: DogBreed,
    /**
     * 덩치. **전부 1f — 마리 구분은 덩치가 아니라 견종으로 한다.**
     * 덩치를 흔들었더니 같은 견종인데 원근이 깨진 것처럼 보였다.
     * 필드를 남겨두는 건 히트 판정·그리기가 이미 이걸 타고 있어서,
     * 나중에 새끼 강아지 같은 걸 넣을 때 여기만 건드리면 되기 때문이다.
     */
    val sizeScale: Float,
    /**
     * 대기 동작 시계를 마리마다 밀어주는 값(ms).
     *
     * 없으면 전 마리가 같은 `timeMs` 를 써서 **숨쉬기·꼬리 흔들기가 완벽히 동기화**된다.
     * 세 마리가 한 몸처럼 까딱거려서 기계처럼 보인다 — 실기기에서 바로 티가 났다.
     */
    val animOffsetMs: Long,
) {
    /**
     * 걷는 속도(격자 단위/초)와 몸 반경. **[breed] 를 따라간다.**
     *
     * 생성자 인자로 받지 않는 게 중요하다. 개발자 도구에서 견종을 바꾸면 [breed] 만
     * 갈리는데, 값을 복사해 뒀으면 허스키로 바꿔도 치와와 속도로 걷는다.
     */
    val speed: Float get() = breed.speed
    val bodyRadius: Float get() = breed.bodyRadius

    var pos: Offset = Offset.Zero
    var target: Offset = Offset.Zero
    var restUntil: Long = 0L
    var moving: Boolean = false

    /** 진행 방향이 화면 왼쪽이면 좌우 반전. 3/4 앞모습 스프라이트를 대비한 것. */
    var mirrored: Boolean = false

    /** 걸음 위상. 이동 거리에 비례해 늘어나서 속도와 자동으로 맞는다. */
    var phase: Float = 0f

    /**
     * 0 = 앉음, 1 = 일어섬. [moving] 을 따라가되 **천천히** 따라간다.
     *
     * 그림이 앉은 자세라 걸으려면 몸통을 들어올려 다리를 드러내야 하는데
     * ([com.daengs.app.miniroom.art.DogPose]), 그걸 즉시 하면 몸이 순간이동한다.
     */
    var stand: Float = 0f

    /**
     * 배웅한 아이인가.
     *
     * **자기 자리에 앉는다.** 목록에서는 무지개가 붙는데 방에서는 다른 아이들과 똑같이
     * 돌아다니면, 화면 두 곳이 다른 말을 한다. 방은 매일 보는 자리라 더 그렇다.
     *
     * 방에서 빼지 않는다 — 배웅은 지우는 것이 아니다.
     */
    var departed: Boolean = false
}

/**
 * 강아지 무리. 알아서 돌아다니고, 손으로 잡아 옮길 수 있다.
 *
 * 위치는 저장하지 않는다 — 어차피 계속 돌아다녀서 저장해도 의미가 없다.
 */
@Stable
class DogHerd(initialRoster: List<DogBreed>, seed: Int = 7) {

    /** 명부 없이 세울 때. **데모다** — 사용자 강아지를 아직 못 받아 온 자리에서 쓴다. */
    constructor(count: Int, seed: Int = 7) : this(DogBreed.demoRoster(count), seed)

    private val rnd = Random(seed)
    private var lastMs = 0L

    /**
     * 방에 서 있어야 할 견종 — **사용자가 등록한 강아지 목록**이다.
     *
     * [dogs] 의 breed 와 따로 두는 이유는 개발자 도구다. 거기서 견종을 덮어써도
     * 명부는 그대로여야 **되돌렸을 때 내 강아지로 돌아온다.**
     */
    var roster: List<DogBreed> = initialRoster
        private set

    /** 개발자 도구가 덮어쓴 견종. null 이면 명부대로 선다. */
    private var breedOverride: DogBreed? = null

    /**
     * 배웅한 아이의 명부 자리. 견종과 따로 두는 이유는 [setRoster] 가 자리를 유지하기
     * 때문이다 — 명부가 갱신될 때마다 이 값도 [applyRoster] 에서 다시 얹어야 한다.
     */
    private var departedAt: Set<Int> = emptySet()

    var dogs: List<DogActor> = emptyList()
        private set

    /** 손으로 잡고 있는 강아지. 잡힌 동안은 스스로 안 움직인다. */
    var draggingId: Int? = null

    init {
        dogs = List(initialRoster.size) { newDog(it, initialRoster[it]) }
        spread()
    }

    /**
     * 처음 자리를 서로 벌린다.
     *
     * [newDog] 안에서는 못 한다 — 리스트를 만드는 중이라 [dogs] 가 아직 비어 있어서
     * 간격을 잴 상대가 없다. 리스트가 완성된 뒤 한 번 훑어야 앞의 마리들이 보인다.
     */
    private fun spread() {
        for (d in dogs) {
            d.pos = freeSpot(emptySet(), d)
            d.target = d.pos
        }
    }

    /**
     * 견종은 **뽑지 않고 차례대로 돌린다.** 무작위로 뽑으면 여러 마리가 다 같은 견종으로
     * 나오는 판이 생겨서, 여러 마리라는 게 눈에 안 들어온다.
     */
    private fun newDog(i: Int, breed: DogBreed): DogActor {
        val d = DogActor(
            id = i,
            breed = breed,
            sizeScale = 1f,
            // 무작위가 아니라 대기 주기(8프레임 / 6fps ≈ 1333ms)를 마리 수로 나눠 흩는다.
            // 무작위면 둘이 우연히 겹쳐서 여전히 같이 움직이는 판이 나온다.
            animOffsetMs = i * 430L + rnd.nextLong(140),
        )
        d.pos = randomSpot()
        d.target = randomSpot()
        d.restUntil = rnd.nextLong(0, 1500)
        return d
    }

    /** 벽에 딱 붙지 않게 가장자리를 조금 남긴다. */
    private fun randomSpot(): Offset {
        val lo = 0.4f
        val hi = RoomSpec.GRID - 0.4f
        return Offset(lo + rnd.nextFloat() * (hi - lo), lo + rnd.nextFloat() * (hi - lo))
    }

    /**
     * 방에 세울 견종을 통째로 갈아끼운다.
     *
     * **자리를 유지한다.** 강아지를 등록하거나 견종을 고칠 때마다 목록을 다시 받아
     * 오는데(`PetHolder`), 그때 방 전체를 새로 세우면 **멀쩡히 걷던 아이들이
     * 순간이동한다.** 남는 마리는 그대로 두고 견종만 갈아끼우고, 는 마리만 새로 놓는다.
     *
     * 개발자 도구가 견종을 덮어쓰고 있으면 그게 이긴다 — 사람이 방금 누른 것이라
     * 화면의 진짜다. 되돌리면 새 명부로 돌아온다.
     */
    fun setRoster(breeds: List<DogBreed>) {
        if (breeds == roster && breeds.size == dogs.size) return
        roster = breeds
        val kept = dogs.take(breeds.size)
        val added = List(breeds.size - kept.size) { newDog(kept.size + it, breeds[kept.size + it]) }
        dogs = kept + added
        applyRoster()
        // 새로 온 아이만 자리를 잡는다. 이미 있던 아이는 걷던 자리에 그대로 둔다.
        for (d in added) {
            d.pos = freeSpot(emptySet(), d)
            d.target = d.pos
        }
        // 잡고 있던 아이가 명부에서 빠졌을 수 있다.
        if (dogs.none { it.id == draggingId }) draggingId = null
    }

    private fun applyRoster() {
        dogs.forEachIndexed { i, d ->
            d.breed = breedOverride ?: roster[i]
            d.departed = i in departedAt
        }
    }

    /**
     * 배웅한 아이가 명부의 몇 번째인가.
     *
     * 명부와 따로 받는다. 견종은 안 바뀌었는데 배웅만 한 경우가 있고([setRoster] 는
     * 그때 일찍 빠져나간다), 반대로 강아지를 새로 등록해 명부만 늘어난 경우도 있다.
     */
    fun setDeparted(indices: Set<Int>) {
        if (indices == departedAt) return
        departedAt = indices
        dogs.forEachIndexed { i, d -> d.departed = i in indices }
    }

    fun byId(id: Int): DogActor? = dogs.firstOrNull { it.id == id }

    /**
     * 견종을 통째로 바꾼다. 개발자 도구에서 16종을 훑어보려고 있는 것이다.
     *
     * @param breed null 이면 원래대로 [DogBreed.ALL] 을 차례로 돌린다
     */
    fun setBreedOverride(breed: DogBreed?) {
        breedOverride = breed
        dogs.forEachIndexed { i, d ->
            // **기본 구성과 다르게 [DogBreed.ALL] 을 다 받는다.**
            //
            // 방에 저절로 도는 견종은 [DogBreed.ROOM_BREEDS] 세 종이다(위 [newDog]).
            // 그런데 이 함수는 **개발자 패널 전용**이라 성격이 다르다 — 사람이 칩을
            // 눌러 고른 것이고, 새로 들어온 강아지 그림을 화면에서 확인하는 유일한
            // 길이다. 여기서 걸러 버리면 칩 25개 중 22개가 눌러도 아무 일이 없고,
            // 하필 "섞기"로 되돌아가서 고장 난 줄도 모른다.
            //
            // **되돌릴 때는 명부로 돌아간다.** 예전엔 [DogBreed.ROOM_BREEDS] 로 돌아갔는데,
            // 그러면 도구를 껐을 때 방에 **내 강아지가 아닌 개**가 남는다.
            d.breed = breed ?: roster[i]
        }
    }

    /** 격자 밖으로 못 나가게. */
    fun clampToFloor(p: Offset): Offset {
        val lo = 0.15f
        val hi = RoomSpec.GRID - 0.15f
        return Offset(p.x.coerceIn(lo, hi), p.y.coerceIn(lo, hi))
    }

    // -- 가구 막기 --------------------------------------------------------------
    //
    // 막힌 칸은 [MiniRoomState.occupiedCells] 가 준다. 거기서 `flat` 아트(러그·강아지
    // 침대)는 이미 빠져 있으므로 **바닥에 깔린 것 위로는 지나가고, 서 있는 가구는
    // 통과하지 못한다** — 규칙을 여기서 새로 정의하지 않는다. 아이템 하나를 flat 으로
    // 바꾸면 배치 점유와 강아지 통행이 같이 따라온다.

    /**
     * [radius] 는 마리마다 다르다 ([DogBreed.bodyRadius]). 래브라도가 지나가는 통로를
     * 치와와 반경으로 재면, 큰 개가 가구를 파고든 채 멈춘다.
     */
    private fun blockedAt(x: Float, y: Float, blocked: Set<IntOffset>, radius: Float): Boolean {
        if (blocked.isEmpty()) return false
        // 네 모서리. 가운데 한 점만 보면 모서리가 가구를 파고든다.
        for (dx in -1..1 step 2) {
            for (dy in -1..1 step 2) {
                val c = floor(x + dx * radius).toInt()
                val r = floor(y + dy * radius).toInt()
                if (IntOffset(c, r) in blocked) return true
            }
        }
        return false
    }

    /**
     * [from] 에서 [to] 로 가되 막힌 곳은 안 밟는다.
     *
     * 축을 하나씩 따로 시도하는 게 핵심이다. 둘을 한꺼번에 판정하면 가구 모서리에
     * 비스듬히 닿는 순간 완전히 멈춰서, 벽에 코를 박고 떠는 것처럼 보인다.
     * 축을 나누면 막힌 축만 버리고 **나머지 축으로 미끄러져** 가구를 타고 돌아간다.
     */
    private fun slide(from: Offset, to: Offset, blocked: Set<IntOffset>, radius: Float): Offset {
        var x = from.x
        var y = from.y
        if (!blockedAt(to.x, y, blocked, radius)) x = to.x
        if (!blockedAt(x, to.y, blocked, radius)) y = to.y
        return Offset(x, y)
    }

    /**
     * 막히지 않고 **다른 강아지와도 떨어진** 무작위 목표.
     *
     * 강아지끼리는 서로를 막지 않는다 — 지나가다 스치는 건 자연스럽고, 서로 막으면
     * 좁은 방에서 넷이 교착된다. 겹쳐 보이는 건 대부분 **둘이 같은 자리에 오래 서 있을
     * 때**라, 오래 머무는 자리(목적지)만 벌려두면 체감이 거의 해결된다.
     *
     * 간격은 **보장이 아니라 노력**이다. 가구가 많거나 방이 좁으면 지킬 자리가 없는데,
     * 그때 못 찾았다고 멈추면 강아지가 얼어붙는다. 그래서 두 단계로 물러선다 —
     * 먼저 [MIN_DOG_GAP] 을 지키며 찾고, 안 되면 간격을 포기하고 막힌 칸만 피한다.
     *
     * @param self 자기 자신은 간격 검사에서 빼고, 몸 반경도 이 마리 것으로 잰다
     */
    private fun freeSpot(blocked: Set<IntOffset>, self: DogActor? = null): Offset {
        val radius = self?.bodyRadius ?: DogBreed.ALL.first().bodyRadius
        repeat(12) {
            val p = randomSpot()
            if (!blockedAt(p.x, p.y, blocked, radius) && farFromOthers(p, self)) return p
        }
        // 간격을 못 지키는 상황 — 막힌 칸만 피한다
        repeat(12) {
            val p = randomSpot()
            if (!blockedAt(p.x, p.y, blocked, radius)) return p
        }
        return randomSpot()
    }

    /**
     * [p] 가 다른 강아지의 **자리와 목적지 양쪽**에서 떨어져 있는가.
     *
     * 목적지도 같이 보는 게 중요하다. 지금 자리만 보면 둘이 서로의 목적지를 향해
     * 걸어가다 같은 지점에서 만나 겹친 채 쉰다 — 뽑는 순간엔 멀었으니 통과해버린다.
     */
    private fun farFromOthers(p: Offset, self: DogActor?): Boolean {
        val gapSq = MIN_DOG_GAP * MIN_DOG_GAP
        for (o in dogs) {
            if (o === self) continue
            if ((o.pos - p).getDistanceSquared() < gapSq) return false
            if ((o.target - p).getDistanceSquared() < gapSq) return false
        }
        return true
    }

    /** 가장 가까운 빈 칸의 한가운데. 가구 밑에 깔렸을 때 걸어 나갈 방향이 된다. */
    private fun nearestFree(from: Offset, blocked: Set<IntOffset>): Offset {
        var best = from
        var bestD = Float.MAX_VALUE
        for (c in 0 until RoomSpec.GRID) {
            for (r in 0 until RoomSpec.GRID) {
                if (IntOffset(c, r) in blocked) continue
                val p = Offset(c + 0.5f, r + 0.5f)
                val d = (p - from).getDistanceSquared()
                if (d < bestD) {
                    bestD = d
                    best = p
                }
            }
        }
        return best
    }

    /**
     * 손으로 끌 때의 이동. 자율 이동과 **같은 규칙**을 탄다 —
     * 손가락으로도 책상을 뚫고 지나갈 수 없고, 모서리에서는 미끄러진다.
     */
    fun dragTo(dog: DogActor, desired: Offset, blocked: Set<IntOffset>) {
        dog.pos = slide(dog.pos, clampToFloor(desired), blocked, dog.bodyRadius)
    }

    /**
     * 매 프레임 호출. 목표까지 걸어가고, 도착하면 잠깐 쉬었다가 새 목표를 고른다.
     *
     * @param nowMs 프레임 시계 값
     * @param blocked 밟을 수 없는 칸 ([MiniRoomState.occupiedCells])
     */
    fun update(nowMs: Long, blocked: Set<IntOffset> = emptySet()) {
        if (lastMs == 0L) {
            lastMs = nowMs
            return
        }
        // 앱이 잠깐 멈췄다 돌아오면 dt 가 커져서 순간이동한다. 상한을 둔다.
        val dt = ((nowMs - lastMs) / 1000f).coerceIn(0f, 0.05f)
        lastMs = nowMs

        for (d in dogs) {
            if (d.id == draggingId) {
                d.moving = false
                continue
            }

            // 처음 자리가 나빴거나, 서 있는 자리에 가구가 놓였다. 충돌을 무시하고
            // 가까운 빈 칸으로 걸어 나온다 — 순간이동시키면 눈에 띄게 튄다.
            val trapped = blockedAt(d.pos.x, d.pos.y, blocked, d.bodyRadius)
            // **배웅한 아이는 제자리에 앉는다.** 가구에 깔린 경우에만 걸어 나온다 —
            // 안 그러면 서랍장 밑에 낀 채로 영영 앉아 있는다.
            if (d.departed && !trapped) {
                d.moving = false
                continue
            }
            if (trapped) {
                d.target = nearestFree(d.pos, blocked)
                d.restUntil = 0L
            } else if (nowMs < d.restUntil) {
                d.moving = false
                continue
            }

            val delta = d.target - d.pos
            val dist = delta.getDistance()
            if (dist < ARRIVE_DIST) {
                // 오래 쉰다. 계속 돌아다니면 방이 소란스럽고, 원래 원한 그림은
                // "제자리에서 꼬리 흔들기" 쪽이다. 여기 한 곳만 보면 된다.
                d.restUntil = nowMs + REST_MIN_MS + rnd.nextLong(REST_SPREAD_MS)
                d.target = freeSpot(blocked, d)
                d.moving = false
                continue
            }
            val step = d.speed * dt
            val want = clampToFloor(d.pos + delta * (step / dist).coerceAtMost(1f))
            // 갇힌 동안에는 막힘 판정을 끈다. 안 그러면 가구 밑에서 영영 못 나온다.
            val next = if (trapped) want else slide(d.pos, want, blocked, d.bodyRadius)

            val gained = (next - d.pos).getDistance()
            if (gained < step * 0.2f) {
                // 미끄러질 여지도 없이 막혔다. 떠는 대신 다른 목표를 고른다.
                d.target = freeSpot(blocked, d)
                d.restUntil = nowMs + 250L
                d.moving = false
                continue
            }

            // 화면상 가로 이동 방향 = (col - row). 아이소메트릭이라 격자 축과 다르다.
            // 목표 방향이 아니라 **실제로 간 방향**으로 판정해야 한다 — 가구를 타고
            // 미끄러지는 동안에는 둘이 다르고, 그때 몸이 엉뚱한 쪽을 본다.
            val movedBy = next - d.pos
            val screenDx = movedBy.x - movedBy.y
            if (abs(screenDx) > 0.0005f) d.mirrored = screenDx < 0f

            d.pos = next
            d.moving = true
            // 걸음 시계는 **시간 기반**이다 — 저쪽 목업 그대로.
            // 거리 기반으로 하면 발이 안 미끄러지는 대신, 미끄러져 돌아가는 동안
            // 다리가 얼어붙어 더 어색하다.
            d.phase += dt * WALK_FPS
        }

        // 앉기 <-> 서기. 위 루프가 `continue` 로 여러 군데서 빠져나가므로 여기서 한 번에 민다.
        // 0.18초쯤 걸려 자세가 바뀐다 — 즉시 바꾸면 몸이 순간이동한다.
        val k = (dt * 5.5f).coerceAtMost(1f)
        for (d in dogs) {
            d.stand += ((if (d.moving) 1f else 0f) - d.stand) * k
        }
    }

    /**
     * 뒤에 있는 강아지부터. 가구와 깊이로 섞으려면 [DogActor.depthCell] 순이어야 한다.
     * 같은 칸에 둘이 겹치면 그때만 실수 좌표로 가른다.
     */
    fun sortedByDepth(): List<DogActor> =
        dogs.sortedWith(compareBy({ it.depthCell }, { it.pos.x + it.pos.y }))

    companion object {
        // 걷는 속도는 여기 없다. 견종마다 다르므로 [DogBreed.speed] 가 갖는다.

        /** 워크 시트 프레임 속도. 시트가 4프레임이라 초당 1.25 바퀴 돈다. */
        const val WALK_FPS = 5f

        /** 목적지에 닿았다고 볼 거리. 속도와 같은 이유로 12/16 배 했다. */
        const val ARRIVE_DIST = 0.045f

        /** 도착 후 쉬는 시간. 4~10초 — 저쪽 값 그대로다. */
        const val REST_MIN_MS = 4000L
        const val REST_SPREAD_MS = 6000L

        /**
         * 강아지끼리 **목적지를 벌리는** 최소 거리(격자 단위).
         *
         * 몸 반경([DogBreed.bodyRadius]) 의 세 배쯤. 목적지를 완전 무작위로 뽑으면
         * 둘이 겹친 채 오래 서 있는 판이 눈에 띄게 늘어난다.
         *
         * 견종마다 반경이 달라도 **간격은 하나로 둔다.** 마리별로 다르게 하면 큰 개가
         * 고른 자리를 작은 개가 다시 밀어내는 식이라, 자리 고르기가 수렴하지 않는다.
         * 가장 큰 반경(래브라도 0.5625) 의 두 배보다 넉넉하면 충분하다.
         *
         * 마리 수를 늘릴 거면 여길 같이 봐야 한다. 값이 크고 마리가 많으면
         * [freeSpot] 의 1단계가 매번 실패해서 간격이 사실상 없는 것과 같아진다.
         */
        const val MIN_DOG_GAP = 1.3f
    }
}

/**
 * 강아지의 앞뒤 정렬 키 — **발끝이 딛고 있는 칸**으로 잰다.
 *
 * 실수 좌표를 그대로 `x + y` 로 쓰면 안 된다. 가구는 칸을 통째로 차지해서 정렬 키가
 * 정수인데, 강아지만 연속값이면 **자가 서로 다르다.** 예를 들어
 *
 * ```
 * 책상 (4,1)        -> 정렬 키 5
 * 강아지 (3.7, 1.5) -> 3.7 + 1.5 = 5.2  > 5  ->  "앞"
 * ```
 *
 * 강아지의 col 은 3 이라 책상보다 **한 칸 뒤**인데 앞으로 판정된다. 뒤에 있는 강아지가
 * 책상 위에 그려지니 몸이 책상을 뚫고 지나가는 것처럼 보인다.
 * 칸으로 내리면 `3 + 1 = 4 < 5` 라 제대로 뒤로 간다.
 *
 * 칸 단위라 값이 툭툭 끊기는 것도 이득이다 — 가구 옆에서 조금 흔들려도 앞뒤가
 * 깜빡이지 않는다. 게다가 [DogHerd] 의 몸 반경 덕에 막힌 칸 경계에 딱 붙어 서지도 못한다.
 */
val DogActor.depthCell: Int get() = floor(pos.x).toInt() + floor(pos.y).toInt()

/**
 * 명부대로 선 무리를 기억한다. 명부가 바뀌면 **자리를 지킨 채** 갈아끼운다
 * ([DogHerd.setRoster]).
 */
@Composable
fun rememberDogHerd(roster: List<DogBreed>, departed: Set<Int> = emptySet()): DogHerd {
    val herd = remember { DogHerd(roster) }
    remember(roster) { herd.setRoster(roster); roster }
    // 명부 **뒤에** 얹는다. setRoster 가 는 마리를 새로 만들면서 이 표시를 지운다.
    remember(roster, departed) { herd.setDeparted(departed); departed }
    return herd
}
