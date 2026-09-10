package com.daengs.app.territory

import java.time.Instant
import org.json.JSONObject

internal fun TerritoryOperation.renewalMarkIdentity(): String = JSONObject(body).getString("mark_identity")

/** The request string is frozen in the outbox before the first PUT. */
internal suspend fun deliverTerritoryRenewal(
    operation: TerritoryOperation, api: TerritoryActionClient, token: String,
): String {
    val envelope = JSONObject(operation.body)
    val id = canonicalUuid(envelope.getString("renewal_id"))
    val claim = canonicalUuid(envelope.getString("claim_id"))
    val request = envelope.getString("request")
    val response = api.request(token, "PUT", "/claims/$claim/renewals/$id", request)
    val result = JSONObject(response)
    require(result.getString("renewal_id") == id)
    val version = result.get("site_version")
    require((version is Long || version is Int) &&
        (version as Number).toLong() > JSONObject(request).getLong("expected_site_version"))
    Instant.parse(result.getString("expires_at"))
    return response
}

internal fun TerritoryOperation.renewalGuidance(): String = when (state) {
    "CONFIRMED" -> "유지 시간을 연장했어요 · 연장 보상 0점"
    "REJECTED" -> when (failure) {
        "season_ended" -> "시즌이 바뀌었어요 · 점유 상태를 다시 확인해 주세요"
        "site_changed", "not_current_owner" -> "점유 상태가 바뀌었어요 · 다시 확인해 주세요"
        "photo_required" -> "인증된 영역은 새 사진으로 연장해 주세요"
        "stale_location", "OUT_OF_RANGE", "UNTRUSTED_LOCATION", "site_not_nearby" ->
            "위치가 확인되지 않았어요 · 현장에서 다시 연장해 주세요"
        "NOT_RECORDING" -> "산책을 재개한 뒤 다시 연장해 주세요"
        else -> "연장하지 못했어요 · 점유 상태를 다시 확인해 주세요"
    }
    else -> "유지 연장 결과를 확인하고 있어요 · 연장 보상 0점"
}
