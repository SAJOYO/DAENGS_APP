package com.daengs.app.territory

import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TerritoryPhotoApiTest {
    @Test fun `signed PUT sends exact binary and ticket headers without app authentication`() = runBlocking {
        for (status in listOf(200, 409, 412, 302)) {
            val file = File.createTempFile("territory-upload-", ".jpg").apply { writeBytes(byteArrayOf(0, -1, 23, 34)) }
            val headers = mutableListOf<String>()
            var uploaded = byteArrayOf()
            try {
                ServerSocket(0).use { server ->
                    server.soTimeout = 5000
                    val receiver = thread {
                        server.accept().use { socket ->
                            val input = socket.getInputStream()
                            val line = StringBuilder()
                            while (true) {
                                val value = input.read()
                                if (value < 0) break
                                if (value == 10) {
                                    val text = line.toString().trimEnd('\r'); line.clear()
                                    if (text.isEmpty()) break
                                    headers += text
                                } else line.append(value.toChar())
                            }
                            val size = headers.first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                            uploaded = input.readNBytes(size)
                            socket.getOutputStream().write("HTTP/1.1 $status Test\r\nLocation: http://127.0.0.1:1/must-not-follow\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        }
                    }
                    val result = runCatching { HttpTerritoryPhotoUploader().upload("http://127.0.0.1:${server.localPort}/image?signature=keep%2Bthis",
                        mapOf("Content-Type" to "image/jpeg", "x-goog-if-generation-match" to "0"), file) }
                    receiver.join(5000)
                    assertEquals(status != 302, result.isSuccess)
                    if (status == 302) assertEquals(302, (result.exceptionOrNull() as TerritoryUploadException).status)
                }
                assertEquals("PUT /image?signature=keep%2Bthis HTTP/1.1", headers.first())
                assertFalse(headers.any { it.startsWith("Authorization:", true) || it.startsWith("Cookie:", true) })
                assertTrue(headers.any { it.equals("x-goog-if-generation-match: 0", true) })
                assertArrayEquals(file.readBytes(), uploaded)
            } finally { file.delete() }
        }
    }

    @Test fun `mismatched capture session site or invalid photo status never enters upload pipeline`() {
        val capture = """{"client_capture_id":"$CLAIM","client_session_id":"$WALK","site_id":"$SITE","captured_at":"1970-01-01T00:00:03Z"}"""
        val response = """{"attempt_id":"$DOG","client_capture_id":"$CLAIM","client_session_id":"$WALK","site_id":"$SITE","captured_at":"1970-01-01T00:00:03Z","status":"PENDING_UPLOAD","upload_url":"https://storage.invalid/photo","upload_headers":{},"expires_in_seconds":300}"""
        assertEquals(DOG, parsePhotoTicket(response, capture).photoId)
        listOf(response.replace(SITE, "$SITE-extra"), response.replace(WALK, DOG2),
            response.replace(CLAIM, DOG2), response.replace("PENDING_UPLOAD", "UNKNOWN"),
            response.replace("1970-01-01T00:00:03Z", "1970-01-01T00:00:04Z"))
            .forEach { assertTrue(runCatching { parsePhotoTicket(it, capture) }.isFailure) }
    }
}
