package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.AspectRatioOption
import com.example.model.ClipKind
import com.example.model.TimelineClip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@UnstableApi
class VideoExportEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun cancel() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
        activeTransformer?.cancel()
        activeTransformer = null
    }

    fun exportProject(
        clips: List<TimelineClip>,
        aspectRatio: AspectRatioOption,
        isBakeAndRepeat: Boolean,
        bakeTargetClipId: String?,
        onProgress: (Int, String) -> Unit,
        onComplete: (Uri, String?) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()

        val clipsToRender = if (isBakeAndRepeat && bakeTargetClipId != null) {
            val target = clips.firstOrNull { it.id == bakeTargetClipId }
            if (target != null) listOf(target.copy(timelineStartMs = 0L)) else clips
        } else {
            clips
        }

        if (clipsToRender.isEmpty()) {
            onError("Add at least one video clip before exporting.")
            return
        }

        val composition = buildComposition(clipsToRender, aspectRatio)
        if (composition == null) {
            onError("Could not build media composition from the current tracks.")
            return
        }

        val outputFile = if (isBakeAndRepeat) {
            val dir = File(context.cacheDir, "baked_clips").apply { mkdirs() }
            File(dir, "bake_${System.currentTimeMillis()}.mp4")
        } else {
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            File(dir, "VelocityCut_$stamp.mp4")
        }
        if (outputFile.exists()) outputFile.delete()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressRunnable?.let { mainHandler.removeCallbacks(it) }
                progressRunnable = null
                activeTransformer = null

                if (isBakeAndRepeat) {
                    onComplete(Uri.fromFile(outputFile), outputFile.absolutePath)
                } else {
                    val savedUri = saveToMediaStoreOrFileProvider(outputFile)
                    onComplete(savedUri, outputFile.absolutePath)
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                progressRunnable?.let { mainHandler.removeCallbacks(it) }
                progressRunnable = null
                activeTransformer = null
                onError(exportException.localizedMessage ?: "Export failed (${exportException.errorCodeName})")
            }
        }

        try {
            val transformer = Transformer.Builder(context)
                .addListener(listener)
                .build()
            activeTransformer = transformer

            onProgress(2, if (isBakeAndRepeat) "Baking speed pass..." else "Compositing tracks...")
            transformer.start(composition, outputFile.absolutePath)

            val holder = ProgressHolder()
            val runnable = object : Runnable {
                override fun run() {
                    val t = activeTransformer ?: return
                    val state = t.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val pct = holder.progress.coerceIn(0, 99)
                        val label = when {
                            isBakeAndRepeat -> "Baking speed stack ($pct%)..."
                            pct < 40 -> "Processing speed & frames ($pct%)..."
                            pct < 80 -> "Compositing video & sound layers ($pct%)..."
                            else -> "Finalizing MP4 ($pct%)..."
                        }
                        onProgress(pct, label)
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            progressRunnable = runnable
            mainHandler.postDelayed(runnable, 250L)
        } catch (t: Throwable) {
            onError(t.localizedMessage ?: "Failed to start Media3 Transformer.")
        }
    }

    private fun buildComposition(
        clips: List<TimelineClip>,
        aspectRatio: AspectRatioOption
    ): Composition? {
        val mainClips = clips.filter { it.kind == ClipKind.MAIN_VIDEO }
            .sortedBy { it.timelineStartMs }
        val overlayClips = clips.filter { it.kind == ClipKind.OVERLAY_VIDEO }
            .sortedBy { it.timelineStartMs }
        val audioClips = clips.filter { it.kind == ClipKind.AUDIO }
            .sortedBy { it.timelineStartMs }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        val videoSequenceOverlays = mutableMapOf<Int, List<TimelineClip>>()

        // Sequence 0: Main video sequence (or first overlay if main is absent)
        val primaryVideoList = mainClips.ifEmpty { overlayClips.take(1) }
        if (primaryVideoList.isNotEmpty()) {
            val seqBuilder = EditedMediaItemSequence.Builder()
            var cursorMs = 0L
            for (clip in primaryVideoList) {
                if (clip.timelineStartMs > cursorMs) {
                    seqBuilder.addGap((clip.timelineStartMs - cursorMs) * 1000L)
                }
                seqBuilder.addItem(toEditedMediaItem(clip, removeVideo = false))
                cursorMs = clip.timelineEndMs
            }
            sequences.add(seqBuilder.build())
            videoSequenceOverlays[0] = primaryVideoList
        }

        // Additional video overlay sequences (each overlay track becomes a concurrent sequence)
        val remainingOverlays = if (mainClips.isEmpty()) overlayClips.drop(1) else overlayClips
        val overlaysByTrack = remainingOverlays.groupBy { it.trackIndex }.toSortedMap()
        for ((_, trackClips) in overlaysByTrack) {
            val seqBuilder = EditedMediaItemSequence.Builder()
            var cursorMs = 0L
            for (clip in trackClips) {
                if (clip.timelineStartMs > cursorMs) {
                    seqBuilder.addGap((clip.timelineStartMs - cursorMs) * 1000L)
                }
                seqBuilder.addItem(toEditedMediaItem(clip, removeVideo = false))
                cursorMs = clip.timelineEndMs
            }
            val seqIndex = sequences.size
            sequences.add(seqBuilder.build())
            videoSequenceOverlays[seqIndex] = trackClips
        }

        // Dedicated Audio Track sequences
        val audioByTrack = audioClips.groupBy { it.trackIndex }.toSortedMap()
        for ((_, trackAudio) in audioByTrack) {
            val seqBuilder = EditedMediaItemSequence.Builder()
            var cursorMs = 0L
            for (clip in trackAudio) {
                if (clip.timelineStartMs > cursorMs) {
                    seqBuilder.addGap((clip.timelineStartMs - cursorMs) * 1000L)
                }
                seqBuilder.addItem(toEditedMediaItem(clip, removeVideo = true))
                cursorMs = clip.timelineEndMs
            }
            sequences.add(seqBuilder.build())
        }

        if (sequences.isEmpty()) return null

        val compositorSettings = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<Size>): Size {
                return Size(aspectRatio.width, aspectRatio.height)
            }

            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                if (inputId == 0) {
                    return StaticOverlaySettings.Builder().build()
                }
                val timeMs = presentationTimeUs / 1000L
                val trackClips = videoSequenceOverlays[inputId].orEmpty()
                val activeClip = trackClips.firstOrNull {
                    timeMs in it.timelineStartMs..it.timelineEndMs
                } ?: trackClips.firstOrNull()

                val transform = activeClip?.overlayTransform
                return if (transform != null) {
                    StaticOverlaySettings.Builder()
                        .setScale(transform.scale, transform.scale)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setBackgroundFrameAnchor(transform.normX, -transform.normY)
                        .setAlphaScale(transform.alpha)
                        .setRotationDegrees(-transform.rotationDeg)
                        .build()
                } else {
                    StaticOverlaySettings.Builder().build()
                }
            }
        }

        val presentationEffect = Presentation.createForWidthAndHeight(
            aspectRatio.width,
            aspectRatio.height,
            Presentation.LAYOUT_SCALE_TO_FIT
        )

        return Composition.Builder(sequences)
            .setVideoCompositorSettings(compositorSettings)
            .setEffects(Effects(emptyList(), listOf(presentationEffect)))
            .build()
    }

    private fun toEditedMediaItem(clip: TimelineClip, removeVideo: Boolean): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 100L))
                    .build()
            )
            .build()

        val speed = clip.effectiveSpeed.toFloat().coerceIn(0.05f, 1000f)
        val hasSpeedChange = abs(speed - 1.0f) > 0.005f

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        if (hasSpeedChange) {
            val speedProvider = object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = Long.MAX_VALUE
            }
            if (!removeVideo) {
                videoEffects.add(SpeedChangeEffect(speedProvider))
            }
            if (clip.hasAudioStream && !clip.isMuted) {
                if (clip.preservePitch) {
                    audioProcessors.add(SpeedChangingAudioProcessor(speedProvider))
                } else {
                    val sonic = SonicAudioProcessor().apply {
                        setSpeed(speed)
                        setPitch(speed.coerceIn(0.25f, 8.0f))
                    }
                    audioProcessors.add(sonic)
                }
            }
        }

        if (clip.hasAudioStream) {
            val studioAudio = StudioAudioProcessor(
                volume = clip.volume,
                isMuted = clip.isMuted,
                fadeInMs = clip.fadeInMs,
                fadeOutMs = clip.fadeOutMs,
                clipDurationUs = clip.effectiveDurationMs * 1000L,
                fxPreset = clip.audioFx
            )
            audioProcessors.add(studioAudio)
        }

        val builder = EditedMediaItem.Builder(mediaItem)
            .setDurationUs(clip.trimmedSourceDurationMs * 1000L)
            .setEffects(Effects(audioProcessors, videoEffects))

        if (removeVideo) {
            builder.setRemoveVideo(true)
        }
        if (!clip.hasAudioStream || clip.isMuted) {
            builder.setRemoveAudio(true)
        }
        return builder.build()
    }

    private fun saveToMediaStoreOrFileProvider(sourceFile: File): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VelocityCut")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                return uri
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            sourceFile
        )
    }
}
