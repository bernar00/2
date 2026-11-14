package com.example

import com.example.model.DEFAULT_SPEED_PRESETS
import com.example.model.formatSpeedMultiplier
import com.example.model.formatTimecode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun capcutPresetsHaveExpectedMultipliers() {
        val map = DEFAULT_SPEED_PRESETS.associateBy { it.label }
        assertEquals(2.0, map["2x"]!!.multiplier, 0.0001)
        assertEquals(5.4, map["5.4.0"]!!.multiplier, 0.0001)
        assertEquals(7.5, map["7.50"]!!.multiplier, 0.0001)
        assertEquals(5.5, map["5.5.0"]!!.multiplier, 0.0001)
    }

    @Test
    fun formattingHelpersProduceCleanLabels() {
        assertEquals("2.00x", formatSpeedMultiplier(2.0))
        assertEquals("10.8x", formatSpeedMultiplier(10.8))
        assertEquals("00:05.4", formatTimecode(5400L))
    }
}
