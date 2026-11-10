package com.panda.update.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateConfigTest {

    @Test
    fun `fromValue maps known values`() {
        assertEquals(UpdateType.FORCE_UPDATE, UpdateType.fromValue("force_update"))
        assertEquals(UpdateType.OPTIONAL_UPDATE, UpdateType.fromValue("optional_update"))
        assertEquals(UpdateType.OFF, UpdateType.fromValue("off_pop_up_update"))
    }

    @Test
    fun `fromValue is case insensitive`() {
        assertEquals(UpdateType.FORCE_UPDATE, UpdateType.fromValue("FORCE_UPDATE"))
    }

    @Test
    fun `fromValue falls back to OFF for null, empty or unknown`() {
        assertEquals(UpdateType.OFF, UpdateType.fromValue(null))
        assertEquals(UpdateType.OFF, UpdateType.fromValue(""))
        assertEquals(UpdateType.OFF, UpdateType.fromValue("something_else"))
    }

    @Test
    fun `create parses type and times show`() {
        val config = AppUpdateConfig.create("optional_update", 3L)
        assertEquals(UpdateType.OPTIONAL_UPDATE, config.updateType)
        assertEquals(3, config.optionalUpdateTimesShow)
    }

    @Test
    fun `create defaults times show to 1 when zero or negative`() {
        assertEquals(1, AppUpdateConfig.create("optional_update", 0L).optionalUpdateTimesShow)
        assertEquals(1, AppUpdateConfig.create("optional_update", -5L).optionalUpdateTimesShow)
    }

    @Test
    fun `create uses default times show when omitted`() {
        assertEquals(1, AppUpdateConfig.create("force_update").optionalUpdateTimesShow)
    }

    @Test
    fun `create with null type is OFF`() {
        assertEquals(UpdateType.OFF, AppUpdateConfig.create(null).updateType)
    }
}
