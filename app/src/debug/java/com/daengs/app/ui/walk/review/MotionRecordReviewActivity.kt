package com.daengs.app.ui.walk.review

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.daengs.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** ADB-only diagnostic entry point in the isolated review APK. No diary controls are changed. */
class MotionRecordReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? WalkRecordReviewApplication ?: run { finish(); return }
        val status = TextView(this).apply { text = "신규 산책 백업을 확인하고 있어요." }
        setContentView(status)
        lifecycleScope.launch {
            try {
                val scope = app.sessions.accountScope.value
                val http = MotionReviewHttp(app.sessions, scope)
                http.checkScope()
                val folder = File(noBackupFilesDir, "motion-record-review/${UUID.randomUUID()}")
                MotionRecordReviewCapture(requireNotNull(scope.ownerId), BuildConfig.VERSION_NAME, folder,
                    http::checkScope, http::get).run(intent.getStringExtra("walk_id"))
                status.text = "백업 확인 자료를 저장했어요."
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                // Error text may contain server data; only its class is shown.
                status.text = "백업 확인을 마치지 못했어요: ${error.javaClass.simpleName}"
            }
        }
    }
}
