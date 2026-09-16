package com.daengs.app.assistant

import com.daengs.app.chat.ChatPersistence
import com.daengs.app.chat.ChatApiError
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.FacilityConversationRepository
import com.daengs.app.place.FacilityAssistantTurn
import com.daengs.app.place.FacilityException
import com.daengs.app.place.FacilityResponsePolicy
import com.daengs.app.place.bookmarks.BookmarkTurn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.json.JSONArray
import java.util.UUID

typealias AssistantQuery =
    suspend (String, String, GeoPoint?, String?, ChatPersistence?, ScreeningFollowUp?) -> Result<AssistantResponse>

data class FacilityAssistantReference(
    val sessionId: String, val revision: Int, val requestId: String, val answer: String,
) {
    fun recovery() = buildJsonObject {
        put("session_id", sessionId); put("client_request_id", requestId)
    }

    companion object {
        fun from(results: JSONArray?): FacilityAssistantReference? {
            for (i in 0 until (results?.length() ?: 0)) {
                val result = results!!.getJSONObject(i)
                if (result.optString("capability") != "place") continue
                val data = result.optJSONObject("data") ?: continue
                if (data.optString("contract_version") != "place-facility-v2") continue
                val ref = data.getJSONObject("facility")
                return FacilityAssistantReference(
                    UUID.fromString(ref.getString("session_id")).toString(),
                    ref.getInt("revision").also { require(it > 0) },
                    UUID.fromString(ref.getString("client_request_id")).toString(),
                    data.getString("answer"),
                )
            }
            return null
        }
    }
}

/** Only a newly submitted turn may change the map. Parsing saved chat history never does. */
class FacilityAssistant(
    private val repository: FacilityConversationRepository,
    private val captureBookmarks: () -> BookmarkTurn? = { null },
    private val onSearchApplied: () -> Unit = {},
    private val send: suspend (String, String, GeoPoint?, String?, ChatPersistence?, JsonObject) -> Result<AssistantResponse> =
        { token, text, where, dog, persistence, facility -> AssistantApi.query(token, text, where, dog, persistence, facility) },
) {
    private val pending = linkedMapOf<String, FacilityAssistantTurn>()

    @Suppress("UNUSED_PARAMETER") // Authentication is refreshed inside the captured login lifetime.
    suspend fun query(token: String, text: String, where: GeoPoint?, dogId: String?, persistence: ChatPersistence?): Result<AssistantResponse> {
        val key = persistence?.let { "${it.sessionId}:${it.clientMessageId}" } ?: "query:$text"
        return try {
            val turn = pending[key]?.takeIf(repository::isAssistantAccountCurrent)
                ?: repository.captureAssistantTurn(persistence?.clientMessageId ?: UUID.randomUUID().toString(), captureBookmarks())
                    .also { pending[key] = it }
            while (pending.size > 20) pending.remove(pending.keys.first())?.bookmarks?.cancel()
            val response = send(repository.assistantAccessToken(turn), text, where, dogId, persistence, turn.context).getOrThrow()
            val reference = response.facility
            val completion = if (reference != null) {
                try { repository.acceptAssistantTurn(turn, reference) }
                catch (expired: FacilityException) {
                    if (expired.status !in setOf(409, 410) || turn.before == null) throw expired
                    repository.recoverAssistantView(turn)
                }.let { completion -> completion ?: repository.state.value.result?.let {
                    FacilityResponsePolicy.answer(it.copy(answer = it.answer ?: reference.answer))
                } }.also {
                    if (!repository.state.value.result!!.preservesDisplay) onSearchApplied()
                }
            } else if (response.facilityError in setOf("facility_expired", "facility_conflict") && turn.before != null) {
                repository.recoverAssistantView(turn).also { onSearchApplied() }
            } else if (response.clarify?.missing?.any { it.startsWith("location.") } == true) {
                FacilityResponsePolicy.LOCATION
            } else if (response.facilityError != null) {
                FacilityResponsePolicy.text(response.facilityErrorMessage ?: response.message)
            } else null
            pending.remove(key)
            Result.success(if (completion == null) response else response.copy(
                message = if (response.resultCount <= 1) completion else
                    response.message.replace("[장소]\n${reference?.answer ?: response.facilityErrorMessage}", "[장소]\n$completion"),
                clarify = response.clarify?.let { if (it.missing.any { key -> key.startsWith("location.") })
                    it.copy(question = FacilityResponsePolicy.LOCATION) else it },
            ))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            if (error is FacilityException && error.status == 401) {
                pending.remove(key)?.bookmarks?.cancel()
                return Result.failure(ChatApiError(401, "FACILITY_LOGIN_REQUIRED", "다시 로그인해 주세요."))
            }
            val invalidView = pending[key]?.let { !repository.isAssistantTurnCurrent(it) } == true
            if (invalidView || error is IllegalArgumentException || (error is IllegalStateException && error !is FacilityException && error !is ChatApiError)) {
                pending.remove(key)?.bookmarks?.cancel()
                Result.failure(ChatApiError(409, "FACILITY_VIEW_CHANGED",
                    FacilityResponsePolicy.VIEW_CHANGED,
                    mapOf("retry_with_fresh_client_message_id" to "true")))
            } else if (error is FacilityException) Result.failure(ChatApiError(error.status, "FACILITY_REQUEST_FAILED",
                if (error.status == 401) "다시 로그인해 주세요." else FacilityResponsePolicy.UNKNOWN))
            else Result.failure(error)
        }
    }

}
