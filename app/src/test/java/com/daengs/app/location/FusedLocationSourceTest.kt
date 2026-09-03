package com.daengs.app.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusedLocationSourceTest {

    @Test
    fun `플랫폼 mock 표식은 실행 환경과 관계없이 우선한다`() {
        assertTrue(
            isMockEvidence(
                platformReportedMock = true,
                fingerprint = "google/shiba/shiba:16/release-keys",
                model = "Pixel 8",
                manufacturer = "Google",
                device = "shiba",
                product = "shiba",
            ),
        )
    }

    @Test
    fun `플랫폼 표식이 빠진 Android Studio AVD도 mock이다`() {
        assertTrue(
            isMockEvidence(
                platformReportedMock = false,
                fingerprint = "google/sdk_gphone16k_x86_64/emu64xa16k:17/dev-keys",
                model = "sdk_gphone16k_x86_64",
                manufacturer = "Google",
                device = "emu64xa16k",
                product = "sdk_gphone16k_x86_64",
            ),
        )
    }

    @Test
    fun `실제 Google Pixel은 제조사만으로 mock이 되지 않는다`() {
        assertFalse(
            isMockEvidence(
                platformReportedMock = false,
                fingerprint = "google/shiba/shiba:16/release-keys",
                model = "Pixel 8",
                manufacturer = "Google",
                device = "shiba",
                product = "shiba",
            ),
        )
    }
}
