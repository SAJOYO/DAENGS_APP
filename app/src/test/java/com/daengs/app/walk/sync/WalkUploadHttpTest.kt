package com.daengs.app.walk.sync

import com.daengs.app.walk.RecordedSession
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WalkUploadHttpTest {
    private val session = RecordedSession(UPLOAD_SESSION, startedAtMillis = 1_000, endedAtMillis = 10_000)
    private val points = listOf(uploadFix(0), uploadFix(1))

    @Test fun `생성 201 및 재전송 200은 작은 응답을 선택하고 기존 요청 본문을 보낸다`() = runBlocking {
        for ((code, status) in listOf(201 to "stored", 200 to "replayed")) {
            withServer(code, uploadReceipt(points, status).toString()) { api, request ->
                assertEquals(UPLOAD_WALK, api.upload("test-token", session, points).getOrThrow())
                val sent = request.get()
                assertEquals("POST /app/walks?response=receipt-v1 HTTP/1.1", sent.line)
                assertEquals(UPLOAD_SESSION, sent.body.getString("client_session_id"))
                assertEquals(points.size, sent.body.getJSONArray("points").length())
                assertTrue(sent.headers.any { it.equals("Authorization: Bearer test-token", ignoreCase = true) })
            }
        }
    }

    @Test fun `append는 두 ID와 보낸 범위를 검증하며 본문 계약을 유지한다`() = runBlocking {
        val chunk = listOf(uploadFix(2_000), uploadFix(2_001))
        withServer(200, uploadReceipt(chunk, "replayed").toString()) { api, request ->
            api.appendPoints("test-token", UPLOAD_WALK, UPLOAD_SESSION, chunk).getOrThrow()
            assertEquals("POST /app/walks/$UPLOAD_WALK/points?response=receipt-v1 HTTP/1.1", request.get().line)
            assertEquals(setOf("points"), request.get().body.keys().asSequence().toSet())
            assertEquals(2_000, request.get().body.getJSONArray("points").getJSONObject(0).getInt("client_seq"))
        }
    }

    @Test fun `빈 최초 업로드는 null 청크로 확인한다`() = runBlocking {
        withServer(201, uploadReceipt(emptyList()).toString()) { api, request ->
            assertEquals(UPLOAD_WALK, api.upload("t", session, emptyList()).getOrThrow())
            assertEquals(0, request.get().body.getJSONArray("points").length())
        }
    }

    @Test fun `query를 무시하는 구형 서버의 생성 및 누적 append 응답도 검증한다`() = runBlocking {
        withServer(200, legacyUpload(points).toString()) { api, _ ->
            assertEquals(UPLOAD_WALK, api.upload("t", session, points).getOrThrow())
        }
        withServer(200, legacyUpload(points).toString()) { api, _ ->
            api.appendPoints("t", UPLOAD_WALK, UPLOAD_SESSION, points.takeLast(1)).getOrThrow()
        }
    }

    @Test fun `HTTP 실패는 그대로 전달하며 호환 POST를 재발송하지 않는다`() = runBlocking {
        for (status in listOf(401, 403, 409, 422, 500, 503)) {
            withServer(status, """{"detail":"walk_chunk_conflict"}""") { api, _ ->
                val failure = api.appendPoints("t", UPLOAD_WALK, UPLOAD_SESSION, points).exceptionOrNull()
                assertTrue(failure is WalkHttpException)
                assertEquals(status, (failure as WalkHttpException).statusCode)
                assertEquals("walk_chunk_conflict", failure.message)
            }
        }
    }

    @Test fun `2xx의 잘못된 JSON이나 다른 청크는 성공으로 바뀌지 않는다`() = runBlocking {
        for ((code, body) in listOf(200 to "broken", 204 to "", 200 to "{}",
            200 to uploadReceipt(points).put("client_session_id", UPLOAD_OTHER).toString(),
            200 to uploadReceipt(listOf(uploadFix(2))).toString(),
            200 to legacyUpload(points.take(1)).toString())) {
            withServer(code, body) { api, _ ->
                assertTrue(api.appendPoints("t", UPLOAD_WALK, UPLOAD_SESSION, points).isFailure)
            }
        }
        withServer(200, uploadReceipt(points).put("walk_id", UPLOAD_OTHER).toString()) { api, _ ->
            assertTrue(api.upload("t", session.copy(serverWalkId = UPLOAD_WALK), points).isFailure)
        }
    }

    @Test fun `finalize와 상세 GET은 기존 경로와 manifest를 유지한다`() = runBlocking {
        withServer(200, """{"analysis_state":"derived","point_count":2}""") { api, request ->
            api.finalize("t", UPLOAD_WALK, WalkFinalizeManifest(2, 1)).getOrThrow()
            assertEquals("POST /app/walks/$UPLOAD_WALK/finalize HTTP/1.1", request.get().line)
            assertEquals(2, request.get().body.getInt("expected_point_count"))
            assertEquals(1, request.get().body.getInt("terminal_client_seq"))
        }
        withServer(200, legacyUpload(points).toString()) { api, request ->
            assertEquals(points, api.detail("t", UPLOAD_WALK).getOrThrow().fixes)
            assertEquals("GET /app/walks/$UPLOAD_WALK HTTP/1.1", request.get().line)
        }
    }

    private data class Request(val line: String, val headers: List<String>, val body: JSONObject)

    /** 한 응답 뒤 listener도 닫는다. 숨은 재발송은 접속 실패로 드러난다. */
    private suspend fun withServer(
        status: Int,
        response: String,
        test: suspend (WalkHttpApi, AtomicReference<Request>) -> Unit,
    ) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val request = AtomicReference<Request>()
            val failure = AtomicReference<Throwable>()
            val receiver = thread(isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        server.close()
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                        val first = input.readLine()
                        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                        val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }
                            ?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val body = CharArray(length)
                        var offset = 0
                        while (offset < length) {
                            val read = input.read(body, offset, length - offset)
                            check(read > 0)
                            offset += read
                        }
                        request.set(Request(first, headers, if (length == 0) JSONObject() else JSONObject(String(body))))
                        val bytes = response.toByteArray(Charsets.UTF_8)
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(bytes)
                        output.flush()
                    }
                } catch (e: Throwable) { failure.set(e) }
            }
            try { test(WalkHttpApi("http://127.0.0.1:${server.localPort}"), request) }
            finally {
                server.close()
                receiver.join(5_000)
                check(!receiver.isAlive) { "HTTP 대역이 종료되지 않았습니다." }
                failure.get()?.let { throw AssertionError("HTTP 대역 실패", it) }
            }
        }
    }
}
