package com.daengs.app.dogcard.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/** 만드는 중일 때 다시 묻는 간격. 한 장에 30~60초라 5초면 끝난 뒤 금방 뜬다. */
const val PHOTO_POLL_MS = 5_000L

/**
 * 이만큼 지나도 만드는 중이면 조회를 멈추고 목록을 다시 받는다. 서버는 9분(엔진·검수
 * 타임아웃 × 재시도 + 60초)이 지나면 조회 때 `interrupted` 로 바꿔 둔다.
 */
const val PHOTO_STALE_MS = 10 * 60_000L

/** 카드를 막는 데 세는 상태 — `Failed` 는 다시 만들 수 있어야 하니 뺀다 (docs/photo-cards.md §9.2). */
private val TAKEN_STATUSES = setOf(PhotoCardStatus.Ready, PhotoCardStatus.Generating)

/**
 * 포토 카드를 들고 있는 자리. `CardHolder` 와 같은 결이다 (`mutableStateOf` 홀더).
 *
 * **정본은 서버다.** 기기에는 완성 그림 파일만 있다 — 그래서 지우기는 누끼 카드와 반대로
 * **서버가 먼저**고, 서버가 못 지우면 기기에서도 안 지운다 (docs/photo-cards.md §3).
 */
class PhotoCardHolder(
    private val remote: PhotoCardRemote,
    private val files: PhotoCardFiles,
    private val accessToken: suspend () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val reveals: PhotoRevealLog = MemoryRevealLog(),
) {
    /** 최근이 앞이다. 실패한 카드도 들고 있는다 — 머리말의 실패 한 줄이 쓴다. */
    var cards: List<PhotoCard> by mutableStateOf(emptyList())
        private set

    /**
     * 받아 둔 완성 그림. 여기 있어야 칸에 완성으로 뜬다.
     *
     * **켜지자마자 기기에 있는 파일부터 쓴다** — 목록이 오기 전에도(콜드스타트·오프라인)
     * 방 액자와 칸이 발바닥이 아니라 그림을 보여준다. `load()` 가 서버 목록으로 맞춘다.
     */
    var images: Map<String, File> by mutableStateOf(files.existing())
        private set

    var creating: Boolean by mutableStateOf(false)
        private set

    /** 만들기 화면에 띄울 서버 문장. */
    var createError: String? by mutableStateOf(null)
        private set

    /**
     * 지우기 실패(그리고 토큰 없이 지우려 할 때). 도감이 한 줄로 알린다.
     * **목록 실패는 안 쓴다** — `load()` 참고.
     */
    var error: String? by mutableStateOf(null)
        private set

    /** 결과를 아직 안 본 카드. 만든 직후 넣고, 결과 화면을 보면 뺀다 (§8.3). */
    var unrevealed: Set<String> by mutableStateOf(reveals.load())
        private set

    /**
     * 오늘 남은 하루 한도. `null` 이면 **#543 배포 전이거나 무제한** — 그때는 막지 않는다
     * (docs/photo-cards.md §9.1). 카드가 완성돼 그림을 받는 순간 하나 줄이고, 다음 목록에서
     * 서버 값으로 다시 맞춘다.
     */
    var dailyRemaining: Int? by mutableStateOf(null)
        private set

    /** 도감이 「완성됐어요」 로 알릴 카드 — 결과를 안 봤고, 완성이고, 그림까지 받은 것. */
    val readyToReveal: PhotoCard?
        get() = cards.firstOrNull { it.id in unrevealed && it.status == PhotoCardStatus.Ready && it.id in images }

    fun markRevealed(id: String) {
        if (id !in unrevealed) return
        rememberUnrevealed(unrevealed - id)
    }

    private fun rememberUnrevealed(ids: Set<String>) {
        unrevealed = ids
        reveals.save(ids)
    }

    /** 비우거나 지우기 도중 받던 그림이 되살아나지 않도록. */
    private var epoch = 0

    val generating: Boolean get() = cards.any { it.status == PhotoCardStatus.Generating }

    val latestFailure: PhotoCard? get() = cards.firstOrNull { it.status == PhotoCardStatus.Failed }

    /**
     * 그 강아지가 이미 `Ready`·`Generating` 카드를 가진 카드 — 만들기 화면이 칸을 막는 데 쓴다
     * (docs/photo-cards.md §9.2). **보호자마다 따로 센다**(결정 14) — `cards` 는 이미 이
     * 보호자의 목록이라 그대로 세면 된다. `dogId` 가 없으면(강아지를 아직 안 골랐으면) 빈 집합.
     *
     * **카드 종류마다 센다** (#593, D-085) — 4월 카드가 딸기를 막지 않고 딸기가 상추를 막지
     * 않는다. 서버의 `repositories/ai_card.py::has_card` 와 같은 단위다.
     */
    fun takenCards(dogId: String?): Set<PhotoCardKey> {
        if (dogId == null) return emptySet()
        return cards
            .filter { it.dogId == dogId && it.status in TAKEN_STATUSES }
            .map { it.key }
            .toSet()
    }

    fun clearCreateError() { createError = null }

    fun clearError() { error = null }

    /**
     * 목록을 받는다. **실패해도 들고 있던 것을 안 비운다** (`CardHolder.load` 원칙).
     *
     * **실패해도 조용하다.** 도감을 열 때마다 도는 자리라, 여기서 `error` 를 쓰면
     * 오프라인이거나 서버 점검 중인 사람이 도감을 열 때마다 토스트를 본다 — 지우기와
     * 달리 사용자가 방금 누른 동작이 아니다(docs/photo-cards.md §3). `error` 는
     * `remove()` 실패에만 쓴다.
     */
    suspend fun load() {
        val token = accessToken() ?: return
        // 비우는 동안(로그아웃·탈퇴) 목록이 뒤늦게 오면 이전 사람 것이 되살아난다 —
        // `create()`·`store()` 와 같은 이유로 세대 번호를 찍어 둔다.
        val started = epoch
        val list = remote.list(token).getOrNull() ?: return
        if (epoch != started) return
        // **그림을 받기 전에는 완성으로 바꾸지 않는다.** 여기서 로컬이 이미 `Generating`
        // 이던 카드가 새 목록에서 `Ready` 로 왔다고 바로 반영하면(10분 스테일 경로·시계가
        // 앞선 기기라 매번 이 경로를 타는 기기), `generating` 이 꺼져 이 코루틴을 돌리던
        // `MainActivity` 의 `LaunchedEffect(photos.generating)` 이 취소되고 그림을 못 받는다
        // — `pollOnce()` 를 고친 것과 같은 문제(커밋 2d4cd243). 그림을 받아 `store()` 할
        // 때까지 로컬 사본(Generating)을 그대로 낸다. 로컬에서 이미 완성·실패였던 카드나
        // 로컬에 없던 새 카드는 그대로 서버 값을 쓴다.
        val stillDrawing = cards.filter { it.status == PhotoCardStatus.Generating }.associateBy { it.id }
        cards = list.cards.map { fresh ->
            val waiting = stillDrawing[fresh.id]
            if (waiting != null && fresh.status == PhotoCardStatus.Ready) waiting else fresh
        }
        dailyRemaining = list.dailyRemaining
        val kept = unrevealed.filter { id -> list.cards.any { it.id == id && it.status != PhotoCardStatus.Failed } }.toSet()
        if (kept != unrevealed) rememberUnrevealed(kept)
        files.keepOnly(list.cards.map { it.id }.toSet())
        images = files.existing()
        // keepOnly 가 걸린 동안 비워졌을 수 있다 — 그 사이 온 그림을 또 받지 않는다.
        if (epoch != started) return
        fetchImages(token)
        // 방금 위에서 붙잡아 둔 카드들 — 그림을 받아야 완성으로 바꾼다. `pollOnce()` 의
        // 조회 한 장과 같은 순서다: 그림부터 받고(`store`), 성공해야 `cards` 를 바꾼다.
        // `store()` 가 비우기·지우기 도중이면 스스로 거른다.
        list.cards.filter { fresh -> stillDrawing.containsKey(fresh.id) && fresh.status == PhotoCardStatus.Ready }
            .forEach { fresh ->
                val detail = remote.get(token, fresh.id).getOrNull() ?: return@forEach
                if (detail.card.status == PhotoCardStatus.Ready && !store(detail)) return@forEach
                cards = cards.map { if (it.id == detail.card.id) detail.card else it }
            }
    }

    suspend fun create(card: PhotoCardKey, dogName: String, dogId: String?, jpeg: ByteArray, titleName: String? = null): String? {
        val token = accessToken() ?: run {
            createError = "로그인하면 포토 카드를 만들 수 있어요"
            return null
        }
        // 비우는 동안(로그아웃·탈퇴) 요청이 끝나면 이전 사람의 카드나 오류 문장이 다음
        // 사람 화면에 남는다 — `store()` 와 같은 이유로 세대 번호를 찍어 둔다.
        val started = epoch
        creating = true
        createError = null
        return try {
            val result = remote.create(token, card, dogName, dogId, jpeg, titleName)
            if (epoch != started) return null
            result.fold(
                onSuccess = { made ->
                    cards = listOf(made) + cards.filterNot { it.id == made.id }
                    rememberUnrevealed(unrevealed + made.id)
                    made.id
                },
                onFailure = { createError = it.message ?: "카드를 만들지 못했어요."; null },
            )
        } finally {
            creating = false
        }
    }

    suspend fun pollOnce() {
        if (!generating) return
        val token = accessToken() ?: return
        val stale = cards.any { it.status == PhotoCardStatus.Generating && now() - it.createdAtMillis > PHOTO_STALE_MS }
        if (stale) {
            load()
            return
        }
        cards.filter { it.status == PhotoCardStatus.Generating }.forEach { waiting ->
            val detail = remote.get(token, waiting.id).getOrNull() ?: return@forEach
            // **그림을 먼저 받고 나서 완성으로 바꾼다.** 먼저 바꾸면 `generating` 이 꺼져
            // 이 조회를 돌리던 코루틴이 취소되고(`MainActivity` 의 `LaunchedEffect(photos.generating)`),
            // 받던 그림이 끊겨 칸이 계속 「만드는 중」 으로 남는다. 못 받으면 다음 조회에서 다시 받는다.
            if (detail.card.status == PhotoCardStatus.Ready && !store(detail)) return@forEach
            cards = cards.map { if (it.id == detail.card.id) detail.card else it }
            // 서버가 한도를 쓰는 건 완성되는 순간이다 — 그림을 받아 칸이 완성으로 바뀌는
            // 지금 하나 줄이고, 다음 목록에서 서버 값으로 다시 맞춘다 (docs §9.2).
            if (detail.card.status == PhotoCardStatus.Ready) {
                dailyRemaining = dailyRemaining?.let { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    /** 만드는 중이 있는 동안만 돈다. 부르는 쪽이 앱이 앞에 있을 때만 부른다. */
    suspend fun pollWhileGenerating(intervalMs: Long = PHOTO_POLL_MS) {
        while (generating) {
            delay(intervalMs)
            pollOnce()
        }
    }

    suspend fun remove(id: String): Boolean {
        val token = accessToken() ?: run {
            error = "로그인하면 지울 수 있어요"
            return false
        }
        return remote.delete(token, id).fold(
            onSuccess = {
                cards = cards.filterNot { it.id == id }
                images = images - id
                files.delete(id)
                if (id in unrevealed) rememberUnrevealed(unrevealed - id)
                true
            },
            onFailure = { error = it.message ?: "카드를 지우지 못했어요."; false },
        )
    }

    /** 로그아웃·탈퇴. 다음 사람이 남의 카드 그림을 물려받으면 안 된다. */
    suspend fun forget() {
        epoch++
        cards = emptyList()
        images = emptyMap()
        dailyRemaining = null
        createError = null
        error = null
        rememberUnrevealed(emptySet())
        files.clear()
    }

    private suspend fun fetchImages(token: String) {
        cards.filter { it.status == PhotoCardStatus.Ready && it.id !in images }.forEach { ready ->
            remote.get(token, ready.id).getOrNull()?.let { store(it) }
        }
    }

    /** 그림 파일을 남기고 `images` 에 넣었으면 true — `pollOnce()` 가 이걸 보고 완성으로 바꾼다. */
    private suspend fun store(detail: PhotoCardDetail): Boolean {
        val started = epoch
        val id = detail.card.id
        val url = detail.imageUrl ?: return false
        val png = remote.download(url).getOrNull() ?: return false
        // 다운로드 도중 비우거나 이 카드를 지우면 받은 파일을 버린다
        if (epoch != started || cards.none { it.id == id }) {
            files.delete(id)
            return false
        }
        val file = files.write(id, png) ?: return false
        // 저장 도중에도 다시 확인한다
        if (epoch != started || cards.none { it.id == id }) {
            files.delete(id)
            return false
        }
        images = images + (id to file)
        return true
    }
}
