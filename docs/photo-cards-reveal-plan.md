# 포토 카드 만든 뒤 보여 주기 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 포토 카드를 만들면 만들기 화면에서 기다렸다 뒤집기 → 결과를 보여 주고, 나가면 도감에서 완성 알림으로 보여 주며, 완성돼도 칸이 안 바뀌던 것을 고친다.

**Architecture:** 원인 수정은 `PhotoCardHolder.pollOnce()` 에서 그림을 받은 뒤에 완성으로 바꾸는 한 곳. 연출은 새 파일 `ui/dex/PhotoReveal.kt`(뒷면·뒤집기·결과·단계 판정)에 모아 만들기 화면과 도감이 같이 쓴다. 「결과를 안 본 완성 카드」 는 홀더의 `unrevealed` 집합으로 들고 `PhotoRevealLog` 로 기기에 남긴다.

**Tech Stack:** Kotlin · Jetpack Compose · JUnit4 + kotlinx-coroutines-test

**Spec:** [`docs/photo-cards.md`](photo-cards.md) **§8** (그리고 §3·§5) — 먼저 읽는다.

## Global Constraints

- 커밋 메시지는 **한글 서술형, 접두사 없음.** 제목은 무엇이 어떻게 잘못돼 있었는지(`~던 것`), 본문에 왜·무엇을 재봤는지(숫자는 JUnit XML 에서 읽은 그대로). 끝에 정확히 두 줄 — **자기 모델 이름으로 바꾸지 않는다**:
  `Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01Nz4skkb8UjiEWnbWj16iwf`
  메시지는 Write 도구로 scratchpad 파일에 쓰고 `git commit -F <파일>`.
- 빌드·테스트 (Git Bash, **매 호출마다** export): `cd /c/Users/403/Documents/workspace/DAENGS_APP && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat …` (timeout 600000).
- 전체 단위 테스트에는 **dev 에도 있는 실패 1건 `FacilityConnectedUiTest`** 가 있다. 그것만 실패면 커밋 본문에 그렇게 적는다(BUILD SUCCESSFUL 이라고 쓰지 않는다).
- 파일은 Read/Edit/Write 도구로만 (heredoc·sed 금지 — 한글이 깨진다). `git add` 는 바꾼 파일만 (`-A`·`.` 금지 — `.claude/` 가 있다). push·stash·amend 금지.
- 화면 파일에 **새 날것 색(`Color(0x…)`) 금지.** 쓸 수 있는 색: `ui/theme` 의 `CreamBg · CardWhite · DaengPink · DaengPinkDeep · PinkSoft · PinkFaint · TextDark · TextMuted` (알파는 `.copy(alpha = …)`).
- 새 Composable 에 `@Preview`. `DesignLockTest` 가 깨지면 테스트를 고치지 말고 되돌린다.
- 야채·과일(누끼) 카드 동작은 바뀌면 안 된다.
- 연출 값: 뒤집기 **760ms**, 90도에서 앞면으로, `cameraDistance = 14f * density` (누끼 `FlipToCard` 와 같다).
- 사용자에게 「AI 카드」라는 말을 띄우지 않는다.

## 파일 지도

| 파일 | 새/고침 | 책임 |
| --- | --- | --- |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt` | 고침 | 그림 받은 뒤 완성 · `unrevealed` · `readyToReveal` · `markRevealed` · `create` 가 id 를 돌려줌 |
| `app/src/main/java/com/daengs/app/dogcard/photo/PhotoRevealLog.kt` | 새 | `PhotoRevealLog` · `MemoryRevealLog` · `PrefsRevealLog` |
| `app/src/main/java/com/daengs/app/ui/dex/PhotoReveal.kt` | 새 | `PhotoMakeStage` · `photoMakeStage()` · `PhotoCardBack` · `PhotoFlip` · `PhotoResult` · `PhotoRevealFlow` |
| `app/src/main/java/com/daengs/app/ui/dex/PhotoCardMakeScreen.kt` | 고침 | 사진 고르기 → 그리는 중 → 뒤집기·결과 / 실패 |
| `app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt` | 고침 | 만드는 중 칸 뒷면 · 완성 알림 한 줄 · 알림에서 여는 결과 |
| `app/src/main/java/com/daengs/app/MainActivity.kt` | 고침 | 배선 |
| 테스트 `dogcard/photo/PhotoCardHolderTest.kt`(더함) · `ui/dex/PhotoMakeStageTest.kt`(새) | | |

---

### Task 1: 완성돼도 칸이 안 바뀌던 것 (원인 수정)

**원인 (컨트롤러가 코드에서 찾은 가설 — 테스트로 확정한다):** `pollOnce()` 가 카드를 먼저 `Ready` 로 바꾸고(`cards = cards.map …`) 그다음 `store(detail)` 로 그림을 받는다. 바꾸는 순간 `generating` 이 `false` 가 되고, `MainActivity` 의 `LaunchedEffect(photos.generating)` 가 키가 바뀌어 **자기 코루틴을 취소**한다 — 그 코루틴 안에서 받던 다운로드가 끊겨 카드는 `Ready` 인데 파일이 없고(=칸은 계속 「만드는 중」), 도감을 다시 열어 `load()` → `fetchImages()` 가 돌아야 그림이 생긴다.

**Files:**
- Modify: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt` (`pollOnce`, `store`)
- Test: `app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardHolderTest.kt`

**Interfaces:**
- Produces: `private suspend fun store(detail: PhotoCardDetail): Boolean` (그림 파일을 남기고 `images` 에 넣었으면 true). `pollOnce()` 는 **`Ready` 인 카드의 그림을 받은 뒤에만** `cards` 에서 그 카드를 `Ready` 로 바꾼다. 그림을 못 받으면 `Generating` 으로 남겨 다음 조회에서 다시 받는다.

- [ ] **Step 1: 실패하는 테스트 두 개를 더한다** — 먼저 `PhotoCardHolderTest` 의 `FakeRemote` 를 읽고, `download` 에 `var failDownload = false` 를 더한다(참이면 `Result.failure(IllegalStateException("그림을 받지 못했어요"))`, 기존 `onDownload` 훅은 그 앞에서 그대로 부른다). 그리고:

```kotlin
    /**
     * **그림을 받는 동안 카드가 아직 완성이면 안 된다.** 완성으로 먼저 바꾸면 `generating` 이
     * 꺼져 조회 코루틴(`LaunchedEffect(photos.generating)`)이 취소되고, 받던 그림이 끊겨
     * 칸이 계속 「만드는 중」 으로 남는다 — 실기기에서 도감을 나갔다 와야 바뀌던 원인.
     */
    @Test
    fun `그림을 받는 동안에는 아직 완성으로 바꾸지 않는다`() = runTest {
        val remote = FakeRemote()
        val h = holder(remote)
        h.create(4, "콩이", null, byteArrayOf(9))
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Ready, likeness = 4)
        remote.urls["new-4"] = "https://x/n.png"
        var seen: PhotoCardStatus? = null
        remote.onDownload = { seen = h.cards.first().status }
        h.pollOnce()
        assertEquals(PhotoCardStatus.Generating, seen)
        assertEquals(PhotoCardStatus.Ready, h.cards.first().status)
        assertTrue(h.images.containsKey("new-4"))
    }

    @Test
    fun `그림을 못 받으면 만드는 중으로 남아 다음 조회에서 다시 받는다`() = runTest {
        val remote = FakeRemote()
        val h = holder(remote)
        h.create(4, "콩이", null, byteArrayOf(9))
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Ready)
        remote.urls["new-4"] = "https://x/n.png"
        remote.failDownload = true
        h.pollOnce()
        assertEquals(PhotoCardStatus.Generating, h.cards.first().status)
        assertTrue(h.generating)
        remote.failDownload = false
        h.pollOnce()
        assertEquals(PhotoCardStatus.Ready, h.cards.first().status)
        assertTrue(h.images.containsKey("new-4"))
    }
```

(`onDownload` 훅의 실제 타입은 파일에서 확인하고 맞춘다. `h.create` 는 이 태스크에서는 아직 `Boolean` 을 돌려준다.)

- [ ] **Step 2: 돌려서 실패를 본다**

Run: `… ./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.PhotoCardHolderTest'`
Expected: 첫 테스트는 `seen` 이 `Ready` 라 FAIL, 둘째는 첫 조회 뒤 `Ready` 라 FAIL. 이 실패 출력이 원인 확정의 근거다 — 보고서에 그대로 붙인다.

- [ ] **Step 3: 고친다** — `store` 가 `Boolean` 을 돌려주게 하고(모든 `return` 을 `return false`, 마지막 `images = …` 뒤 `return true`), `pollOnce` 의 반복을:

```kotlin
        cards.filter { it.status == PhotoCardStatus.Generating }.forEach { waiting ->
            val detail = remote.get(token, waiting.id).getOrNull() ?: return@forEach
            // **그림을 먼저 받고 나서 완성으로 바꾼다.** 먼저 바꾸면 `generating` 이 꺼져
            // 이 조회를 돌리던 코루틴이 취소되고(`MainActivity` 의 `LaunchedEffect(photos.generating)`),
            // 받던 그림이 끊겨 칸이 계속 「만드는 중」 으로 남는다. 못 받으면 다음 조회에서 다시 받는다.
            if (detail.card.status == PhotoCardStatus.Ready && !store(detail)) return@forEach
            cards = cards.map { if (it.id == detail.card.id) detail.card else it }
        }
```

`fetchImages` 의 `store(it)` 는 결과를 안 써도 된다.

- [ ] **Step 4: 통과를 본다** — 같은 명령, `TEST-com.daengs.app.dogcard.photo.PhotoCardHolderTest.xml` 의 `tests=`/`failures=` 를 읽는다.

- [ ] **Step 5: 커밋** — 제목 `완성돼도 도감을 나갔다 와야 포토 카드 칸이 바뀌던 것`. 본문: 원인(완성으로 먼저 바꿔 조회 코루틴이 취소되며 다운로드가 끊김), 고친 방법, RED→GREEN 테스트 수.

---

### Task 2: 결과를 안 본 완성 카드 기억하기

**Files:**
- Create: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoRevealLog.kt`
- Modify: `app/src/main/java/com/daengs/app/dogcard/photo/PhotoCardHolder.kt`
- Test: `app/src/test/java/com/daengs/app/dogcard/photo/PhotoCardHolderTest.kt`

**Interfaces:**
- Consumes: Task 1 의 `store(): Boolean`.
- Produces:
  - `interface PhotoRevealLog { fun load(): Set<String>; fun save(ids: Set<String>) }` · `class MemoryRevealLog(initial: Set<String> = emptySet()) : PhotoRevealLog` · `class PrefsRevealLog(context: Context) : PhotoRevealLog`
  - `PhotoCardHolder(remote, files, accessToken, now = System::currentTimeMillis, reveals: PhotoRevealLog = MemoryRevealLog())`
  - `val unrevealed: Set<String>` (state) · `val readyToReveal: PhotoCard?` (결과를 안 봤고 `Ready` 이고 그림이 있는 첫 카드) · `fun markRevealed(id: String)`
  - **`suspend fun create(...): String?`** — 만든 카드 id, 실패·세대 바뀜·토큰 없음이면 null

- [ ] **Step 1: 테스트를 고치고 더한다** — 기존 `h.create(...)` 단정 네 줄: `assertTrue(h.create(…))` → `assertNotNull(h.create(…))`, `assertFalse(h.create(…))` → `assertNull(h.create(…))`. 그리고:

```kotlin
    @Test
    fun `만들면 결과를 안 본 카드로 기억한다`() = runTest {
        val log = MemoryRevealLog()
        val h = PhotoCardHolder(FakeRemote(), PhotoCardFiles(tmp.newFolder()), { "t" }, { 2_000L }, log)
        val id = h.create(4, "콩이", null, byteArrayOf(9))
        assertEquals(setOf(id), h.unrevealed)
        assertEquals(setOf(id), log.load())
        assertNull("아직 그리는 중이라 알릴 카드는 없다", h.readyToReveal)
    }

    @Test
    fun `완성되고 그림까지 받아야 알릴 카드가 되고 결과를 보면 빠진다`() = runTest {
        val remote = FakeRemote()
        val log = MemoryRevealLog()
        val h = PhotoCardHolder(remote, PhotoCardFiles(tmp.newFolder()), { "t" }, { 2_000L }, log)
        val id = h.create(4, "콩이", null, byteArrayOf(9))!!
        remote.server[0] = remote.server[0].copy(status = PhotoCardStatus.Ready)
        remote.urls[id] = "https://x/n.png"
        h.pollOnce()
        assertEquals(id, h.readyToReveal?.id)
        h.markRevealed(id)
        assertNull(h.readyToReveal)
        assertTrue(log.load().isEmpty())
    }

    @Test
    fun `켜질 때 기억해 둔 카드를 이어받고 목록에 없거나 실패한 id 는 버린다`() = runTest {
        val remote = FakeRemote().apply {
            server += card("a", 4, PhotoCardStatus.Ready)
            server += card("f", 9, PhotoCardStatus.Failed)
            urls["a"] = "https://x/a.png"
        }
        val log = MemoryRevealLog(setOf("a", "f", "gone"))
        val h = PhotoCardHolder(remote, PhotoCardFiles(tmp.newFolder()), { "t" }, { 2_000L }, log)
        assertEquals(setOf("a", "f", "gone"), h.unrevealed)
        h.load()
        assertEquals(setOf("a"), h.unrevealed)
        assertEquals(setOf("a"), log.load())
        assertEquals("a", h.readyToReveal?.id)
    }

    @Test
    fun `로그아웃하면 결과 안 본 기억도 비운다`() = runTest {
        val log = MemoryRevealLog(setOf("a"))
        val h = PhotoCardHolder(FakeRemote(), PhotoCardFiles(tmp.newFolder()), { "t" }, { 2_000L }, log)
        h.forget()
        assertTrue(h.unrevealed.isEmpty())
        assertTrue(log.load().isEmpty())
    }
```

- [ ] **Step 2: 실패를 본다** (컴파일 실패 — `MemoryRevealLog` 없음).

- [ ] **Step 3: `PhotoRevealLog.kt`**

```kotlin
package com.daengs.app.dogcard.photo

import android.content.Context

/**
 * **결과를 아직 안 본 완성 카드** 의 id. 만들기 화면을 나가 기다린 사람에게 도감이 「완성됐어요」
 * 를 알리는 데 쓴다 (docs/photo-cards.md §8.3). 방에 있는 동안 완성돼도 다음에 도감을 열 때 알리려고
 * 기기에 남긴다.
 *
 * 홀더는 이 인터페이스만 본다 — JVM 테스트가 [MemoryRevealLog] 로 갈아 끼운다.
 */
interface PhotoRevealLog {
    fun load(): Set<String>
    fun save(ids: Set<String>)
}

class MemoryRevealLog(initial: Set<String> = emptySet()) : PhotoRevealLog {
    private var ids = initial
    override fun load(): Set<String> = ids
    override fun save(ids: Set<String>) { this.ids = ids }
}

/**
 * `SharedPreferences` 에 쉼표로 이어 둔다 (`MissLog` 와 같은 결). 로그아웃하면 홀더가 빈 집합을
 * 저장해 비운다 — 계정별 키를 따로 두지 않는다.
 */
class PrefsRevealLog(context: Context) : PhotoRevealLog {
    private val prefs = context.applicationContext.getSharedPreferences("photo-reveal", Context.MODE_PRIVATE)

    override fun load(): Set<String> =
        prefs.getString(KEY, null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    override fun save(ids: Set<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    private companion object { const val KEY = "unrevealed" }
}
```

- [ ] **Step 4: 홀더** — 생성자 끝에 `private val reveals: PhotoRevealLog = MemoryRevealLog(),`. 필드:

```kotlin
    /** 결과를 아직 안 본 카드. 만든 직후 넣고, 결과 화면을 보면 뺀다 (§8.3). */
    var unrevealed: Set<String> by mutableStateOf(reveals.load())
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
```

- `create`: 반환형 `String?`. 토큰 없음·세대 바뀜 → `return null`. `onSuccess = { made -> cards = …; rememberUnrevealed(unrevealed + made.id); made.id }`, `onFailure = { createError = …; null }`.
- `load()`: `cards = list` 다음 줄에 — 실패 카드는 머리말 한 줄이 알리므로 여기서 버린다:
```kotlin
        val kept = unrevealed.filter { id -> list.any { it.id == id && it.status != PhotoCardStatus.Failed } }.toSet()
        if (kept != unrevealed) rememberUnrevealed(kept)
```
- `forget()`: `rememberUnrevealed(emptySet())` 를 더한다.
- `remove(id)` 성공 갈래에 `if (id in unrevealed) rememberUnrevealed(unrevealed - id)`.

- [ ] **Step 4b: spec 표를 구현에 맞춘다** — `docs/photo-cards.md` §8.4 표에서 `dogcard/photo/PhotoRevealStore.kt` 줄을 `dogcard/photo/PhotoRevealLog.kt` · "`unrevealed` 를 기기에 남기는 작은 저장소 (SharedPreferences 한 키, 로그아웃하면 홀더가 비운다). JVM 테스트는 `MemoryRevealLog`" 로 바꾼다 (Edit 도구).

- [ ] **Step 5: `MainActivity` 가 `create` 의 Boolean 을 쓰던 자리를 컴파일되게만 고친다** — `if (photos.create(…)) done()` → `if (photos.create(…) != null) done()` (Task 6 에서 다시 바꾼다).

- [ ] **Step 6: 통과·빌드** — `… :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.dogcard.photo.*'`.

- [ ] **Step 7: 커밋** — 제목 `나가서 기다린 사람에게 포토 카드가 완성됐다고 알릴 기억이 없던 것`.

---

### Task 3: 뒷면 · 뒤집기 · 결과 부품

**Files:**
- Create: `app/src/main/java/com/daengs/app/ui/dex/PhotoReveal.kt`
- Test: `app/src/test/java/com/daengs/app/ui/dex/PhotoMakeStageTest.kt`

**Interfaces:**
- Consumes: `PhotoCard` · `PhotoCardStatus` (dogcard/photo) · `PHOTO_RATIO` · `DexCard` · `CardArt.Local` · `rememberCardImage(art, sample)` (ui/dex) · `CardShot` · `rememberCardSaver()` (ui/dogcard) · `cardFileName(templateId, cardId, at)` (dogcard) · `DaengsWideButton(label, onClick, modifier, enabled, busy, accent)` (ui/common)
- Produces:
  - `enum class PhotoMakeStage { Pick, Drawing, Reveal, Failed }` · `fun photoMakeStage(card: PhotoCard?, file: File?): PhotoMakeStage`
  - `@Composable fun PhotoCardBack(modifier: Modifier = Modifier, label: String? = null)` — 5:8 뒷면, 은은한 반짝임. **크기는 부르는 쪽 modifier 가 정한다**(비율은 안에서 `aspectRatio(PHOTO_RATIO, matchHeightConstraintsFirst = matchHeight)`)
  - 위 함수에 `matchHeight: Boolean = false` 인자
  - `@Composable fun PhotoRevealFlow(dex: DexCard, card: PhotoCard, file: File, onRevealed: (String) -> Unit, onOpenDex: () -> Unit, modifier: Modifier = Modifier)` — 뒷면에서 뒤집고(그림이 읽힌 뒤 시작), 끝나면 `onRevealed(card.id)` 를 한 번 부르고 결과 화면

- [ ] **Step 1: 단계 판정 테스트**

```kotlin
package com.daengs.app.ui.dex

import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PhotoMakeStageTest {
    private fun card(status: PhotoCardStatus) = PhotoCard("a", null, 9, "안녕", "CHUSEOK 안녕", status, null, null, 0L)

    @Test fun `아직 안 보냈으면 사진 고르기`() = assertEquals(PhotoMakeStage.Pick, photoMakeStage(null, null))
    @Test fun `그리는 중이면 그리는 중`() = assertEquals(PhotoMakeStage.Drawing, photoMakeStage(card(PhotoCardStatus.Generating), null))

    /** 완성이라도 그림을 받기 전에는 뒤집을 앞면이 없다. */
    @Test fun `완성이어도 그림이 없으면 그리는 중`() = assertEquals(PhotoMakeStage.Drawing, photoMakeStage(card(PhotoCardStatus.Ready), null))
    @Test fun `완성이고 그림이 있으면 뒤집기`() = assertEquals(PhotoMakeStage.Reveal, photoMakeStage(card(PhotoCardStatus.Ready), File("a.png")))
    @Test fun `실패면 실패`() = assertEquals(PhotoMakeStage.Failed, photoMakeStage(card(PhotoCardStatus.Failed), null))
}
```

- [ ] **Step 2: 실패를 본다.**

- [ ] **Step 3: `PhotoReveal.kt`**

```kotlin
package com.daengs.app.ui.dex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daengs.app.dogcard.cardFileName
import com.daengs.app.dogcard.photo.PhotoCard
import com.daengs.app.dogcard.photo.PhotoCardStatus
import com.daengs.app.ui.common.DaengsWideButton
import com.daengs.app.ui.dogcard.CardShot
import com.daengs.app.ui.dogcard.rememberCardSaver
import com.daengs.app.ui.theme.CardWhite
import com.daengs.app.ui.theme.CreamBg
import com.daengs.app.ui.theme.PinkSoft
import com.daengs.app.ui.theme.TextDark
import com.daengs.app.ui.theme.TextMuted
import java.io.File

// ---------------------------------------------------------------------------
// 포토 카드를 만든 뒤 보여 주기 (docs/photo-cards.md §8)
//
// 누끼 뽑기는 뽑자마자 그 자리에서 뒤집히고 결과가 뜬다. 포토 카드는 서버가 30~60초 그리는데,
// 예전에는 도감으로 돌아가 칸에 「만드는 중…」만 떴다 — 아무 일도 안 일어난 것처럼 보였다.
// 여기 부품을 만들기 화면(기다리면)과 도감(나가면 알림)이 같이 쓴다. 연출 값은 누끼
// `FlipToCard` 와 같게 둔다 — 틀(`CardTemplate`)에 묶여 있어 그 함수를 합치지는 않는다.
// ---------------------------------------------------------------------------

enum class PhotoMakeStage { Pick, Drawing, Reveal, Failed }

/** 만들기 화면 단계. **그림을 받기 전에는 뒤집지 않는다** — 앞면이 없다. */
fun photoMakeStage(card: PhotoCard?, file: File?): PhotoMakeStage = when {
    card == null -> PhotoMakeStage.Pick
    card.status == PhotoCardStatus.Failed -> PhotoMakeStage.Failed
    card.status == PhotoCardStatus.Ready && file != null -> PhotoMakeStage.Reveal
    else -> PhotoMakeStage.Drawing
}

/** 뒤집기 한 번의 길이. 누끼 `FlipToCard` 와 같다. */
private const val FLIP_MS = 760

/**
 * 뒷면. 서버가 그리는 동안 **살아 있어 보이게** 옅은 빛이 비스듬히 지나간다 — 멈춘 판이면
 * 고장 난 줄 안다(도감의 꾹 누르기 게이지와 같은 이유).
 */
@Composable
fun PhotoCardBack(modifier: Modifier = Modifier, label: String? = null, matchHeight: Boolean = false) {
    val sweep by rememberInfiniteTransition(label = "photoBack").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(
        modifier
            .aspectRatio(PHOTO_RATIO, matchHeightConstraintsFirst = matchHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(TextDark)
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, PinkSoft.copy(alpha = 0.28f), Color.Transparent),
                        start = Offset(x - size.width * 0.5f, 0f),
                        end = Offset(x + size.width * 0.5f, size.height),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", color = PinkSoft, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        label?.let {
            Text(
                it,
                color = CardWhite,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

/** 뒷면에서 앞면으로. **그림이 읽힌 뒤에** 돈다 — 안 읽혔는데 돌면 빈 앞면이 나온다. */
@Composable
private fun PhotoFlip(file: File, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val art = rememberCardImage(CardArt.Local(file))
    val turn = remember(file.path) { Animatable(0f) }
    LaunchedEffect(file.path, art != null) {
        if (art == null) return@LaunchedEffect
        turn.animateTo(180f, tween(FLIP_MS))
        onDone()
    }
    Box(
        modifier
            .aspectRatio(PHOTO_RATIO)
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 14f * density
            },
    ) {
        if (turn.value < 90f || art == null) {
            PhotoCardBack(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                Image(art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** 결과. 누끼 `ResultBody` 와 같은 배치 — 카드 · 이름 · 도감에서 보기 · 저장 · 공유. */
@Composable
private fun PhotoResult(dex: DexCard, card: PhotoCard, file: File, onOpenDex: () -> Unit) {
    val art = rememberCardImage(CardArt.Local(file))
    val saver = rememberCardSaver()
    Box(Modifier.fillMaxWidth(0.72f)) {
        art?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
    }
    Spacer(Modifier.height(12.dp))
    Text(dex.ko, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("${card.dogName}의 카드가 완성됐어요", color = TextMuted, fontSize = 13.sp)
    Spacer(Modifier.height(16.dp))
    DaengsWideButton("도감에서 보기", onOpenDex, Modifier.fillMaxWidth(), accent = true)
    // 사진첩·공유는 서버가 그린 PNG 그대로 — 틀이 없으니 `template = null` (도감 설명 시트와 같다).
    val shot = art?.let {
        CardShot(
            fileName = cardFileName(templateId = dex.id, cardId = card.id, at = card.createdAtMillis),
            art = it, template = null, face = null, name = card.dogName, code = "",
        )
    }
    Spacer(Modifier.height(8.dp))
    DaengsWideButton(
        when {
            saver.busy -> "저장하는 중…"
            saver.toGallery -> "갤러리에 저장"
            else -> "이미지로 저장"
        },
        { shot?.let(saver::save) },
        Modifier.fillMaxWidth(),
        enabled = shot != null && !saver.busy,
    )
    Spacer(Modifier.height(8.dp))
    DaengsWideButton("공유하기", { shot?.let(saver::share) }, Modifier.fillMaxWidth(), enabled = shot != null && !saver.busy)
    saver.note?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = TextMuted, fontSize = 12.sp)
    }
}

/**
 * 뒤집고 결과를 보여 준다. 뒤집기가 끝나는 순간 [onRevealed] 를 **한 번** 부른다 — 도감의
 * 「완성됐어요」 알림이 그때 사라진다.
 */
@Composable
fun PhotoRevealFlow(
    dex: DexCard,
    card: PhotoCard,
    file: File,
    onRevealed: (String) -> Unit,
    onOpenDex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(CreamBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!flipped) {
            Spacer(Modifier.height(40.dp))
            PhotoFlip(file, onDone = { flipped = true; onRevealed(card.id) }, modifier = Modifier.fillMaxWidth(0.72f))
        } else {
            PhotoResult(dex, card, file, onOpenDex)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDF4F0, heightDp = 420)
@Composable
private fun PhotoCardBackPreview() {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PhotoCardBack(Modifier.width(140.dp))
        PhotoCardBack(Modifier.width(140.dp), label = "만드는 중…")
    }
}
```

(와일드카드 import 가 이 저장소 결과 다르면 명시 import 로 바꾼다. `rememberCardImage` 는 `ui/dex/CardArt.kt`, `CardArt.Local` 도 같은 곳.)

- [ ] **Step 4: 통과·빌드** — `… :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.PhotoMakeStageTest' --tests 'com.daengs.app.DesignLockTest'`.

- [ ] **Step 5: 커밋** — 제목 `포토 카드가 완성돼도 뒤집히며 나오는 순간이 없던 것 — 뒷면·뒤집기·결과 부품을 둔다`.

---

### Task 4: 만들기 화면이 기다렸다 보여 준다

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/dex/PhotoCardMakeScreen.kt`
- Modify: `app/src/main/java/com/daengs/app/MainActivity.kt` (컴파일되게 인자만 — 진짜 배선은 Task 6)

**Interfaces:**
- Consumes: Task 3 전부. `photoFailureText(month, errorCode)` (dogcard/photo). `PhotoCardMakeScreen` 의 기존 인자.
- Produces: 새 시그니처

```kotlin
@Composable
fun PhotoCardMakeScreen(
    startMonth: Int?,
    dogs: List<PhotoDog>,
    busy: Boolean,
    error: String?,
    /** 방금 보낸 카드. null 이면 아직 안 보냈다 */
    watching: PhotoCard?,
    /** 그 카드의 받아 둔 그림 */
    watchingFile: File?,
    onSubmit: (month: Int, dog: PhotoDog, jpeg: ByteArray) -> Unit,
    /** 「다 되면 알려 주세요」 · 그리는 중 뒤로가기 — 요청은 서버에 있다, 취소가 아니다 */
    onWaitElsewhere: () -> Unit,
    onRevealed: (String) -> Unit,
    /** 실패 뒤 「다시 만들기」 — 실패 행을 지우고 사진 고르기로 */
    onRetry: (PhotoCard) -> Unit,
    onOpenDex: () -> Unit,
    onCancel: () -> Unit,
)
```

- [ ] **Step 1: 단계로 가른다** — 기존 상태(`month`·`dog`·`picked`…)와 `pick` 런처는 **`when` 위에 그대로** 둔다(실패 뒤 다시 만들 때 고른 사진이 남는다). 기존 `BackHandler { if (!busy) onCancel() }` 와 `PhotoCardMakeContent(...)` 호출을 아래로 바꾼다:

```kotlin
    when (photoMakeStage(watching, watchingFile)) {
        PhotoMakeStage.Pick -> {
            // **항상 등록해 둔다.** (기존 주석 유지)
            BackHandler { if (!busy) onCancel() }
            PhotoCardMakeContent(/* 기존 인자 그대로 */)
        }
        PhotoMakeStage.Drawing -> {
            val card = watching!!
            BackHandler { onWaitElsewhere() }
            PhotoDrawingBody(dogName = card.dogName, month = card.month, onWaitElsewhere = onWaitElsewhere)
        }
        PhotoMakeStage.Reveal -> {
            val card = watching!!
            val dex = photoCardFor(card.month)
            BackHandler { onOpenDex() }
            if (dex != null && watchingFile != null) {
                PhotoRevealFlow(dex, card, watchingFile, onRevealed = onRevealed, onOpenDex = onOpenDex)
            }
        }
        PhotoMakeStage.Failed -> {
            val card = watching!!
            BackHandler { onRetry(card); onCancel() }
            PhotoFailedBody(
                text = photoFailureText(card.month, card.errorCode),
                onRetry = { onRetry(card) },
                onCancel = { onRetry(card); onCancel() },
            )
        }
    }
```

- [ ] **Step 2: 두 몸통 Composable** (같은 파일, private)

```kotlin
/** 서버가 그리는 동안. 뒷면이 살아 있고, 나가도 된다는 걸 말해 준다. */
@Composable
private fun PhotoDrawingBody(dogName: String, month: Int, onWaitElsewhere: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CreamBg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        PhotoCardBack(Modifier.fillMaxWidth(0.62f))
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.62f), color = DaengPink, trackColor = PinkFaint)
        Spacer(Modifier.height(16.dp))
        Text("${dogName}의 ${month}월 카드를 그리는 중이에요", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("1분쯤 걸려요. 나가도 다 되면 도감에서 알려 드려요.", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        DaengsWideButton("다 되면 알려 주세요", onWaitElsewhere, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PhotoFailedBody(text: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CreamBg).systemBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = TextDark, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        DaengsWideButton("다시 만들기", onRetry, Modifier.fillMaxWidth(), accent = true)
        Spacer(Modifier.height(6.dp))
        DaengsTextAction("그만두기", onCancel, tint = TextMuted)
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun PhotoDrawingBodyPreview() = PhotoDrawingBody("안녕", 9) {}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PhotoFailedBodyPreview() =
    PhotoFailedBody("9월 카드를 만들지 못했어요 · 잠시 뒤 다시 만들어 주세요", {}, {})
```

`LinearProgressIndicator` 는 `androidx.compose.material3.LinearProgressIndicator` (이 저장소 M3 버전에 `trackColor` 인자가 없으면 빼고 보고서에 적는다).

- [ ] **Step 3: `MainActivity` 컴파일 맞추기** — `PhotoCardMakeScreen(` 호출에 `watching = null, watchingFile = null, onWaitElsewhere = done, onRevealed = {}, onRetry = {}, onOpenDex = done,` 를 더한다 (Task 6 에서 진짜로 잇는다).

- [ ] **Step 4: 빌드·테스트** — `… :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.*' --tests 'com.daengs.app.DesignLockTest'`.

- [ ] **Step 5: 커밋** — 제목 `포토 카드를 만들어도 결과 없이 도감으로 돌아가던 것 — 만들기 화면이 그리는 중을 보여 주고 뒤집는다`.

---

### Task 5: 도감 — 만드는 중 뒷면 · 완성 알림 · 알림에서 결과

**Files:**
- Modify: `app/src/main/java/com/daengs/app/ui/dex/CardDexScreen.kt`

**Interfaces:**
- Consumes: Task 3 `PhotoCardBack` · `PhotoRevealFlow`.
- Produces: `CardDexScreen` 새 인자 (전부 기본값): `revealCard: PhotoCard? = null` · `revealFile: File? = null` · `onRevealed: ((String) -> Unit)? = null`.

- [ ] **Step 1: 만드는 중 칸 표지** — `GridCard` 와 `CardViewer` 의 `PhotoBlank(locked = slot.locked, label = if (pending) … else null, modifier = …)` 호출에서, **`pending` 이면** 같은 modifier 사슬(`rubbable` 포함)을 그대로 넘겨 `PhotoCardBack(modifier = …, label = "만드는 중…", matchHeight = true)`(그리드) / `PhotoCardBack(modifier = …, label = "만드는 중이에요. 다 되면 알려 드려요")`(확대 뷰, `matchHeight = false`)로 바꾼다. `PhotoBlank` 는 `height(...)` 를 modifier 로 받고 있으니, 그리드는 그 modifier 에 이미 높이가 있어 `matchHeight = true` 가 맞다. 잠긴 칸은 `PhotoBlank` 그대로.

- [ ] **Step 2: 결과 오버레이** — 상태와 뒤로가기:

```kotlin
    // 알림에서 연 결과. **누른 순간의 카드를 붙잡아 둔다** — 뒤집기가 끝나면 `onRevealed` 로
    // `revealCard` 가 null 이 되는데, 그걸 그대로 보면 결과 화면이 뜨자마자 사라진다.
    var revealing by remember { mutableStateOf<Pair<PhotoCard, File>?>(null) }
```

`BackHandler` 의 `when` 맨 앞에 `revealing != null -> revealing = null`. `if (makingPhoto && makePhoto != null) { … return }` 바로 아래에:

```kotlin
    revealing?.let { (card, file) ->
        val dex = photoCardFor(card.month)
        if (dex != null) {
            PhotoRevealFlow(
                dex = dex,
                card = card,
                file = file,
                onRevealed = { onRevealed?.invoke(it) },
                onOpenDex = { deck = DexDeck.Photo; revealing = null },
            )
            return
        }
    }
```

(`deck` 이 이 자리보다 아래에 선언돼 있으면 선언을 위로 올린다 — 동작은 같다.)

- [ ] **Step 3: 완성 알림 한 줄** — private Composable:

```kotlin
/**
 * 「9월 카드가 완성됐어요 · 보기」. **저절로 사라지지 않는다** — 결과를 보면(`onRevealed`) 홀더가 지운다.
 * 화면을 저절로 바꾸지 않는 이유는 다른 카드를 보던 중에 튀기 때문이다 (docs/photo-cards.md §8 결정 9).
 */
@Composable
private fun PhotoReadyNotice(month: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextDark.copy(alpha = 0.92f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${month}월 카드가 완성됐어요", color = CardWhite, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text("보기", color = PinkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6E9E3)
@Composable
private fun PhotoReadyNoticePreview() = PhotoReadyNotice(9, {})
```

도감 본문 `Box` 안, 확대 뷰 `AnimatedVisibility` 다음·`removedNote` 앞에:

```kotlin
        if (revealCard != null && revealFile != null && opened == null) {
            PhotoReadyNotice(
                month = revealCard.month,
                onOpen = { revealing = revealCard to revealFile },
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 28.dp),
            )
        }
```

그리고 `removedNote` 의 `padding(bottom = 28.dp)` 를, 알림이 떠 있을 때 겹치지 않게 `padding(bottom = if (revealCard != null && revealFile != null && opened == null) 80.dp else 28.dp)` 로. (확대 뷰가 열려 있으면 알림을 숨긴다 — 설명 시트 버튼을 가린다.)

- [ ] **Step 4: 빌드·테스트** — `… :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.ui.dex.*' --tests 'com.daengs.app.DesignLockTest'`.

- [ ] **Step 5: 커밋** — 제목 `나가서 기다린 사람에게 도감이 포토 카드 완성을 알리지 않던 것`.

---

### Task 6: `MainActivity` 배선

**Files:**
- Modify: `app/src/main/java/com/daengs/app/MainActivity.kt`

**Interfaces:**
- Consumes: Task 2 `PrefsRevealLog` · `create(): String?` · `markRevealed` · `readyToReveal`; Task 4 새 `PhotoCardMakeScreen` 인자; Task 5 `CardDexScreen` 새 인자.

- [ ] **Step 1: 홀더에 기억 저장소** — `PhotoCardHolder(remote = …, files = …, accessToken = freshToken, reveals = PrefsRevealLog(context))`.

- [ ] **Step 2: 만들기 화면 배선** — `makePhoto = { startMonth, done -> … }` 안:

```kotlin
                        makePhoto = { startMonth, done ->
                            LaunchedEffect(Unit) { photos.clearCreateError() }
                            // 방금 보낸 카드. 이 오버레이가 떠 있는 동안만 기억한다 — 닫으면 처음부터.
                            var watchId by remember { mutableStateOf<String?>(null) }
                            val watching = watchId?.let { id -> photos.cards.firstOrNull { it.id == id } }
                            PhotoCardMakeScreen(
                                startMonth = startMonth,
                                dogs = /* 기존 그대로 */,
                                busy = photos.creating,
                                error = photos.createError,
                                watching = watching,
                                watchingFile = watching?.let { photos.images[it.id] },
                                onSubmit = { month, dog, jpeg ->
                                    scope.launch { photos.create(month, dog.name, dog.id, jpeg)?.let { watchId = it } }
                                },
                                onWaitElsewhere = done,
                                onRevealed = { photos.markRevealed(it) },
                                // 실패 행은 서버에서 지운다 — 남으면 도감 머리말에 같은 실패가 또 뜬다.
                                onRetry = { failed ->
                                    scope.launch { photos.remove(failed.id) }
                                    watchId = null
                                },
                                onOpenDex = done,
                                onCancel = done,
                            )
                        },
```

- [ ] **Step 3: 도감 알림 배선** — `CardDexScreen(` 호출에:

```kotlin
                        revealCard = photos.readyToReveal,
                        revealFile = photos.readyToReveal?.let { photos.images[it.id] },
                        onRevealed = { photos.markRevealed(it) },
```

- [ ] **Step 4: 빌드 + 전체 단위 테스트** — `… :app:assembleDebug` 후 `… :app:testDebugUnitTest` 한 번, JUnit XML 합계를 읽는다(알려진 1건 외 실패 없어야 함).

- [ ] **Step 5: 커밋** — 제목 `만들기 화면과 도감이 방금 만든 포토 카드를 따라가지 못하던 것 — 보낸 카드와 결과 안 본 카드를 잇는다`.

---

### Task 7: 실기기 확인 (컨트롤러가 직접)

- 설치 후 `Success` 와 설치 시각을 직접 본다. 스크린샷 전 앞 화면이 `com.daengs.app` 인지 본다.
- **카드 생성은 비용·하루 한도 1장을 쓴다 — 시작 전에 사용자에게 묻고, 사진은 사용자가 고른다.**
- 한 장으로 볼 수 있는 흐름은 하나다. 사용자와 정한다: ① 만들기 화면에서 기다려 뒤집기 → 결과 → 저장, 또는 ② 「다 되면 알려 주세요」 로 나가 도감 알림 → 보기 → 결과. 둘 다 칸이 나갔다 오지 않아도 완성으로 바뀌는지(Task 1) 본다.
- **지우기 확정 버튼은 누르지 않는다** (메모리 `confirm-destructive-on-device`).
- 끝나면 PR #414 본문 「확인한 것」·「남은 것」 갱신.

## Self-review 메모

- §8.1 원인 → Task 1 (가설을 RED 테스트로 확정) · §8.2 → Task 3·4·6 · §8.3 → Task 2·5·6 · §8.4 부품 표 → 파일 지도 · §8.5 테스트 → Task 1·2·3.
- §8.4 표의 `PhotoRevealStore` 이름은 `PhotoRevealLog` 로, "계정별 키" 는 "로그아웃하면 비움" 으로 구현한다(단일 키) — Task 2 에서 spec 표 두 줄을 같이 고친다.
- 이름 일관성: `readyToReveal` · `markRevealed` · `unrevealed` · `PhotoRevealLog` · `photoMakeStage` · `PhotoCardBack` · `PhotoRevealFlow` · `watching`/`watchingFile` · `revealCard`/`revealFile`/`onRevealed`.
