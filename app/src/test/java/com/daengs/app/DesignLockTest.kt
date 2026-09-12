package com.daengs.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * **잠긴 디자인을 지킨다** — `docs/design-locks.md`.
 *
 * ⛔ 이 테스트가 깨지면 **테스트를 고치지 말고 변경을 되돌린다.** 여기 적힌 것은
 *    사용자가 실기기에서 보고 정한 화면이다. 바꾸려면 사람이 문서부터 고친다.
 *
 * 화면을 그려서 보는 테스트가 아니다 — 네이버 지도는 Robolectric 에서 안 뜬다.
 * 대신 **소스를 읽어** 지도 호출에 얼굴이 실려 있는지 본다. 거칠지만, 이 결정이
 * 뒤집힌 방식이 늘 같았다: 새 화면이 `MapHost(...)` 를 부르면서 얼굴 인자를 빼먹는다.
 * 기본값이 `null` 이라 컴파일도 되고 화면도 멀쩡해 보인다 — 파란 점만 빼고.
 */
class DesignLockTest {

    private val root: File = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.isDirectory }

    private fun source(path: String): String =
        File(root, "com/daengs/app/$path").readText()

    /** 파일 안의 `MapHost(` 호출마다 괄호가 닫힐 때까지를 잘라 낸다. */
    private fun mapHostCalls(text: String): List<String> {
        val calls = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = text.indexOf("MapHost(", from).takeIf { it >= 0 } ?: break
            // 정의(`fun MapHost(`)는 호출이 아니다.
            if (text.substring(maxOf(0, start - 4), start) == "fun ") { from = start + 8; continue }
            var depth = 0
            var i = start + "MapHost".length
            while (i < text.length) {
                when (text[i]) { '(' -> depth++; ')' -> { depth--; if (depth == 0) break } }
                i++
            }
            calls += text.substring(start, i + 1)
            from = i + 1
        }
        return calls
    }

    // -- 1. 지도의 「내 위치」는 사용자 프로필이다 --------------------------------

    private val liveLocationMaps = listOf(
        "ui/places/ConnectedPlaceSearchScreen.kt",   // 내 주변 — 검색
        "ui/places/PlaceBookmarksScreen.kt",         // 내 주변 — 찜
        "ui/walk/WalkScreen.kt",                     // 산책
    )

    @Test
    fun `내 위치를 그리는 지도는 전부 프로필 얼굴을 넘긴다`() {
        liveLocationMaps.forEach { path ->
            val calls = mapHostCalls(source(path))
            assertTrue("$path 에서 MapHost 호출을 못 찾았다 — 파일이 옮겨졌으면 이 목록도 옮긴다", calls.isNotEmpty())
            calls.forEach { call ->
                if ("avatarRes" !in call || "avatarPhoto" !in call) fail(
                    "⛔ 잠긴 디자인 위반 ($path): 지도의 내 위치는 사용자 프로필이어야 한다. " +
                        "MapHost 에 avatarRes 와 avatarPhoto 를 둘 다 넘긴다 — 안 넘기면 SDK 파란 점이 된다. " +
                        "docs/design-locks.md 1절. 테스트를 고치지 말고 변경을 되돌릴 것.",
                )
            }
        }
    }

    /**
     * **얼굴을 모를 때도 파란 점이 아니다.** 강아지 정보가 없는 때(불러오는 중 · 로그인 전 ·
     * 강아지 없음 · 통신 실패)에 견종 얼굴만 넘기면 두 인자가 다 null 이 되어 SDK 점으로
     * 떨어진다. 그래서 얼굴 리소스는 늘 발바닥 폴백을 거친다.
     */
    @Test
    fun `얼굴을 모르면 발바닥으로 떨어진다`() {
        val fallbacks = mapOf(
            "ui/places/ConnectedPlaceSearchScreen.kt" to "locationFaceRes(",
            "ui/places/PlaceBookmarksScreen.kt" to "locationFaceRes(",
            "ui/walk/WalkScreen.kt" to "avatarRes = faceRes",
        )
        fallbacks.forEach { (path, needle) ->
            mapHostCalls(source(path)).forEach { call ->
                if (needle !in call) fail(
                    "⛔ 잠긴 디자인 위반 ($path): 얼굴을 모를 때 파란 점으로 떨어진다. " +
                        "avatarRes 는 발바닥 폴백(locationFaceRes / walkFacePortraitRes)을 거친다. " +
                        "docs/design-locks.md 1절. 테스트를 고치지 말고 변경을 되돌릴 것.",
                )
            }
        }
        assertTrue(
            "WalkScreen 의 faceRes 가 발바닥 폴백을 안 거친다",
            "walkFacePortraitRes(" in source("ui/walk/WalkScreen.kt"),
        )
    }

    @Test
    fun `발바닥 폴백은 null 을 돌려주지 않는다`() {
        assertEquals(R.drawable.ic_location_paw, com.daengs.app.map.provider.naver.locationFaceRes(null))
        assertEquals(123, com.daengs.app.map.provider.naver.locationFaceRes(123))
    }

    // -- 3. 점령 · 산책의 주요 버튼은 분홍이다 --------------------------------------

    /**
     * 점령 쪽 주요 버튼만 **진한 갈색으로 채워져** 있었다(「점령 지도 보기」 · 규칙 창의
     * 「직접 해보기」 · 「촬영하고 산책 계속」). 앱의 다른 주요 버튼(「산책 시작」)은 분홍이라
     * 같은 앱 안에서 다른 앱처럼 보였다. 채움 버튼의 바탕을 진한 갈색으로 되돌리지 않는다.
     */
    @Test
    fun `점령 산책의 채움 버튼은 진한 갈색이 아니다`() {
        val dark = Regex("buttonColors\\(\\s*containerColor\\s*=\\s*(TextDark|DaengsColors\\.TextPrimary)")
        listOf("ui/game", "ui/walk").forEach { dir ->
            File(root, "com/daengs/app/$dir").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                dark.find(file.readText())?.let {
                    fail(
                        "⛔ 잠긴 디자인 위반 (${file.name}): 채움 버튼 바탕이 진한 갈색이다. 주요 버튼은 " +
                            "DaengPink 다 (「산책 시작」과 같게). docs/design-locks.md 3절. " +
                            "테스트를 고치지 말고 변경을 되돌릴 것.",
                    )
                }
            }
        }
    }

    @Test
    fun `얼굴 인자에 null 을 박아 넣지 않는다`() {
        val nullFace = Regex("avatar(Res|Photo)\\s*=\\s*null")
        liveLocationMaps.forEach { path ->
            mapHostCalls(source(path)).forEach { call ->
                assertTrue(
                    "⛔ 잠긴 디자인 위반 ($path): avatar 인자에 null 을 넣었다. docs/design-locks.md 1절.",
                    !nullFace.containsMatchIn(call),
                )
            }
        }
    }
}
