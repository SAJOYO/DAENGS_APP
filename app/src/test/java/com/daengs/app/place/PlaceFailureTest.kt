package com.daengs.app.place

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceFailureTest {
    @Test
    fun `transport and payload exceptions become stable domain failures`() {
        assertEquals(PlaceFailure.Offline, UnknownHostException("host detail").toPlaceFailure())
        assertEquals(PlaceFailure.Timeout, SocketTimeoutException("timeout detail").toPlaceFailure())
        assertEquals(
            PlaceFailure.InvalidResponse,
            SerializationException("payload detail").toPlaceFailure(),
        )
    }

    @Test
    fun `http status controls retryability without exposing response details`() {
        val server = PlaceApiException(503, "proxy trace").toPlaceFailure()
        val rejected = PlaceApiException(422, "field detail").toPlaceFailure()

        assertEquals(PlaceFailure.ServerUnavailable, server)
        assertTrue(server.retryable)
        assertEquals(PlaceFailure.RequestRejected(422), rejected)
        assertFalse(rejected.retryable)
        assertFalse(rejected.userMessage().contains("field detail"))
    }
}
