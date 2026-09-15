package com.daengs.app.map.provider.naver

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.daengs.app.walk.WalkMomentType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryActionPinArtTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `single action and scene share frame dimensions and selection never resizes it`() {
        for (density in listOf(1f, 2.75f)) {
            val scene = diaryGroupPinBitmap(1,1,false,density,detached=true)
            val normal = diaryActionPinBitmap(context,setOf(WalkMomentType.SNIFFING),1,false,density)
            val selected = diaryActionPinBitmap(context,setOf(WalkMomentType.SNIFFING),1,true,density)
            assertEquals(scene.width,normal.width); assertEquals(scene.height,normal.height)
            assertEquals(normal.width,selected.width); assertEquals(normal.height,selected.height)
            assertEquals(0,android.graphics.Color.alpha(normal.getPixel(0,normal.height-1)))
            listOf(scene,normal,selected).forEach { it.recycle() }
        }
    }

    @Test fun `mixed behaviors and large count badges fit inside their reserved bitmap`() {
        val kinds = setOf(WalkMomentType.SNIFFING,WalkMomentType.EXCRETION,WalkMomentType.BARKING)
        val normal = diaryActionPinBitmap(context,kinds,999,false,2f)
        val selected = diaryActionPinBitmap(context,kinds,999,true,2f)
        assertEquals(normal.width,selected.width); assertEquals(normal.height,selected.height)
        assertTrue(normal.width > diaryPinSize("",2f,detached=true).first)
        assertTrue(normal.height > diaryPinSize("",2f,detached=true).second)
        normal.recycle(); selected.recycle()
    }
}
