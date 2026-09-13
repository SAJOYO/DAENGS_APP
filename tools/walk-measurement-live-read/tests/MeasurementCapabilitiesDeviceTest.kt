package com.daengs.app.walk.sync

import android.os.Bundle
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daengs.app.auth.TokenStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** GET only, on an existing login. No token, owner, walk ID or coordinates leave the application. */
@RunWith(AndroidJUnit4::class)
class MeasurementCapabilitiesDeviceTest {
    @Test fun isolatedBootstrap() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app.javaClass == Application::class.java)
        check(InstrumentationRegistry.getInstrumentation().processName == MeasurementReadRunner.PROCESS)
    }
    @Test fun readCapabilities() = runBlocking {
        isolatedBootstrap()
        val app = ApplicationProvider.getApplicationContext<Application>()
        check(app.packageName == "com.daengs.app.devtest")
        check(java.io.File(app.applicationInfo.dataDir, "shared_prefs/daengs_session.xml").isFile) {
            "Development app login is required"
        }
        val auth = requireNotNull(TokenStore(app).load()) { "Development app login is required" }
        check(auth.accessAlive(System.currentTimeMillis())) { "Open the development app to renew its login first" }
        // Read the installed client's origin; a newly compiled BuildConfig constant may be empty.
        val base = Class.forName("com.daengs.app.BuildConfig", true, app.classLoader)
            .getField("API_BASE_URL").get(null) as String
        check(base.startsWith("http://") || base.startsWith("https://"))
        val connection = URL(base.trimEnd('/') + "/app/walks/trajectory-capabilities").openConnection() as HttpURLConnection
        val caps = try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer ${auth.accessToken}")
            check(connection.responseCode == 200) { "Capabilities HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
        val safe = JSONObject()
        listOf("persisted_measurements_supported", "measurement_versions", "motion_evidence_supported",
            "motion_precision_supported").forEach { key -> if (caps.has(key)) safe.put(key, caps.get(key)) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nAuthenticated capabilities: $safe\n")
        })
    }
}
