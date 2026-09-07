package com.daengs.app.ui.chat

import android.content.Intent
import com.daengs.app.assistant.PlaceSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [placeMapIntent] 가 서버 URL 이 아니라 **좌표로 앱이 직접 지은** `geo:` intent 를
 * 내는가. `journey/MapHandoff.kt` 의 신뢰 스킴 검증은 여기 대상이 아니다 — 그건
 * 서버가 준 네이버 경로 링크 전용이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaceSuggestionCardTest {

    private fun candidate(
        title: String = "멍멍이 미용실",
        lat: Double? = 37.5563,
        lon: Double? = 126.9236,
    ) = PlaceSuggestions.Candidate(
        placeId = PlaceSuggestions.PlaceId("kto", "P-1"),
        title = title,
        summary = "",
        kindLabel = "",
        lat = lat,
        lon = lon,
        distanceMeters = null,
        address = "",
        facts = emptyList(),
        notices = emptyList(),
        whyMatched = emptyList(),
    )

    @Test
    fun `좌표로 geo intent를 짓는다`() {
        val intent = placeMapIntent(candidate())!!

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertTrue(intent.dataString.orEmpty().startsWith("geo:37.5563,126.9236"))
    }

    @Test
    fun `좌표가 없으면 null이다`() {
        assertNull(placeMapIntent(candidate(lat = null, lon = null)))
        assertNull(placeMapIntent(candidate(lat = 37.5563, lon = null)))
    }
}
