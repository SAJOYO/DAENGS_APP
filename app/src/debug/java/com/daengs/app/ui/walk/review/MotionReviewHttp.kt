package com.daengs.app.ui.walk.review

import com.daengs.app.BuildConfig
import com.daengs.app.auth.AccountScope
import com.daengs.app.auth.SessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class MotionReviewResponse(val status: Int, val body: String, val etag: String? = null)
internal class MotionReviewHttpFailure(val status: Int) : Exception("HTTP $status")

/** Fixed-origin GETs only. The entire capture belongs to one login generation. */
internal class MotionReviewHttp(private val sessions: SessionProvider, private val scope: AccountScope) {
    fun checkScope() = check(scope.ownerId != null && sessions.accountScope.value == scope) {
        "REVIEW_ACCOUNT_CHANGED"
    }

    suspend fun get(path: String): MotionReviewResponse = withContext(Dispatchers.IO) {
        require(allowedMotionReviewPath(path))
        checkScope()
        val auth = sessions.freshSession() ?: error("REVIEW_LOGIN_REQUIRED")
        checkScope()
        check(auth.appUserId == scope.ownerId)
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer ${auth.accessToken}")
            val status = connection.responseCode
            val bytes = (if (status == 200) connection.inputStream else connection.errorStream)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 32 * 1024 * 1024) { "REVIEW_RESPONSE_LIMIT" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            checkScope()
            MotionReviewResponse(status, bytes.toString(Charsets.UTF_8), connection.getHeaderField("ETag"))
        } finally { connection.disconnect() }
    }
}

internal fun allowedMotionReviewPath(path: String): Boolean =
    path.matches(Regex("/app/walks(\\?cursor=[A-Za-z0-9%_.~-]{1,2048})?")) ||
        path in setOf("/app/walks/motion-capabilities", "/app/walks/trajectory-capabilities") ||
        path.matches(Regex("/app/walks/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" +
            "(/motion-(backup|precision)(/chunks/[0-9]{1,3})?|/motion-calculation|" +
            "/trajectory-calculation\\?version=walk-trajectory-calculation-v1)?"))

internal fun motionReviewHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
