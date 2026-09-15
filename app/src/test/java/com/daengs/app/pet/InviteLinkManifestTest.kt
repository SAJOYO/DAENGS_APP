package com.daengs.app.pet

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 매니페스트가 초대 링크를 어디로, 어떻게 보내는가. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteLinkManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `초대 링크는 MainActivity 로 간다`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${InviteLink.HOST}${InviteLink.PATH}#abc_DEF-123"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)

        val hits = context.packageManager.queryIntentActivities(intent, 0)

        assertEquals(listOf(MainActivity::class.java.name), hits.map { it.activityInfo.name })
    }

    /** standard 면 떠 있는 앱에 링크가 올 때마다 MainActivity 가 하나씩 더 쌓인다. */
    @Test
    fun `떠 있는 앱에 온 링크는 새 인스턴스가 아니라 onNewIntent 로 받는다`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }
}
