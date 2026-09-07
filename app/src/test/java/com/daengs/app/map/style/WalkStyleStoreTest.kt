package com.daengs.app.map.style

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WalkStyleStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun clear() { WalkStyleStore(context).preferences.edit().clear().commit() }
    @Test fun selectionSurvivesNewStoreAndCorruptCacheFallsBack() {
        val store = WalkStyleStore(context)
        assertEquals("pink", store.read().themeId)
        store.select("blue")
        assertEquals("blue", WalkStyleStore(context).read().themeId)
        store.preferences.edit().putString("policy", "broken").commit()
        assertEquals("blue", WalkStyleStore(context).read().themeId)
        store.select("unknown")
        assertEquals("pink", store.read().themeId)
    }
    @Test fun malformedRemoteResponseCannotReplaceUsablePolicy() {
        val store = WalkStyleStore(context)
        val before = store.read()
        assertTrue(runCatching { store.cachePolicy("{}") }.isFailure)
        assertEquals(before, store.read())
    }
}
