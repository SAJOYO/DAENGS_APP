package com.daengs.app.screening

/**
 * 사진 한 장을 진단한다. **기록으로 남기되, 못 남겨도 진단은 한다.**
 *
 * 길이 둘이다:
 * - **새 계약** `/app/screening/…` — 기록이 남는다. 토큰이 있어야 한다
 * - **옛 경로** `/screen/v1/screen` — 판정만. 저쪽이 그대로 살려 뒀다
 *
 * ⚠️ **옛 경로를 지우면 안 된다.** 로그인 안 한 사람도 진단은 쓸 수 있고, 서버
 *    저장소가 아직 안 켜진 동안에는 새 계약이 **503** 이다(D-052). 그때 진단까지
 *    같이 죽으면 되던 기능이 안 되는 것이라, 기록을 포기하고 판정을 살린다.
 *
 * 이 갈림을 화면에서 떼어 놓았다 — 어느 길로 갔는지가 화면에 안 보여서, 잘못 갈리면
 * "왜 기록이 안 남지" 로만 남는다.
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
        val token = accessToken()
        if (token != null && ScreeningRecordApi.configured) {
            recorded(token, petId, jpeg, box)?.let { return it }
        }
        // 여기까지 왔으면 기록은 못 남긴다. **판정은 살린다.**
        return ScreeningApi.screen(jpeg, box).fold(
            onSuccess = { Outcome.Screened(it, recordId = null) },
            onFailure = { Outcome.Failed(it.message ?: "진단 서버에 닿지 못했어요.") },
        )
    }

    /**
     * 새 계약으로 한 바퀴. **못 하면 null 을 돌려주고 부르는 쪽이 옛 경로로 간다.**
     *
     * ⚠️ 판정까지 성공했는데 결과가 비어 있으면(이론상 없지만) 옛 경로로 다시 보내지
     *    않는다 — 같은 사진을 두 번 판정하면 서로 다른 답이 나올 수 있고, 사용자는
     *    두 번 기다린다. 그때는 기록만 남기고 실패로 알린다.
     */
    private suspend fun recorded(
        token: String,
        petId: String?,
        jpeg: ByteArray,
        box: FloatArray?,
    ): Outcome? {
        val ticket = ScreeningRecordApi.start(token, petId, box).getOrNull() ?: return null
        if (ScreeningRecordApi.upload(ticket, jpeg).isFailure) return null
        return ScreeningRecordApi.confirm(token, ticket.recordId).fold(
            onSuccess = { record ->
                record.report
                    ?.let { Outcome.Screened(it, recordId = record.recordId) }
                    ?: Outcome.Failed("판정 결과를 받지 못했어요.")
            },
            // confirm 이 실패했다 = 사진은 올라갔는데 판정이 안 됐다. 저쪽이 기록을
            // FAILED 로 남겨 두므로 사진은 나중에 볼 수 있다. 여기서 옛 경로로
            // 다시 보내면 같은 사진을 두 번 판정하게 된다.
            onFailure = { Outcome.Failed(it.message ?: "사진을 판정하지 못했어요.") },
        )
    }

    sealed interface Outcome {
        /** 판정이 왔다. [recordId] 가 null 이면 **기록은 안 남았다** (옛 경로). */
        data class Screened(val report: ScreeningReport, val recordId: String?) : Outcome

        data class Failed(val message: String) : Outcome
    }
}
