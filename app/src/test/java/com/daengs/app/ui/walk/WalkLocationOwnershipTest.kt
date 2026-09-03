package com.daengs.app.ui.walk

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkLocationOwnershipTest {
    @Test
    fun `tracking service always wins location ownership`() {
        assertEquals(
            WalkLocationOwner.TRACKING_SERVICE,
            WalkLocationOwnership(
                screenActive = true,
                permissionGranted = true,
                trackingActive = true,
            ).owner(),
        )
        assertEquals(
            WalkLocationOwner.TRACKING_SERVICE,
            WalkLocationOwnership(
                screenActive = false,
                permissionGranted = false,
                trackingActive = true,
            ).owner(),
        )
    }

    @Test
    fun `screen owns location only while visible and permitted`() {
        assertEquals(
            WalkLocationOwner.SCREEN,
            WalkLocationOwnership(
                screenActive = true,
                permissionGranted = true,
                trackingActive = false,
            ).owner(),
        )
        assertEquals(
            WalkLocationOwner.NONE,
            WalkLocationOwnership(
                screenActive = false,
                permissionGranted = true,
                trackingActive = false,
            ).owner(),
        )
        assertEquals(
            WalkLocationOwner.NONE,
            WalkLocationOwnership(
                screenActive = true,
                permissionGranted = false,
                trackingActive = false,
            ).owner(),
        )
    }
}
