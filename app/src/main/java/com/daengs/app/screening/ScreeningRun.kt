package com.daengs.app.screening

/**
 * 사진 한 장을 진단한다. **길은 하나다** — 새 계약 `/app/screening/…`.
 *
 * `기록 열기 → 사진 PUT → confirm`. 판정은 마지막 걸음이 준다.
 *
 * ## 옛 경로를 왜 뗐나 (#134)
 *
 * 예전에는 여기서 `/screen/v1/screen` 으로 물러섰다. 토큰이 없거나 서버 저장소가
 * 아직 안 켜졌을 때(503) **기록을 포기하고 판정만 살리려던** 장치였다. 셋이 다
 * 끝나서 뗐다:
 *
 * - 서버 `.env` 에 저장소 세 줄이 들어가 새 계약이 503 을 안 낸다 (D-052)
 * - 출시 앱은 로그인이 필수다 — 둘러보기는 디버그 빌드에만 있다
 *   (`LandingScreen.kt`). 그래서 "토큰이 없는 사용자" 가 출시 앱에 없다
 * - 실기기에서 티켓 → PUT → confirm → 기록까지 왕복을 확인했다
 *
 * 뗀 이유는 저쪽에 있다. `/screen/…` 에 인증과 rate limit 을 거는 일이 **이 fallback
 * 때문에** 멈춰 있었다 — 지금 저쪽이 인증을 걸면 여기가 조용히 깨진다.
 *
 * ⚠️ **이제 실패하면 실패라고 말한다.** 예전에는 새 계약이 어디서 넘어져도 옛 경로가
 *    받아 줘서, 화면에는 판정이 나오고 **기록만 조용히 안 남았다.** 그게 "왜 기록이
 *    안 남지" 로만 보이던 그 증상이다.
 */
class ScreeningRun(
    private val accessToken: suspend () -> String?,
) {

    /**
     * @param petId 어느 아이의 피부인가. 없어도 된다
     * @param jpeg 서버로 보낼 원본 바이트
     * @param box 가이드 프레임. **정규화 [x, y, w, h]** — 저쪽이 학습과 같은 함수로 자른다
     */
    suspend fun run(petId: String?, jpeg: ByteArray, box: FloatArray?): Outcome {
        if (!ScreeningRecordApi.configured) return Outcome.Failed(NOT_CONFIGURED)
        val token = accessToken() ?: return Outcome.Failed(NEEDS_LOGIN)

        val ticket = ScreeningRecordApi.start(token, petId, box)
            .getOrElse { return Outcome.Failed(it.message ?: "사진을 올릴 자리를 못 받았어요.") }
        ScreeningRecordApi.upload(ticket, jpeg)
            .onFailure { return Outcome.Failed(it.message ?: "사진을 올리지 못했어요.") }

        return ScreeningRecordApi.confirm(token, ticket.recordId).fold(
            onSuccess = { record ->
                record.report
                    ?.let { Outcome.Screened(it, recordId = record.recordId) }
                    // 이론상 없다. 저쪽이 DONE 을 주면서 결과를 비우면 여기다.
                    ?: Outcome.Failed("판정 결과를 받지 못했어요.")
            },
            // 사진은 올라갔는데 판정이 안 됐다. 저쪽이 기록을 FAILED 로 남겨 두므로
            // **사진은 나중에 볼 수 있다** — 다시 찍을지는 사용자가 정한다.
            onFailure = { Outcome.Failed(it.message ?: "사진을 판정하지 못했어요.") },
        )
    }

    sealed interface Outcome {
        /** 판정이 왔다. [recordId] 로 변화 기록에 남았다. */
        data class Screened(val report: ScreeningReport, val recordId: String) : Outcome

        data class Failed(val message: String) : Outcome
    }

    companion object {
        /**
         * 로그인이 없을 때. **출시 앱에서는 안 보인다** — 로그인해야 들어오는 화면이다.
         * 디버그 둘러보기에서만 닿는 문구다.
         */
        internal const val NEEDS_LOGIN = "로그인하면 피부를 살펴볼 수 있어요."

        internal const val NOT_CONFIGURED =
            "진단 서버가 아직 없어요.\nlocal.properties 의 daengs.apiBaseUrl 을 채우면 열려요."
    }
}
