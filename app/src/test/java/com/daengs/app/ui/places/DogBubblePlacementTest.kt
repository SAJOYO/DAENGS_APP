package com.daengs.app.ui.places

import androidx.compose.ui.unit.*
import org.junit.Assert.*
import org.junit.Test

class DogBubblePlacementTest {
    @Test fun flipsAboveWhenKeyboardLeavesNoRoomBelowAndKeepsTailAtDog() {
        val anchor = IntRect(136, 316, 184, 364)
        val wide = placeDogBubble(anchor, IntSize(320, 844), IntSize(280, 180), 8, 16)
        val keyboard = placeDogBubble(anchor, IntSize(320, 420), IntSize(280, 180), 8, 16)
        assertTrue(wide.below)
        assertFalse(keyboard.below)
        assertEquals(anchor.center.x.toFloat(), wide.offset.x + wide.tailX, .01f)
        assertEquals(anchor.center.y - 16, keyboard.offset.y + 180)
    }

    @Test fun edgePlacementStaysInWindowAndRepositionsWithAvatar() {
        val first = placeDogBubble(IntRect(5, 100, 53, 148), IntSize(320, 720), IntSize(280, 200), 8, 16)
        val moved = placeDogBubble(IntRect(267, 100, 315, 148), IntSize(320, 720), IntSize(280, 200), 8, 16)
        assertEquals(8, first.offset.x)
        assertEquals(32, moved.offset.x)
        assertEquals(29f, first.offset.x + first.tailX, .01f)
        assertEquals(291f, moved.offset.x + moved.tailX, .01f)
    }
}
