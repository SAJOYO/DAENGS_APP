package com.daengs.app.place

import com.daengs.app.auth.Session
import com.daengs.app.auth.AccountScope
import com.daengs.app.location.GeoPoint
import com.daengs.app.place.bookmarks.BookmarkTurn
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class ConversationResult(
    val sessionId: String,
    val revision: Int,
    val requestId: String,
    val filters: JsonObject,
    val search: PlaceSearchResponse?,
    val selected: PlaceKey?,
    val order: List<PlaceKey>,
    val answer: String?,
    val receipt: JsonObject,
    val answerStatus: String = "none",
    val searchPool: String = "all_places",
    val excludedKeys: Set<PlaceKey> = emptySet(),
) {
    val poolLabel get() = when (searchPool) {
        "new_candidates" -> "새 후보"
        "unbookmarked" -> "찜하지 않은 곳"
        else -> "전체 장소"
    }
    val kinds get() = filters.getValue("candidate_kinds").jsonArray.map { PlaceKind.fromWire(it.jsonPrimitive.content) }
    val origin get() = filters.getValue("spatial").jsonObject.let {
        GeoPoint(it.getValue("lat").jsonPrimitive.double, it.getValue("lng").jsonPrimitive.double)
    }
    val radius get() = filters.getValue("spatial").jsonObject.getValue("radius_m").jsonPrimitive.int
    val nameQuery get() = filters.getValue("name_query").jsonPrimitive.content
    val parkingFirst get() = filters.getValue("preferences").jsonArray.isNotEmpty()
    val matches get() = receipt.getValue("result_matches_filters").jsonPrimitive.boolean
    val failed get() = receipt["execution"]?.jsonPrimitive?.content == "failed"
    val preservesDisplay get() = receipt["bookmark_command"]?.let { it != JsonNull } == true ||
        receipt["saved_search_filters"]?.let { it != JsonNull } == true ||
        receipt["code"]?.jsonPrimitive?.content in setOf("feedback_no_mutation", "saved_search_clarify", "saved_search_client_required", "search_already_visible")
}

data class ConversationUiState(
    val busy: Boolean = false,
    val result: ConversationResult? = null,
    val error: String? = null,
    val selected: PlaceKey? = null,
    val notice: String? = null,
    val answerBusy: Boolean = false,
    val answerError: String? = null,
    val filterRetry: ConversationFilterEdit? = null,
    val canUndo: Boolean = false,
    val commandAnswer: String? = null,
)

fun JsonObject.toConversationResult(): ConversationResult {
    require(getValue("contract_version").jsonPrimitive.content == "facility-conversation-v2")
    fun JsonElement.key() = jsonObject.let {
        PlaceKey(it.getValue("source").jsonPrimitive.content, it.getValue("ref").jsonPrimitive.content)
    }
    val search = this["search"]?.takeUnless { it is JsonNull }?.jsonObject?.toPlaceSearchResponse()
    val selected = this["selected"]?.takeUnless { it is JsonNull }?.key()
    val order = getValue("display_order").jsonArray.map { it.key() }
    val keys = search?.groups?.flatMap { it.results }?.map { it.place.key }.orEmpty().toSet()
    require(order.distinct().size == order.size && order.all { it in keys })
    require(selected == null || selected in keys)
    val revision = getValue("revision").jsonPrimitive.int
    val answer = this["answer"]?.takeUnless { it is JsonNull }?.jsonObject
    val status = getValue("answer_status").jsonPrimitive.content
    require(status in listOf("none", "pending", "ready"))
    require((status == "ready") == (answer != null))
    require(answer == null || answer.getValue("revision").jsonPrimitive.int == revision)
    return ConversationResult(
        getValue("session_id").jsonPrimitive.content, getValue("revision").jsonPrimitive.int,
        getValue("client_request_id").jsonPrimitive.content, getValue("filters").jsonObject,
        search, selected, order,
        answer?.get("text")?.jsonPrimitive?.content,
        getValue("receipt").jsonObject, status,
        this["search_pool"]?.jsonPrimitive?.content ?: "all_places",
        this["excluded_keys"]?.jsonArray?.map { it.key() }?.toSet().orEmpty(),
    ).also { result ->
        require(result.searchPool in setOf("all_places", "unbookmarked", "new_candidates"))
        result.receipt["search_pool"]?.let { require(it.jsonPrimitive.content == result.searchPool) }
        search?.requireDogEcho(PlaceSearchRequest(result.origin, result.radius, result.kinds, dogs = search.dogs))
        if (result.matches) {
            require(search != null && search.groups.map { it.kind } == result.kinds)
            val echoedDogs = this.getValue("search").jsonObject["dogs"] ?: JsonArray(emptyList())
            require(echoedDogs == result.filters["dogs"])
        }
    }.let { it.copy(answer = FacilityResponsePolicy.answer(it)) }
}

fun interface ConversationClient {
    suspend fun exchange(token: String, payload: JsonObject): JsonObject
    suspend fun recover(token: String, payload: JsonObject): JsonObject = throw FacilityException(503)
    suspend fun answer(token: String, payload: JsonObject): JsonObject = throw FacilityException(503)
}

class ConversationApi(private val baseUrl: () -> String) : ConversationClient {
    override suspend fun exchange(token: String, payload: JsonObject) = post("", token, payload)
    override suspend fun recover(token: String, payload: JsonObject) = post("/recover", token, payload)
    override suspend fun answer(token: String, payload: JsonObject) = post("/answer", token, payload)

    private suspend fun post(path: String, token: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl().trimEnd('/') + "/app/places/conversation" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
            connectTimeout = 10_000; readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            if (connection.responseCode !in 200..299) throw FacilityException(connection.responseCode)
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= 1024 * 1024)
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }
}

/** Normal and AI searches share one saved state. The client never invents result facts. */
class FacilityConversationRepository(
    private val client: ConversationClient,
    private val fallback: PlaceSearchRepository,
    private val freshSession: suspend () -> Session?,
    private val currentSession: () -> Session?,
    private val currentAccountScope: (() -> AccountScope)? = null,
) : PlaceSearchRepository {
    private val mutable = MutableStateFlow(ConversationUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var owner: String? = null
    // Cancellation/timeout says nothing about whether the server committed. Keep its identity.
    private var pending: JsonObject? = null
    private var pendingBookmarks: BookmarkTurn? = null
    private var activeBookmarks: BookmarkTurn? = null
    private data class Undo(val filters: JsonObject, val sessionId: String, val revision: Int, val pool: String)
    private var undo: Undo? = null

    fun belongsTo(accountId: String?) = accountId != null && owner == accountId

    fun isAssistantAccountCurrent(turn: FacilityAssistantTurn): Boolean {
        return accountCurrent(turn.session, turn.account)
    }

    private fun accountCurrent(session: Session, account: AccountScope?): Boolean {
        val live = currentSession()
        return live?.appUserId == session.appUserId && if (currentAccountScope != null)
            account?.ownerId == session.appUserId && currentAccountScope.invoke() == account
        else live.refreshToken == session.refreshToken
    }

    suspend fun assistantAccessToken(turn: FacilityAssistantTurn): String {
        check(isAssistantTurnCurrent(turn))
        val session = freshSession() ?: throw FacilityException(401)
        check(isAssistantTurnCurrent(turn) && session.appUserId == turn.session.appUserId)
        return session.accessToken
    }

    fun isAssistantTurnCurrent(turn: FacilityAssistantTurn) =
        isAssistantAccountCurrent(turn) && generation == turn.generation && mutable.value.result == turn.before

    suspend fun captureAssistantTurn(requestId: String, bookmarks: BookmarkTurn?): FacilityAssistantTurn {
        val session = freshSession() ?: throw FacilityException(401)
        if (owner != null && owner != session.appUserId) invalidate()
        owner = session.appUserId
        check(!mutable.value.busy) { "시설 검색을 마친 뒤 다시 말해 주세요." }
        val before = mutable.value.result
        val visible = before?.search?.overviewHits(before.parkingFirst)?.map { it.place.key }.orEmpty()
        val context = buildJsonObject {
            put("client_request_id", requestId)
            before?.let {
                put("session_id", it.sessionId); put("expected_revision", it.revision)
                put("visible_order", buildJsonArray { visible.forEach { key -> add(buildJsonObject {
                    put("source", key.source); put("ref", key.ref)
                }) } })
                mutable.value.selected?.takeIf { it in visible }?.let { key ->
                    put("visible_selected", buildJsonObject { put("source", key.source); put("ref", key.ref) })
                }
                if (bookmarks != null) put("bookmark_commands", "v1")
            }
        }
        val account = currentAccountScope?.invoke()
        check(accountCurrent(session, account))
        return FacilityAssistantTurn(generation, session, account, before, context, bookmarks)
    }

    suspend fun acceptAssistantTurn(turn: FacilityAssistantTurn, reference: com.daengs.app.assistant.FacilityAssistantReference): String? {
        fun current() {
            check(isAssistantTurnCurrent(turn)) {
                "시설 검색이 바뀌었어요. 현재 목록에서 다시 말해 주세요."
            }
        }
        current()
        require(reference.requestId == turn.context.getValue("client_request_id").jsonPrimitive.content)
        val result = client.recover(assistantAccessToken(turn), reference.recovery()).toConversationResult()
        current()
        require(result.sessionId == reference.sessionId && result.revision == reference.revision && result.requestId == reference.requestId)
        turn.before?.let { require(result.sessionId == it.sessionId && result.revision == it.revision + 1) }
        var completion: String? = null
        val command = result.receipt["bookmark_command"]?.takeUnless { it == JsonNull }?.jsonObject
        if (command != null) {
            val before = requireNotNull(turn.before)
            require(result.filters == before.filters && result.search == before.search && result.order == before.order && !result.failed && result.answerStatus == "none")
            val key = command.getValue("key").jsonObject.let { PlaceKey(it.getValue("source").jsonPrimitive.content, it.getValue("ref").jsonPrimitive.content) }
            require(key in before.order && turn.bookmarks != null)
            val saved = command.getValue("saved").jsonPrimitive.boolean
            completion = FacilityResponsePolicy.bookmark(turn.bookmarks.execute(result.requestId, key, saved), saved)
            current()
        }
        val selected = mutable.value.selected
        generation++
        pending = null; pendingBookmarks = null; undo = null
        publish(result)
        if (result.preservesDisplay) mutable.value = mutable.value.copy(selected = selected, commandAnswer = completion)
        return completion
    }

    /** Reconcile a stale/expired server view, without replaying the user's ordinal or command. */
    suspend fun recoverAssistantView(turn: FacilityAssistantTurn): String {
        check(isAssistantTurnCurrent(turn))
        val before = requireNotNull(turn.before)
        val result = try {
            client.recover(assistantAccessToken(turn), buildJsonObject {
                put("session_id", before.sessionId)
                put("client_request_id", turn.context.getValue("client_request_id"))
            }).toConversationResult().also {
                require(it.sessionId == before.sessionId && it.revision >= before.revision)
            }
        } catch (error: FacilityException) {
            if (error.status != 410) throw error
            val restoreId = UUID.nameUUIDFromBytes(("assistant-restore:" + turn.context.getValue("client_request_id").jsonPrimitive.content).toByteArray(Charsets.UTF_8)).toString()
            val session = freshSession() ?: throw FacilityException(401)
            check(isAssistantTurnCurrent(turn))
            restore(session, before, turn.generation, turn.account, restoreId)
        }
        check(isAssistantTurnCurrent(turn))
        if (result.failed) return "검색을 다시 불러오지 못했어요. 잠시 뒤 다시 시도해 주세요."
        generation++
        pending = null; pendingBookmarks = null; undo = null
        val notice = FacilityResponsePolicy.RESTORED
        publish(result, notice)
        return notice
    }

    fun cancelPending(cancelBookmarks: Boolean = false) {
        if (cancelBookmarks) { activeBookmarks?.cancel(); pendingBookmarks?.cancel() }
        generation++
        // Undo restores into a new session. An abandoned restore must not replace a new manual intent.
        if (undo != null && pending?.get("mode")?.jsonPrimitive?.content == "restore") pending = null
        undo = null
        mutable.value = mutable.value.copy(busy = false, answerBusy = false, canUndo = false)
    }

    fun invalidate() {
        activeBookmarks?.cancel(); pendingBookmarks?.cancel()
        activeBookmarks = null; pendingBookmarks = null
        generation++
        mutable.value = ConversationUiState()
        owner = null
        pending = null
        undo = null
    }

    fun select(key: PlaceKey) {
        if (key in mutable.value.result?.order.orEmpty()) mutable.value = mutable.value.copy(selected = key)
    }

    override suspend fun search(request: PlaceSearchRequest): PlaceSearchResponse {
        val session = freshSession()
        if (session == null || request.dogSize != null || request.dogWeightKg != null || request.dogAgeYears != null) {
            invalidate()
            return fallback.search(request)
        }
        val result = run(session, "manual", request.toJson())
        return requireNotNull(result.search)
    }

    suspend fun overview(requests: List<PlaceSearchRequest>): PlaceSearchResponse {
        invalidate()
        return searchPlaceBatches(fallback, requests)
    }

    suspend fun chat(query: String, visibleOrder: List<PlaceKey>, bookmarks: BookmarkTurn? = null,
        visibleSelected: PlaceKey? = mutable.value.selected) {
        val session = freshSession() ?: throw FacilityException(401)
        require(mutable.value.result != null) { "먼저 카테고리를 선택해 주변 장소를 검색해 주세요." }
        val before = mutable.value.result
        val result = run(session, "chat", query = query, visibleOrder = visibleOrder, bookmarks = bookmarks,
            visibleSelected = visibleSelected)
        if (before != null && !result.failed && result.matches && (result.filters != before.filters || result.searchPool != before.searchPool) &&
            result.sessionId == before.sessionId && result.revision == before.revision + 1 &&
            mutable.value.notice == null) {
            undo = Undo(before.filters, result.sessionId, result.revision, before.searchPool)
            mutable.value = mutable.value.copy(canUndo = true)
        }
    }

    /** 마지막 AI 변경 한 번만 되돌린다. 기존 restore 계약으로 서버에서 다시 검색한다. */
    suspend fun undo(): Boolean {
        val target = undo ?: return false
        if (mutable.value.busy) return false
        val before = mutable.value.result ?: return false
        if (before.sessionId != target.sessionId || before.revision != target.revision) return false
        val session = freshSession() ?: throw FacilityException(401)
        if (owner != session.appUserId) { invalidate(); throw FacilityException(401) }
        val mine = ++generation
        val account = currentAccountScope?.invoke()
        val payload = pending?.takeIf { it["mode"]?.jsonPrimitive?.content == "restore" && it["restore_filters"] == target.filters }
            ?: buildJsonObject {
                put("client_request_id", UUID.randomUUID().toString()); put("mode", "restore")
                put("restore_filters", target.filters)
                put("candidate_pools", "v1"); put("restore_pool", target.pool)
                put("source_session_id", before.sessionId); put("source_revision", before.revision)
            }
        pending = payload
        mutable.value = mutable.value.copy(busy = true, answerBusy = false, error = null)
        try {
            val result = client.exchange(session.accessToken, payload).toConversationResult()
            checkLive(mine, session, account)
            require(result.requestId == payload.getValue("client_request_id").jsonPrimitive.content && result.revision == 1)
            require(result.filters == target.filters && result.searchPool == target.pool && result.sessionId != before.sessionId && result.matches && !result.failed)
            pending = null; undo = null
            publish(result, "이전 검색 조건으로 되돌렸어요.")
            return true
        } catch (error: Exception) {
            if (mine == generation) mutable.value = mutable.value.copy(busy = false,
                error = if (error is CancellationException) null else "되돌리지 못했어요. 현재 조건을 유지했으니 다시 시도해 주세요.")
            throw error
        }
    }

    suspend fun applyFilters(edit: ConversationFilterEdit): Boolean {
        val session = freshSession() ?: throw FacilityException(401)
        if (owner != session.appUserId) {
            invalidate()
            throw FacilityException(401)
        }
        val before = mutable.value.result
        if (before?.sessionId != edit.sessionId || before.revision != edit.revision) {
            mutable.value = mutable.value.copy(notice = "검색 조건이 바뀌었어요. 현재 조건을 확인한 뒤 다시 선택해 주세요.")
            return false
        }
        run(session, "filters", filterEdit = edit)
        return true
    }

    /** Execute already compiled conditions through the existing restore/search endpoint, no LLM. */
    suspend fun applySearchPlan(transfer: SearchPlanTransfer): ConversationResult {
        if (!transfer.isCurrent()) throw CancellationException("Saved search changed")
        val session = freshSession() ?: throw FacilityException(401)
        if (!transfer.isCurrent()) throw CancellationException("Saved search changed")
        if (session.appUserId != transfer.ownerId) throw CancellationException("Saved search owner changed")
        if (owner != null && owner != session.appUserId) { invalidate(); throw FacilityException(401) }
        val before = mutable.value.result
        require(transfer.excluded.isEmpty() || before?.excludedKeys == transfer.excluded) { "탐색 제외가 바뀌었어요. 현재 검색 조건을 다시 확인해 주세요." }
        val mine = ++generation
        val account = currentAccountScope?.invoke()
        val payload = buildJsonObject {
            put("client_request_id", UUID.randomUUID().toString()); put("mode", "restore")
            put("restore_filters", transfer.filters)
            put("candidate_pools", "v1"); put("restore_pool", transfer.pool)
            before?.let { put("source_session_id", it.sessionId); put("source_revision", it.revision) }
        }
        // This isolated read starts a new server session. An abandoned response never replaces
        // the current session or its pending write identity.
        mutable.value = mutable.value.copy(busy = true, answerBusy = false, error = null)
        try {
            val result = client.exchange(session.accessToken, payload).toConversationResult()
            checkLive(mine, session, account)
            if (!transfer.isCurrent()) throw CancellationException("Saved search changed")
            require(result.requestId == payload.getValue("client_request_id").jsonPrimitive.content && result.revision == 1)
            require(result.filters == transfer.filters && result.searchPool == transfer.pool && result.sessionId != before?.sessionId && result.matches && !result.failed)
            activeBookmarks?.cancel(); pendingBookmarks?.cancel()
            pending = null; pendingBookmarks = null; activeBookmarks = null; undo = null
            owner = session.appUserId
            val count = result.search?.groups?.sumOf { it.results.size } ?: 0
            return publish(result, "${result.poolLabel} 중 조건에 맞는 ${count}곳을 찾았어요.")
        } finally {
            if (mine == generation) mutable.value = mutable.value.copy(busy = false)
        }
    }

    /** Called only after the committed state has reached the map and filters. */
    suspend fun completeAnswer() {
        val before = mutable.value.result ?: return
        if (before.answerStatus != "pending" || mutable.value.busy) return
        val mine = generation
        val account = currentAccountScope?.invoke()
        val session = freshSession() ?: return
        if (session.appUserId != owner) return
        mutable.value = mutable.value.copy(answerBusy = true, answerError = null)
        try {
            val result = client.answer(session.accessToken, buildJsonObject {
                put("session_id", before.sessionId); put("revision", before.revision)
                put("client_request_id", before.requestId)
            }).toConversationResult()
            checkLive(mine, session, account)
            require(result.sessionId == before.sessionId && result.revision == before.revision && result.requestId == before.requestId)
            require(result.copy(answer = before.answer, answerStatus = before.answerStatus) == before)
            mutable.value = mutable.value.copy(result = result, answerBusy = false)
        } catch (error: Exception) {
            if (mine == generation) mutable.value = mutable.value.copy(answerBusy = false,
                answerError = if (error is CancellationException) null else "검색은 반영됐지만 설명을 불러오지 못했어요.")
            if (error is CancellationException) throw error
        }
    }

    private fun checkLive(mine: Long, session: Session, account: AccountScope?) {
        if (mine != generation || !accountCurrent(session, account)) {
            throw CancellationException("Obsolete facility response")
        }
    }

    private fun publish(result: ConversationResult, notice: String? = null,
        filterRetry: ConversationFilterEdit? = null): ConversationResult {
        mutable.value = ConversationUiState(result = result, selected = result.selected,
            notice = if (result.failed) {
                if (result.receipt["known_places"]?.jsonArray?.isNotEmpty() == true)
                    "이미 아는 곳이라는 정정은 반영했어요. 검색을 완료하지 못해 이전 결과를 유지했어요."
                else "검색을 변경하지 못해 이전 조건과 결과를 유지했어요."
            } else notice,
            filterRetry = filterRetry)
        return result
    }

    private suspend fun run(
        session: Session, mode: String, manual: JsonObject? = null,
        query: String = "", visibleOrder: List<PlaceKey> = emptyList(),
        filterEdit: ConversationFilterEdit? = null,
        bookmarks: BookmarkTurn? = null,
        visibleSelected: PlaceKey? = null,
    ): ConversationResult {
        if (owner != null && owner != session.appUserId) invalidate()
        owner = session.appUserId
        val before = mutable.value.result
        val mine = ++generation
        val account = currentAccountScope?.invoke()
        undo = null
        val next = buildJsonObject {
            put("client_request_id", UUID.randomUUID().toString()); put("mode", mode); put("query", query)
            put("candidate_pools", "v1")
            if (before != null) {
                put("session_id", before.sessionId); put("expected_revision", before.revision)
            }
            manual?.let { put("manual", it) }
            filterEdit?.let { put("remove_filters", it.toJson()) }
            if (mode == "chat") visibleSelected?.let { key ->
                put("visible_selected", buildJsonObject { put("source", key.source); put("ref", key.ref) })
            }
            if (mode == "chat" && bookmarks != null) put("bookmark_commands", "v1")
            if (mode == "chat" && bookmarks?.supportsSearch == true) put("saved_search", "v1")
            put("visible_order", buildJsonArray {
                visibleOrder.take(120).forEach { key -> add(buildJsonObject {
                    put("source", key.source); put("ref", key.ref)
                }) }
            })
        }
        val retry = pending
        // The exact request is retried, including its old revision and visible-card references.
        val payload = retry?.takeIf {
            it["mode"]?.jsonPrimitive?.content == "restore" ||
                (it["mode"] == next["mode"] && it["manual"] == next["manual"] && it["query"] == next["query"] &&
                    it["remove_filters"] == next["remove_filters"] && it["bookmark_commands"] == next["bookmark_commands"] &&
                    it["saved_search"] == next["saved_search"]) ||
                before == null
        } ?: next
        val commandContext = if (payload === next) bookmarks else pendingBookmarks
        pending = payload
        pendingBookmarks = commandContext
        activeBookmarks = commandContext
        mutable.value = mutable.value.copy(busy = true, error = null, notice = null, answerBusy = false,
            answerError = null, filterRetry = filterEdit, canUndo = false, commandAnswer = null)
        try {
            var notice: String? = if (payload !== next && (payload["mode"]?.jsonPrimitive?.content == "restore" ||
                payload["manual"] != next["manual"] || payload["query"] != next["query"]))
                "이전 요청을 복구했어요. 원하는 요청을 다시 입력해 주세요." else null
            val result = try {
                val result = client.exchange(session.accessToken, payload).toConversationResult()
                require(result.requestId == payload.getValue("client_request_id").jsonPrimitive.content)
                require(result.revision == (payload["expected_revision"]?.jsonPrimitive?.int ?: 0) + 1)
                require(payload["session_id"] == null || result.sessionId == payload.getValue("session_id").jsonPrimitive.content)
                if (payload["mode"]?.jsonPrimitive?.content == "restore") require(result.filters == payload["restore_filters"])
                result
            } catch (error: FacilityException) {
                checkLive(mine, session, account)
                if (error.status == 409) {
                    val recovered = try {
                        client.recover(session.accessToken, buildJsonObject {
                            payload["session_id"]?.let { put("session_id", it) }
                            put("client_request_id", payload.getValue("client_request_id"))
                        }).toConversationResult().also {
                            require(before == null || it.sessionId == before.sessionId && it.revision >= before.revision)
                        }
                    } catch (expired: FacilityException) {
                        if (expired.status != 410 || before == null) throw expired
                        restore(session, before, mine, account)
                    }
                    notice = FacilityResponsePolicy.RESTORED
                    recovered
                } else if (error.status == 410 && before != null) {
                    notice = FacilityResponsePolicy.RESTORED
                    restore(session, before, mine, account)
                } else throw error
            }
            // A bookmark is independent of the current map generation, but bound to the
            // exact local request, account lifetime and utterance-time target.
            val command = result.receipt["bookmark_command"]?.takeUnless { it is JsonNull }?.jsonObject
            var commandAnswer: String? = null
            val savedFilters = result.receipt["saved_search_filters"]?.takeUnless { it == JsonNull }?.jsonObject
            if (savedFilters != null) {
                checkLive(mine, session, account)
                require(command == null && result.requestId == payload["client_request_id"]?.jsonPrimitive?.content &&
                    payload["saved_search"]?.jsonPrimitive?.content == "v1" && commandContext?.supportsSearch == true)
                require(before != null && result.filters == before.filters && result.search == before.search &&
                    result.order == before.order && !result.failed && result.answerStatus == "none")
                commandAnswer = commandContext!!.search(savedFilters) { mine == generation }
            }
            if (command != null && result.requestId == payload["client_request_id"]?.jsonPrimitive?.content &&
                payload["bookmark_commands"]?.jsonPrimitive?.content == "v1" && commandContext != null) {
                if (!accountCurrent(session, account))
                    throw CancellationException("Bookmark account changed")
                require(before != null && result.filters == before.filters && result.search == before.search &&
                    result.order == before.order && !result.failed && result.answerStatus == "none")
                val key = command.getValue("key").jsonObject.let {
                    PlaceKey(it.getValue("source").jsonPrimitive.content, it.getValue("ref").jsonPrimitive.content)
                }
                require(key in before.order)
                if (mine == generation) mutable.value = mutable.value.copy(answerBusy = true)
                val saved = command.getValue("saved").jsonPrimitive.boolean
                commandAnswer = FacilityResponsePolicy.bookmark(commandContext.execute(result.requestId, key, saved), saved)
            }
            checkLive(mine, session, account)
            pending = null; pendingBookmarks = null
            val currentSelection = mutable.value.selected
            val published = publish(result, notice, if (result.failed) filterEdit?.copy(revision = result.revision) else null)
            if (published.preservesDisplay) mutable.value = mutable.value.copy(
                commandAnswer = commandAnswer, selected = currentSelection)
            return published
        } catch (error: Exception) {
            if (mine == generation) mutable.value = mutable.value.copy(
                busy = false, answerBusy = false, error = when {
                    error is CancellationException -> null
                    error is FacilityException && error.status == 409 -> "이전 요청의 처리 상태를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
                    else -> error.facilityMessage()
                },
            )
            throw error
        }
    }

    private suspend fun restore(session: Session, before: ConversationResult, mine: Long, account: AccountScope?, requestId: String = UUID.randomUUID().toString()): ConversationResult {
        checkLive(mine, session, account)
        val payload = buildJsonObject {
            put("client_request_id", requestId); put("mode", "restore")
            put("restore_filters", before.filters)
            put("candidate_pools", "v1"); put("restore_pool", before.searchPool)
        }
        pending = payload
        val result = client.exchange(session.accessToken, payload).toConversationResult()
        require(result.requestId == payload.getValue("client_request_id").jsonPrimitive.content && result.revision == 1)
        require(result.filters == before.filters && result.searchPool == before.searchPool && result.sessionId != before.sessionId)
        return result
    }
}
