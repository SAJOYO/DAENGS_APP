package com.daengs.app.ui.my

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 영수증 학습 이용 동의 줄은 **아직 내보내지 않는다** (#258).
 *
 * 저쪽 `OCR_CONSENT_VERSION` 이 `"unset"` 이라, 지금 동의를 받아도 실제로 존재하는
 * 개인정보처리방침 판을 가리키지 못한다 — 근거가 안 서는 동의를 받는 것이 안 받는
 * 것보다 나쁘다. 값과 콜백은 `MainActivity` 부터 여기까지 이어져 있고, 판 번호가
 * 정해지면 `MyScreen` 의 `OCR_CONSENT_VISIBLE` 한 줄로 열린다.
 *
 * **이 테스트는 그때 같이 지운다.** 그전까지는 이 줄이 실수로 화면에 나가는 것을 막는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrConsentHiddenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `동의 줄은 값과 콜백을 다 줘도 화면에 안 나온다`() {
        compose.setContent {
            MyScreen(
                breed = null,
                nickname = "윤주",
                pets = emptyList(),
                // 켜진 값과 콜백을 다 줘도 안 보여야 한다 — 가리는 것이 화면 쪽이다.
                ocrConsent = true,
                onOcrConsentChange = {},
                canAddMore = true,
                onAddPet = {},
                onEditPet = {},
                onPickPrimary = {},
                onDeletePet = {},
                deleteBusy = false,
                deleteError = null,
                onDismissDelete = {},
                signedIn = true,
                onSignIn = {},
                onSignOut = {},
                onWithdraw = {},
                withdrawBusy = false,
                withdrawError = null,
                onDismissWithdraw = {},
            )
        }

        compose.onAllNodesWithText("영수증 학습 이용 동의").assertCountEquals(0)
        // 개인정보처리방침은 같은 구역에 있고, 그건 보여야 한다 — 화면 자체가 안 그려진
        // 것을 통과로 착각하지 않으려는 대조군이다.
        compose.onAllNodesWithText("개인정보처리방침").assertCountEquals(1)
    }
}
