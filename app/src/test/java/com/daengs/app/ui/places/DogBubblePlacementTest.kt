package com.daengs.app.ui.places

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class DogBubblePlacementTest {
    @Test fun staysAboveFixedDogBeforeAndAfterKeyboardResizesWindow() {
        listOf(844, 420).forEach { height ->
            val anchor = IntRect(256, height - 88, 304, height - 40)
            val bubble = placeDogBubble(anchor, IntSize(320, height), IntSize(280, 180), 8)
            assertEquals(anchor.top, bubble.offset.y + 180)
            assertEquals(anchor.right, bubble.offset.x + 280)
            assertEquals(anchor.center.x.toFloat(), bubble.offset.x + bubble.tailX, .01f)
            assertTrue(bubble.offset.y >= 8)
        }
    }

    @Test fun edgePlacementClampsToWindowAndKeepsTailAtDog() {
        val first = placeDogBubble(IntRect(5, 100, 53, 148), IntSize(320, 720), IntSize(280, 200), 8)
        val moved = placeDogBubble(IntRect(267, 100, 315, 148), IntSize(320, 720), IntSize(280, 200), 8)
        assertEquals(8, first.offset.x)
        assertEquals(32, moved.offset.x)
        assertEquals(29f, first.offset.x + first.tailX, .01f)
        assertEquals(291f, moved.offset.x + moved.tailX, .01f)
        assertEquals(8, first.offset.y)
        assertEquals(8, moved.offset.y)
    }
}
