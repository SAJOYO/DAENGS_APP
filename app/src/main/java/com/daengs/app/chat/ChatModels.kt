package com.daengs.app.chat

import androidx.compose.runtime.Immutable
import com.daengs.app.assistant.AssistantResponse
import com.daengs.app.auth.AuthApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * `/app/chats` 응답. 계약은 저쪽 `SAJOYO/DAENGS_dev` PR #131 의 `schemas/chat.py` 다.
 *
 * **서버가 진짜다.** 최근 대화가 몇 개까지인지, 어느 순서인지, 초안이 언제 최근 대화가
 * 되는지 전부 저쪽 `services/chat.py` 가 정하고, 여기는 그 답을 **받은 순서 그대로**
 * 든다. 기기에 저장하지 않고 다시 정렬하지도 않는다 — 강아지 목록(`PetHolder`)과
 * 같은 판단이다. 규칙을 앱에서 다시 계산하면 두 벌이 되고 언젠가 갈라진다.
 *
 * 시각은 [AuthApi] 의 `toEpochMs` 로 읽는다. 저쪽이 `+00:00` 으로 내보내고 안드로이드
 * 8 의 `Instant.parse` 가 그걸 못 읽는 문제를 거기서 이미 풀어 뒀다 — 파서를 하나 더
 * 만들면 같은 버그를 두 군데서 고치게 된다.
 */

/** 저쪽 `AgentCategory` — `training` · `life` · `walk` · `place`. 값은 문자열 그대로 든다. */
object ChatCapability {
    const val TRAINING = "training"
    const val LIFE = "life"
    const val WALK = "walk"
    const val PLACE = "place"

    /**
     * 화면에 붙일 라벨. **모르는 값은 null** 이다 — 저쪽이 능력을 하나 더 만드는 날
     * 앱이 엉뚱한 한글을 붙이면 안 되고, 크래시가 나서도 안 된다.
     *
     * `walk` 는 **산책**이다. 저쪽 `life` 비서가 "사료·산책·계절관리" 를 다룬다고 적혀
     * 있어서 `walk` 를 생활로 읽기 쉬운데, 산책 능력은 날씨·대기질로 나갈지를 판정하는
     * 별개 능력이다 (`WalkVerdict`).
     *
     * 피부·보행 진단은 여기 없다. 그건 Chat 능력이 아니라 `HANDOFF` 로 넘어가는 별도
     * 흐름(`ScreeningApi` · `GaitApi`)이라, 저쪽 `AgentCategory` 에도 없다.
     *
     * `place` 는 **장소**다. 답변 본문이 이미 `[장소]` 라는 머리말로 나가고 있어서,
     * 배지를 `갈 곳` 이나 `주변` 으로 쓰면 같은 것을 두 이름으로 부르게 된다.
     */
    fun label(raw: String): String? = when (raw) {
        TRAINING -> "훈련"
        LIFE -> "제도"
        WALK -> "산책"
        PLACE -> "장소"
        else -> null
    }
}

/**
 * 대화 하나.
 *
 * @property lastMessageAtMs **null 이면 초안**이다 — 아직 첫 답변이 안 온 대화. 저쪽은
 *   초안을 최근 대화 다섯에 안 넣고, 목록에도 안 준다. 앱도 초안을 최근 대화로
 *   그리지 않는다 ([ChatSessionList.recent]).
 */
@Immutable
data class ChatSession(
    val id: String,
    val petId: String,
    val title: String,
    /** 이 대화에서 돈 능력의 합집합. 값은 저쪽 문자열 그대로 ([ChatCapability.label] 로 붙인다). */
    val agentCategories: List<String>,
    val createdAtMs: Long,
    val lastMessageAtMs: Long?,
) {
    val isDraft: Boolean get() = lastMessageAtMs == null

    companion object {
        fun parse(json: JSONObject): ChatSession = ChatSession(
            id = json.getString("id"),
            petId = json.getString("pet_id"),
            title = json.optString("title"),
            agentCategories = json.optJSONArray("agent_categories").toStringList(),
            createdAtMs = json.getString("created_at").toEpochMillis(),
            lastMessageAtMs = json.optStringOrNull("last_message_at")?.toEpochMillis(),
        )
    }
}

/**
 * `GET /app/chats?pet_id=…` — 내 계정 + 이 강아지의 최근 대화, **최근 갱신 순**.
 *
 * @property maxSessions 저쪽 상한(지금 5). 앱이 "가장 오래된 것이 사라집니다" 를 언제
 *   보여 줄지 정하는 데 쓴다. **안 오면 null** — 숫자를 지어 넣으면 서버와 갈라진다.
 */
@Immutable
data class ChatSessionList(val sessions: List<ChatSession>, val maxSessions: Int?) {
    /**
     * 화면에 "최근 대화" 로 그릴 것. **서버 순서 그대로**, 초안만 뺀다.
     *
     * 능력별로 나누지 않는다. 한 대화에 훈련과 산책이 같이 돌 수 있고(합집합), 나누면
     * 같은 대화가 두 줄에 서거나 순서가 흐트러진다. 라벨은 카드 안에 붙인다.
     */
    val recent: List<ChatSession> get() = sessions.filter { !it.isDraft }

    companion object {
        fun parse(json: JSONObject): ChatSessionList {
            val arr = json.getJSONArray("sessions")
            return ChatSessionList(
                sessions = (0 until arr.length()).map { ChatSession.parse(arr.getJSONObject(it)) },
                maxSessions = if (json.isNull("max_sessions")) null else json.optInt("max_sessions"),
            )
        }
    }
}

/**
 * 질문 하나와 그 답.
 *
 * @property publicResponse 그때 사용자에게 갔던 [AssistantResponse] 그대로. **완료된
 *   turn 에만 있다.** 되살릴 때 산책 카드·handoff·clarify 를 다시 그리라고 저쪽이
 *   `assistant_content` 와 따로 준다.
 * @property errorCode 실패한 turn 의 이유 (`ASSISTANT_FAILED` · `STALE_PROCESSING` …).
 *   사용자에게 보여 줄 말이 아니라 코드다.
 */
@Immutable
data class ChatTurn(
    val id: String,
    val clientMessageId: String,
    val processingStatus: ProcessingStatus,
    val userContent: String,
    val assistantContent: String?,
    val agentCategories: List<String>,
    /** 저쪽 `AssistantStatus` 문자열 (`ANSWERED` …). 완료된 turn 에만 있다. */
    val assistantStatus: String?,
    val publicResponse: AssistantResponse?,
    val errorCode: String?,
    val completedAtMs: Long?,
    val createdAtMs: Long,
) {
    /** 모르는 값이 와도 죽지 않게 [UNKNOWN] 으로 떨어진다 — `AssistantResponse.Status` 와 같은 이유. */
    enum class ProcessingStatus { PROCESSING, COMPLETED, FAILED, UNKNOWN }

    companion object {
        fun parse(json: JSONObject): ChatTurn = ChatTurn(
            id = json.getString("id"),
            clientMessageId = json.getString("client_message_id"),
            processingStatus = json.optString("processing_status").toProcessingStatus(),
            userContent = json.optString("user_content"),
            assistantContent = json.optStringOrNull("assistant_content"),
            agentCategories = json.optJSONArray("agent_categories").toStringList(),
            assistantStatus = json.optStringOrNull("assistant_status"),
            publicResponse = json.optJSONObject("public_response")?.let(AssistantResponse::parse),
            errorCode = json.optStringOrNull("error_code"),
            completedAtMs = json.optStringOrNull("completed_at")?.toEpochMillis(),
            createdAtMs = json.getString("created_at").toEpochMillis(),
        )

        private fun String.toProcessingStatus(): ProcessingStatus = when (this) {
            "processing" -> ProcessingStatus.PROCESSING
            "completed" -> ProcessingStatus.COMPLETED
            "failed" -> ProcessingStatus.FAILED
            else -> ProcessingStatus.UNKNOWN
        }
    }
}

/** `GET /app/chats/{session_id}` — 대화 하나와 메시지 전부, **오간 순서대로**. */
@Immutable
data class ChatSessionDetail(val session: ChatSession, val turns: List<ChatTurn>) {
    companion object {
        fun parse(json: JSONObject): ChatSessionDetail {
            val arr = json.getJSONArray("turns")
            return ChatSessionDetail(
                session = ChatSession.parse(json.getJSONObject("session")),
                turns = (0 until arr.length()).map { ChatTurn.parse(arr.getJSONObject(it)) },
            )
        }
    }
}

/**
 * 요약에 딸린 출처 하나. 저쪽 `ChatCitation` — `label` 은 항상 있고 `url` 은 원문에
 * 그 주소가 있었을 때만이다. **문장으로 펴지 않고 구조 그대로 든다** — 화면이 링크로
 * 그릴지 글자로 그릴지 정한다.
 */
@Immutable
data class ChatCitation(val label: String, val url: String?) {
    companion object {
        fun parse(json: JSONObject): ChatCitation = ChatCitation(
            label = json.optString("label"),
            url = json.optStringOrNull("url"),
        )
    }
}

/**
 * 보관함의 요약 하나. 나중의 `저장소` 화면이 그린다.
 *
 * @property sourceSessionId 원본 대화. **null 이면 원본이 5개 유지에 밀려 사라진 것**이고
 *   요약은 그대로 남는다 — 저쪽이 그렇게 설계했다. 카드에서 "원본 보기" 를 감추는 근거.
 * @property sourceTurnCount 요약할 때 원본에 있던 문답 수. 대화가 더 이어지면 저쪽이
 *   새 요약을 받아 주고(같은 수면 `SUMMARY_ALREADY_EXISTS`), 앱이 이 수로 "그 뒤로
 *   N 개 더" 를 셀 수 있다.
 * @property cautions **비어 있을 수 있다.** 원문에 주의가 없었으면 지어내지 않는 것이 맞다.
 */
@Immutable
data class ChatSummary(
    val id: String,
    val petId: String,
    val sourceSessionId: String?,
    val sourceTurnCount: Int,
    val title: String,
    val questionSummary: String,
    val answerSummary: String,
    val keyPoints: List<String>,
    val cautions: List<String>,
    val sourceCitations: List<ChatCitation>,
    val agentCategories: List<String>,
    val model: String,
    val promptVersion: String,
    val completedAtMs: Long,
    val createdAtMs: Long,
) {
    companion object {
        fun parse(json: JSONObject): ChatSummary = ChatSummary(
            id = json.getString("id"),
            petId = json.getString("pet_id"),
            sourceSessionId = json.optStringOrNull("source_session_id"),
            sourceTurnCount = json.optInt("source_turn_count"),
            title = json.optString("title"),
            questionSummary = json.optString("question_summary"),
            answerSummary = json.optString("answer_summary"),
            keyPoints = json.optJSONArray("key_points").toStringList(),
            cautions = json.optJSONArray("cautions").toStringList(),
            sourceCitations = json.optJSONArray("source_citations").toObjectList(ChatCitation::parse),
            agentCategories = json.optJSONArray("agent_categories").toStringList(),
            model = json.optString("model"),
            promptVersion = json.optString("prompt_version"),
            completedAtMs = json.optString("completed_at").toEpochMillis(),
            createdAtMs = json.getString("created_at").toEpochMillis(),
        )
    }
}

/** `GET /app/chats/summaries?pet_id=…` — **완성된 요약만**, 저장한 순서의 역순. */
@Immutable
data class ChatSummaryList(val summaries: List<ChatSummary>) {
    companion object {
        fun parse(json: JSONObject): ChatSummaryList =
            ChatSummaryList(json.getJSONArray("summaries").toObjectList(ChatSummary::parse))
    }
}

// -- 아래는 배관 -------------------------------------------------------------

private fun String.toEpochMillis(): Long = with(AuthApi) { this@toEpochMillis.toEpochMs() }

/** `optString` 은 없는 키와 `null` 에 빈 문자열을 준다. `source_session_id = null` 을 가르려면 이게 필요하다. */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun JSONArray?.toStringList(): List<String> =
    List(this?.length() ?: 0) { this!!.optString(it) }

private fun <T> JSONArray?.toObjectList(parse: (JSONObject) -> T): List<T> =
    List(this?.length() ?: 0) { parse(this!!.getJSONObject(it)) }
