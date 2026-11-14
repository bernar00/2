package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.ClipKind
import com.example.model.DEFAULT_SPEED_PRESETS
import com.example.model.SpeedPass
import com.example.model.TimelineClip
import com.example.model.formatSpeedMultiplier
import com.example.model.formatTimecode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches VelocityCut`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VelocityCut", appName)
    }

    @Test
    fun `capcut and repeatable speed passes compound accurately`() {
        val presets = DEFAULT_SPEED_PRESETS.associateBy { it.label }
        assertTrue(presets.containsKey("2x"))
        assertTrue(presets.containsKey("5.4.0"))
        assertTrue(presets.containsKey("7.50"))
        assertTrue(presets.containsKey("5.5.0"))

        val clip = TimelineClip(
            trackIndex = 0,
            kind = ClipKind.MAIN_VIDEO,
            uri = Uri.parse("content://media/external/video/1"),
            displayName = "sample.mp4",
            sourceDurationMs = 81_000L,
            speedPasses = listOf(
                SpeedPass(multiplier = 2.0, label = "2x"),
                SpeedPass(multiplier = 5.4, label = "5.4.0"),
                SpeedPass(multiplier = 7.5, label = "7.50"),
                SpeedPass(multiplier = 5.5, label = "5.5.0")
            )
        )

        val expectedSpeed = 2.0 * 5.4 * 7.5 * 5.5 // 445.5x
        assertEquals(expectedSpeed, clip.effectiveSpeed, 0.001)
        assertEquals("446x", formatSpeedMultiplier(clip.effectiveSpeed))
        assertEquals(182L, clip.effectiveDurationMs)
        assertEquals("01:21.0", formatTimecode(clip.sourceDurationMs))
    }
}
