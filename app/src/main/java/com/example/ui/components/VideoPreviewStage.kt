package com.example.ui.components

import android.content.Context
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.R
import com.example.media.StudioAudioProcessor
import com.example.model.AspectRatioOption
import com.example.model.ClipKind
import com.example.model.OverlayTransform
import com.example.model.TimelineClip
import com.example.model.formatSpeedMultiplier
import com.example.ui.theme.LayerCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.VelocityCyan
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewStage(
    clips: List<TimelineClip>,
    selectedClipId: String?,
    playheadMs: Long,
    isPlaying: Boolean,
    aspectRatio: AspectRatioOption,
    onSelectClip: (String) -> Unit,
    onUpdateOverlayTransform: (String, OverlayTransform) -> Unit,
    onImportMainVideo: () -> Unit,
    onBrowseMainVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMainClip = remember(clips, playheadMs) {
        clips.filter { it.kind == ClipKind.MAIN_VIDEO }
            .firstOrNull { playheadMs in it.timelineStartMs..it.timelineEndMs }
            ?: clips.filter { it.kind == ClipKind.MAIN_VIDEO }.minByOrNull { it.timelineStartMs }
    }

    val activeOverlays = remember(clips, playheadMs) {
        clips.filter { it.kind == ClipKind.OVERLAY_VIDEO && playheadMs in it.timelineStartMs..it.timelineEndMs }
            .sortedBy { it.trackIndex }
    }

    val activeAudios = remember(clips, playheadMs) {
        clips.filter { it.kind == ClipKind.AUDIO && playheadMs in it.timelineStartMs..it.timelineEndMs }
    }

    // Keep audio-only players alive for active audio tracks
    activeAudios.forEach { audioClip ->
        key(audioClip.id) {
            AudioTrackPreviewPlayer(
                clip = audioClip,
                playheadMs = playheadMs,
                isPlaying = isPlaying
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (clips.isEmpty()) {
            EmptyPreviewCanvas(
                onImportMainVideo = onImportMainVideo,
                onBrowseMainVideo = onBrowseMainVideo
            )
        } else {
            val targetRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            ) {
                val canvasWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val canvasHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

                if (activeMainClip != null) {
                    key(activeMainClip.id) {
                        ClipSurfacePlayer(
                            clip = activeMainClip,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onSelectClip(activeMainClip.id) }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = StudioTextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "No main video at playhead",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }
                }

                // Render layered overlay videos (PiP) with direct drag-and-drop on preview stage
                activeOverlays.forEach { overlay ->
                    key(overlay.id) {
                        val isSelected = overlay.id == selectedClipId
                        val currentTransform by rememberUpdatedState(overlay.overlayTransform)
                        val transform = overlay.overlayTransform
                        val pipWidth = maxWidth * transform.scale.coerceIn(0.15f, 1.35f)
                        val overlayRatio = (overlay.width.toFloat() / overlay.height.coerceAtLeast(1).toFloat())
                            .coerceIn(0.4f, 2.5f)
                        val pipHeight = pipWidth / overlayRatio

                        val offsetXPx = (transform.normX * canvasWidthPx * 0.5f).roundToInt()
                        val offsetYPx = (transform.normY * canvasHeightPx * 0.5f).roundToInt()

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset { IntOffset(offsetXPx, offsetYPx) }
                                .size(width = pipWidth, height = pipHeight)
                                .graphicsLayer {
                                    alpha = transform.alpha
                                    rotationZ = transform.rotationDeg
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) LayerCoral else Color.White.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .pointerInput(overlay.id, canvasWidthPx, canvasHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { onSelectClip(overlay.id) },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val dxNorm = (dragAmount.x / (canvasWidthPx * 0.5f).coerceAtLeast(1f))
                                            val dyNorm = (dragAmount.y / (canvasHeightPx * 0.5f).coerceAtLeast(1f))
                                            val next = currentTransform.copy(
                                                normX = (currentTransform.normX + dxNorm).coerceIn(-0.95f, 0.95f),
                                                normY = (currentTransform.normY + dyNorm).coerceIn(-0.95f, 0.95f)
                                            )
                                            onUpdateOverlayTransform(overlay.id, next)
                                        }
                                    )
                                }
                        ) {
                            ClipSurfacePlayer(
                                clip = overlay,
                                playheadMs = playheadMs,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Surface(
                                    color = LayerCoral.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "LAYER V${overlay.trackIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ObsidianBg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Speed & Bake Badge overlay on top-right of preview
                val badgeClip = clips.firstOrNull { it.id == selectedClipId } ?: activeMainClip
                if (badgeClip != null && (badgeClip.effectiveSpeed != 1.0 || badgeClip.bakeGeneration > 0)) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .border(1.dp, VelocityCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = VelocityCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatSpeedMultiplier(badgeClip.effectiveSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                color = VelocityCyan,
                                fontWeight = FontWeight.Bold
                            )
                            if (badgeClip.bakeGeneration > 0) {
                                Text(
                                    text = " · G${badgeClip.bakeGeneration}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LayerCoral
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPreviewCanvas(
    onImportMainVideo: () -> Unit,
    onBrowseMainVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_empty_canvas_1794618661489),
            contentDescription = "VelocityCut Studio Canvas",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.65f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.35f),
                            ObsidianBg.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeaturePill(Icons.Rounded.Speed, "2x · 5.4.0 · 7.50", VelocityCyan)
                FeaturePill(Icons.Rounded.Layers, "Video Layers", LayerCoral)
                FeaturePill(Icons.Rounded.MusicNote, "Sound FX", MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.empty_stage_title),
                style = MaterialTheme.typography.headlineSmall,
                color = StudioTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_stage_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onImportMainVideo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelocityCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_import_video_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.import_video),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onBrowseMainVideo,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_browse_files_button")
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = StudioTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Files",
                        color = StudioTextPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accent: Color
) {
    Surface(
        color = StudioSurface.copy(alpha = 0.88f),
        shape = CircleShape,
        modifier = Modifier.border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextPrimary
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ClipSurfacePlayer(
    clip: TimelineClip,
    playheadMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioProcessor = remember { StudioAudioProcessor() }

    val exoPlayer = remember(clip.id, clip.uri) {
        buildStudioExoPlayer(context, audioProcessor).apply {
            setMediaItem(MediaItem.fromUri(clip.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Update live audio processor settings
    LaunchedEffect(
        clip.volume,
        clip.isMuted,
        clip.fadeInMs,
        clip.fadeOutMs,
        clip.audioFx,
        clip.effectiveDurationMs,
        clip.effectiveSpeed
    ) {
        val extremeSpeedMute = clip.effectiveSpeed > 8.0
        audioProcessor.volume = clip.volume
        audioProcessor.isMuted = clip.isMuted || extremeSpeedMute
        audioProcessor.fadeInMs = clip.fadeInMs
        audioProcessor.fadeOutMs = clip.fadeOutMs
        audioProcessor.clipDurationUs = clip.effectiveDurationMs * 1000L
        audioProcessor.fxPreset = clip.audioFx
        exoPlayer.volume = if (clip.isMuted || extremeSpeedMute) 0f else 1f
    }

    // Sync playback speed & position with timeline playhead
    LaunchedEffect(
        isPlaying,
        playheadMs,
        clip.timelineStartMs,
        clip.trimStartMs,
        clip.effectiveSpeed,
        clip.preservePitch
    ) {
        val localTimelineMs = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
        val targetSourceMs = (clip.trimStartMs + (localTimelineMs * clip.effectiveSpeed).toLong())
            .coerceIn(clip.trimStartMs, clip.trimEndMs)

        val safePreviewSpeed = clip.effectiveSpeed.toFloat().coerceIn(0.1f, 8.0f)
        val safePitch = if (clip.preservePitch) 1.0f else safePreviewSpeed.coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(safePreviewSpeed, safePitch)

        val inWindow = playheadMs in clip.timelineStartMs..clip.timelineEndMs
        if (isPlaying && inWindow) {
            if (clip.effectiveSpeed > 8.0) {
                // High-speed preview (>8x) steps frames directly so video keeps pace with 5.4x * 7.5x stacks
                exoPlayer.pause()
                exoPlayer.seekTo(targetSourceMs)
            } else {
                val driftMs = abs(exoPlayer.currentPosition - targetSourceMs)
                if (driftMs > (220L * safePreviewSpeed).toLong().coerceAtLeast(220L)) {
                    exoPlayer.seekTo(targetSourceMs)
                }
                if (!exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause()
            if (abs(exoPlayer.currentPosition - targetSourceMs) > 45L) {
                exoPlayer.seekTo(targetSourceMs)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                exoPlayer.setVideoTextureView(this)
            }
        },
        update = { textureView ->
            exoPlayer.setVideoTextureView(textureView)
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun AudioTrackPreviewPlayer(
    clip: TimelineClip,
    playheadMs: Long,
    isPlaying: Boolean
) {
    val context = LocalContext.current
    val audioProcessor = remember { StudioAudioProcessor() }

    val exoPlayer = remember(clip.id, clip.uri) {
        buildStudioExoPlayer(context, audioProcessor).apply {
            setMediaItem(MediaItem.fromUri(clip.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(
        clip.volume,
        clip.isMuted,
        clip.fadeInMs,
        clip.fadeOutMs,
        clip.audioFx,
        clip.effectiveDurationMs,
        clip.effectiveSpeed
    ) {
        val extremeSpeedMute = clip.effectiveSpeed > 8.0
        audioProcessor.volume = clip.volume
        audioProcessor.isMuted = clip.isMuted || extremeSpeedMute
        audioProcessor.fadeInMs = clip.fadeInMs
        audioProcessor.fadeOutMs = clip.fadeOutMs
        audioProcessor.clipDurationUs = clip.effectiveDurationMs * 1000L
        audioProcessor.fxPreset = clip.audioFx
        exoPlayer.volume = if (clip.isMuted || extremeSpeedMute) 0f else 1f
    }

    LaunchedEffect(
        isPlaying,
        playheadMs,
        clip.timelineStartMs,
        clip.trimStartMs,
        clip.effectiveSpeed,
        clip.preservePitch
    ) {
        val localTimelineMs = (playheadMs - clip.timelineStartMs).coerceAtLeast(0L)
        val targetSourceMs = (clip.trimStartMs + (localTimelineMs * clip.effectiveSpeed).toLong())
            .coerceIn(clip.trimStartMs, clip.trimEndMs)

        val safeSpeed = clip.effectiveSpeed.toFloat().coerceIn(0.1f, 8.0f)
        val safePitch = if (clip.preservePitch) 1.0f else safeSpeed.coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(safeSpeed, safePitch)

        val inWindow = playheadMs in clip.timelineStartMs..clip.timelineEndMs
        if (isPlaying && inWindow && clip.effectiveSpeed <= 8.0) {
            if (abs(exoPlayer.currentPosition - targetSourceMs) > 250L) {
                exoPlayer.seekTo(targetSourceMs)
            }
            if (!exoPlayer.isPlaying) exoPlayer.play()
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause()
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildStudioExoPlayer(
    context: Context,
    audioProcessor: StudioAudioProcessor
): ExoPlayer {
    val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(audioProcessor))
                .build()
        }
    }
    return ExoPlayer.Builder(context, renderersFactory).build()
}
