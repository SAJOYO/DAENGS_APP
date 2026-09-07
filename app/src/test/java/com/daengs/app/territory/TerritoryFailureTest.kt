package com.daengs.app.territory

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class TerritoryFailureTest {
    @Test
    fun `malformed server json is not described as an unsupported location`() {
        assertEquals(
            TerritoryFailure.InvalidResponse,
            SerializationException("bad json").toTerritoryFailure(),
        )
    }
}
