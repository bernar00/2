package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import java.util.UUID
import kotlin.math.roundToLong

enum class ClipKind {
    MAIN_VIDEO,
    OVERLAY_VIDEO,
    AUDIO
}

enum class AudioFxPreset(val displayName: String, val shortTag: String) {
    NONE("Natural", "FX: Off"),
    BASS_BOOST("Bass Boost", "Bass+"),
    VOCAL_CLARITY("Vocal Crisp", "Vocal"),
    LOFI_RADIO("Lo-Fi Radio", "Lo-Fi"),
    ECHO_CHAMBER("Studio Echo", "Echo"),
    CRISP_TREBLE("Bright Air", "Air+")
}

enum class AspectRatioOption(val label: String, val width: Int, val height: Int) {
    PORTRAIT_9_16("9:16", 720, 1280),
    LANDSCAPE_16_9("16:9", 1280, 720),
    SQUARE_1_1("1:1", 1080, 1080)
}

enum class EditorToolTab(val label: String) {
    SPEED("Speed"),
    LAYER("Layers"),
    SOUND("Sound"),
    CLIP("Edit")
}

data class SpeedPreset(
    val id: String,
    val label: String,
    val subLabel: String,
    val multiplier: Double,
    val isCapCutSignature: Boolean = false
)

val DEFAULT_SPEED_PRESETS = listOf(
    SpeedPreset("2x", "2x", "Double", 2.0, isCapCutSignature = false),
    SpeedPreset("cc_540", "5.4.0", "CapCut", 5.4, isCapCutSignature = true),
    SpeedPreset("cc_750", "7.50", "CapCut", 7.5, isCapCutSignature = true),
    SpeedPreset("cc_550", "5.5.0", "CapCut", 5.5, isCapCutSignature = true),
    SpeedPreset("0.5x", "0.5x", "Half", 0.5, isCapCutSignature = false),
    SpeedPreset("10x", "10x", "Hyper", 10.0, isCapCutSignature = false)
)

data class SpeedPass(
    val id: String = UUID.randomUUID().toString(),
    val multiplier: Double,
    val label: String
)

data class OverlayTransform(
    val normX: Float = 0.42f,   // -1f..1f in NDC space (0 = center)
    val normY: Float = 0.42f,   // -1f..1f in NDC space (0 = center)
    val scale: Float = 0.40f,   // 0.15f..1.35f relative to canvas
    val alpha: Float = 1.0f,    // 0.0f..1.0f
    val rotationDeg: Float = 0f // -180f..180f
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackIndex: Int,
    val kind: ClipKind,
    val uri: Uri,
    val displayName: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val timelineStartMs: Long = 0L,
    val speedPasses: List<SpeedPass> = emptyList(),
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val preservePitch: Boolean = true,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val audioFx: AudioFxPreset = AudioFxPreset.NONE,
    val overlayTransform: OverlayTransform = OverlayTransform(),
    val thumbnails: List<Bitmap> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val hasAudioStream: Boolean = true,
    val width: Int = 1280,
    val height: Int = 720,
    val bakeGeneration: Int = 0
) {
    val trimmedSourceDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)

    val effectiveSpeed: Double
        get() = if (speedPasses.isEmpty()) 1.0 else speedPasses.fold(1.0) { acc, pass ->
            (acc * pass.multiplier).coerceIn(0.05, 100_000.0)
        }

    val effectiveDurationMs: Long
        get() = (trimmedSourceDurationMs / effectiveSpeed).roundToLong().coerceAtLeast(120L)

    val timelineEndMs: Long
        get() = timelineStartMs + effectiveDurationMs
}

enum class TrackType {
    MAIN_VIDEO,
    OVERLAY_VIDEO,
    AUDIO
}

data class TimelineTrack(
    val index: Int,
    val label: String,
    val shortCode: String,
    val type: TrackType
)

val DEFAULT_TRACKS = listOf(
    TimelineTrack(0, "Main Video", "V1", TrackType.MAIN_VIDEO),
    TimelineTrack(1, "Video Layer 1", "V2", TrackType.OVERLAY_VIDEO),
    TimelineTrack(2, "Video Layer 2", "V3", TrackType.OVERLAY_VIDEO),
    TimelineTrack(3, "Sound Track 1", "A1", TrackType.AUDIO),
    TimelineTrack(4, "Sound Track 2", "A2", TrackType.AUDIO)
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Processing(
        val progressPercent: Int,
        val isBakeAndRepeat: Boolean,
        val statusLabel: String
    ) : ExportState
    data class Completed(
        val outputUri: Uri,
        val outputPath: String?,
        val isBakeAndRepeat: Boolean,
        val effectiveSpeedSummary: String
    ) : ExportState
    data class Failed(val message: String) : ExportState
}

fun formatTimecode(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalTenths = clamped / 100L
    val tenths = totalTenths % 10L
    val totalSeconds = clamped / 1000L
    val seconds = totalSeconds % 60L
    val minutes = totalSeconds / 60L
    return String.format("%02d:%02d.%d", minutes, seconds, tenths)
}

fun formatSpeedMultiplier(speed: Double): String {
    return when {
        speed >= 100.0 -> String.format("%.0fx", speed)
        speed >= 10.0 -> String.format("%.1fx", speed)
        else -> String.format("%.2fx", speed)
    }
}
