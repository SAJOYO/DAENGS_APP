package com.daengs.app.ui.pet

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.pet.Pet
import com.daengs.app.ui.my.PRIVACY_POLICY_LABEL
import com.daengs.app.ui.my.PRIVACY_POLICY_URL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 첫 등록에는 취소가 없어서, 강아지를 등록하기 전에는 My 화면의 방침 링크에 못 간다.
 * 그래서 첫 등록 화면에는 링크가 있어야 하고, 수정 화면에는 없어야 한다(My 화면이
 * 바로 뒤에 있다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PetFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `initial registration shows the privacy policy link and opens the deployed page`() {
        compose.setContent {
            PetFormScreen(onSubmit = { _, _ -> }, onCancel = null, busy = false, error = null, initial = null)
        }

        compose.onNodeWithText(PRIVACY_POLICY_LABEL)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(PRIVACY_POLICY_URL, started?.dataString)
    }

    @Test
    fun `editing an existing pet does not add the link`() {
        val existing = Pet(
            id = "p1",
            name = "네옹",
            breed = MIX_BREED,
            sex = null,
            neutered = null,
            weightKg = null,
            birthDate = null,
            birthDateKind = null,
            isPrimary = true,
        )
        compose.setContent {
            PetFormScreen(onSubmit = { _, _ -> }, onCancel = {}, busy = false, error = null, initial = existing)
        }

        compose.onNodeWithText(PRIVACY_POLICY_LABEL).assertDoesNotExist()
    }
}
