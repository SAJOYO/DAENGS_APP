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

    /** 비우거나 지우기 도중 받던 그림이 되살아나지 않도록. */
    private var epoch = 0

    val generating: Boolean get() = cards.any { it.status == PhotoCardStatus.Generating }

    val latestFailure: PhotoCard? get() = cards.firstOrNull { it.status == PhotoCardStatus.Failed }

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
        cards = list
        files.keepOnly(list.map { it.id }.toSet())
        images = files.existing()
        // keepOnly 가 걸린 동안 비워졌을 수 있다 — 그 사이 온 그림을 또 받지 않는다.
        if (epoch != started) return
        fetchImages(token)
    }

    suspend fun create(month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Boolean {
        val token = accessToken() ?: run {
            createError = "로그인하면 포토 카드를 만들 수 있어요"
            return false
        }
        // 비우는 동안(로그아웃·탈퇴) 요청이 끝나면 이전 사람의 카드나 오류 문장이 다음
        // 사람 화면에 남는다 — `store()` 와 같은 이유로 세대 번호를 찍어 둔다.
        val started = epoch
        creating = true
        createError = null
        return try {
            val result = remote.create(token, month, dogName, dogId, jpeg)
            if (epoch != started) return false
            result.fold(
                onSuccess = { made -> cards = listOf(made) + cards.filterNot { it.id == made.id }; true },
                onFailure = { createError = it.message ?: "카드를 만들지 못했어요."; false },
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
        createError = null
        error = null
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
