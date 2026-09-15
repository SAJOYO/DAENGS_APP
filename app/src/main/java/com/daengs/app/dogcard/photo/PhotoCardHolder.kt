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

    /** 받아 둔 완성 그림. 여기 있어야 칸에 완성으로 뜬다. */
    var images: Map<String, File> by mutableStateOf(emptyMap())
        private set

    var creating: Boolean by mutableStateOf(false)
        private set

    /** 만들기 화면에 띄울 서버 문장. */
    var createError: String? by mutableStateOf(null)
        private set

    /** 목록·지우기 실패. 도감이 한 줄로 알린다. */
    var error: String? by mutableStateOf(null)
        private set

    val generating: Boolean get() = cards.any { it.status == PhotoCardStatus.Generating }

    val latestFailure: PhotoCard? get() = cards.firstOrNull { it.status == PhotoCardStatus.Failed }

    fun clearCreateError() { createError = null }

    fun clearError() { error = null }

    /** 목록을 받는다. **실패해도 들고 있던 것을 안 비운다** (`CardHolder.load` 원칙). */
    suspend fun load() {
        val token = accessToken() ?: return
        remote.list(token)
            .onSuccess { list ->
                cards = list
                files.keepOnly(list.map { it.id }.toSet())
                images = files.existing()
                fetchImages(token)
            }
            .onFailure { error = it.message ?: "포토 카드를 불러오지 못했어요." }
    }

    suspend fun create(month: Int, dogName: String, dogId: String?, jpeg: ByteArray): Boolean {
        val token = accessToken() ?: run {
            createError = "로그인하면 포토 카드를 만들 수 있어요"
            return false
        }
        creating = true
        createError = null
        val result = remote.create(token, month, dogName, dogId, jpeg)
        creating = false
        return result.fold(
            onSuccess = { made -> cards = listOf(made) + cards.filterNot { it.id == made.id }; true },
            onFailure = { createError = it.message ?: "카드를 만들지 못했어요."; false },
        )
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
            cards = cards.map { if (it.id == detail.card.id) detail.card else it }
            if (detail.card.status == PhotoCardStatus.Ready) store(detail)
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

    private suspend fun store(detail: PhotoCardDetail) {
        val url = detail.imageUrl ?: return
        val png = remote.download(url).getOrNull() ?: return
        val file = files.write(detail.card.id, png) ?: return
        images = images + (detail.card.id to file)
    }
}
