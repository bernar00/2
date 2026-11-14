package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.model.AudioFxPreset
import com.example.model.ClipKind
import com.example.model.DEFAULT_TRACKS
import com.example.model.TimelineClip
import com.example.model.TimelineTrack
import com.example.model.TrackType
import com.example.model.formatSpeedMultiplier
import com.example.model.formatTimecode
import com.example.ui.theme.AudioMint
import com.example.ui.theme.AudioMintContainer
import com.example.ui.theme.AudioViolet
import com.example.ui.theme.AudioVioletContainer
import com.example.ui.theme.LayerCoral
import com.example.ui.theme.LayerCoralContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeedGold
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.TrackLaneBg
import com.example.ui.theme.VelocityCyan
import com.example.ui.theme.VelocityCyanContainer
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val TrackHeaderWidth = 56.dp
private val TrackRowHeight = 52.dp
private val RulerHeight = 26.dp

@Composable
fun TimelineSection(
    clips: List<TimelineClip>,
    selectedClipId: String?,
    playheadMs: Long,
    totalDurationMs: Long,
    pixelsPerSecond: Float,
    snapEnabled: Boolean,
    onSeekTo: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onBeginDragGesture: () -> Unit,
    onMoveClip: (clipId: String, newStartMs: Long, newTrackIndex: Int) -> Unit,
    onTrimClip: (clipId: String, newTrimStartMs: Long, newTrimEndMs: Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleSnap: () -> Unit,
    onAddClipToTrack: (TimelineTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val pxPerSecPx = with(density) { pixelsPerSecond.dp.toPx() }
    val scrollState = rememberScrollState()
    val verticalScroll = rememberScrollState()

    val timelineSpanMs = maxOf(totalDurationMs + 5_000L, 12_000L)
    val contentWidthDp = ((timelineSpanMs / 1000f) * pixelsPerSecond).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TrackLaneBg)
            .border(1.dp, StudioBorder.copy(alpha = 0.5f))
    ) {
        // Timeline utility header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TIMELINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onToggleSnap,
                    color = if (snapEnabled) VelocityCyanContainer else StudioSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (snapEnabled) VelocityCyan else StudioBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("timeline_snap_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridOn,
                            contentDescription = "Toggle Magnetic Snap",
                            tint = if (snapEnabled) VelocityCyan else StudioTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (snapEnabled) "Snap On" else "Snap Off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snapEnabled) VelocityCyan else StudioTextMuted
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Drag clips · Trim handles",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextMuted
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { onZoomChange(0.78f) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("timeline_zoom_out")
                ) {
                    Icon(
                        Icons.Rounded.ZoomOut,
                        contentDescription = "Zoom out timeline",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onZoomChange(1.28f) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("timeline_zoom_in")
                ) {
                    Icon(
                        Icons.Rounded.ZoomIn,
                        contentDescription = "Zoom in timeline",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Tracks + Scrollable Timeline Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(verticalScroll)
        ) {
            // Fixed Left Track Headers Column
            Column(
                modifier = Modifier
                    .width(TrackHeaderWidth)
                    .background(StudioSurface)
            ) {
                // Ruler corner
                Box(
                    modifier = Modifier
                        .height(RulerHeight)
                        .fillMaxWidth()
                        .background(StudioSurface)
                        .border(0.5.dp, StudioBorder.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TRK",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextMuted
                    )
                }

                DEFAULT_TRACKS.forEach { track ->
                    val accent = trackAccentColor(track)
                    val icon = when (track.type) {
                        TrackType.MAIN_VIDEO -> Icons.Rounded.Videocam
                        TrackType.OVERLAY_VIDEO -> Icons.Rounded.Layers
                        TrackType.AUDIO -> Icons.Rounded.MusicNote
                    }
                    Box(
                        modifier = Modifier
                            .height(TrackRowHeight)
                            .fillMaxWidth()
                            .border(0.5.dp, StudioBorder.copy(alpha = 0.35f))
                            .clickable { onAddClipToTrack(track) }
                            .padding(horizontal = 4.dp)
                            .testTag("track_header_${track.shortCode.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = track.label,
                                    tint = accent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = track.shortCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add to ${track.label}",
                                tint = StudioTextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Horizontally Scrollable Multi-Track Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .height(RulerHeight + TrackRowHeight * DEFAULT_TRACKS.size)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Interactive Time Ruler (Tap or drag to scrub playhead)
                        TimeRulerBar(
                            timelineSpanMs = timelineSpanMs,
                            pixelsPerSecond = pixelsPerSecond,
                            pxPerSecPx = pxPerSecPx,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RulerHeight)
                        )

                        // Track Lanes
                        DEFAULT_TRACKS.forEach { track ->
                            val trackClips = clips.filter { it.trackIndex == track.index }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TrackRowHeight)
                                    .background(
                                        if (track.index % 2 == 0) TrackLaneBg else ObsidianBg.copy(alpha = 0.65f)
                                    )
                                    .border(0.5.dp, StudioBorder.copy(alpha = 0.25f))
                            ) {
                                if (trackClips.isEmpty()) {
                                    Text(
                                        text = when (track.type) {
                                            TrackType.MAIN_VIDEO -> "Tap V1 + to import main video"
                                            TrackType.OVERLAY_VIDEO -> "Tap + to add overlay video layer"
                                            TrackType.AUDIO -> "Tap + to add sound / music track"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioTextMuted.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(start = 12.dp)
                                    )
                                }

                                trackClips.forEach { clip ->
                                    TimelineClipBlock(
                                        clip = clip,
                                        isSelected = clip.id == selectedClipId,
                                        pixelsPerSecond = pixelsPerSecond,
                                        pxPerSecPx = pxPerSecPx,
                                        rowHeightPx = with(density) { TrackRowHeight.toPx() },
                                        onSelect = { onSelectClip(clip.id) },
                                        onBeginDrag = onBeginDragGesture,
                                        onMove = { newStartMs, newTrackIdx ->
                                            onMoveClip(clip.id, newStartMs, newTrackIdx)
                                        },
                                        onTrim = { newTrimStart, newTrimEnd ->
                                            onTrimClip(clip.id, newTrimStart, newTrimEnd)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Global Playhead Needle Line spanning Ruler + all Tracks
                    val playheadOffsetPx = ((playheadMs / 1000f) * pxPerSecPx).roundToInt()
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(playheadOffsetPx - 1, 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .zIndex(20f)
                            .background(VelocityCyan)
                    )
                    // Playhead Handle Cap on Ruler
                    Canvas(
                        modifier = Modifier
                            .offset { IntOffset(playheadOffsetPx - 8, 2) }
                            .size(16.dp)
                            .zIndex(21f)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height * 0.55f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(0f, size.height * 0.55f)
                            close()
                        }
                        drawPath(path, color = VelocityCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRulerBar(
    timelineSpanMs: Long,
    pixelsPerSecond: Float,
    pxPerSecPx: Float,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSeek by rememberUpdatedState(onSeekTo)
    Box(
        modifier = modifier
            .background(StudioSurface)
            .border(0.5.dp, StudioBorder.copy(alpha = 0.5f))
            .pointerInput(pxPerSecPx) {
                detectTapGestures { offset ->
                    val ms = ((offset.x / pxPerSecPx) * 1000f).roundToLong().coerceAtLeast(0L)
                    currentOnSeek(ms)
                }
            }
            .pointerInput(pxPerSecPx) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val ms = ((change.position.x / pxPerSecPx) * 1000f).roundToLong().coerceAtLeast(0L)
                    currentOnSeek(ms)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSeconds = (timelineSpanMs / 1000L).toInt()
            val stepSec = when {
                pixelsPerSecond >= 110f -> 1
                pixelsPerSecond >= 50f -> 2
                else -> 5
            }
            for (sec in 0..totalSeconds) {
                val x = sec * pxPerSecPx
                if (x > size.width) break
                val isMajor = sec % stepSec == 0
                drawLine(
                    color = if (isMajor) StudioTextSecondary else StudioBorder,
                    start = Offset(x, if (isMajor) size.height * 0.35f else size.height * 0.65f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMajor) 2f else 1f
                )
            }
        }
        val totalSeconds = (timelineSpanMs / 1000L).toInt()
        val stepSec = when {
            pixelsPerSecond >= 110f -> 1
            pixelsPerSecond >= 50f -> 2
            else -> 5
        }
        for (sec in 0..totalSeconds step stepSec) {
            val xDp = (sec * pixelsPerSecond).dp
            Text(
                text = "${sec}s",
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
                modifier = Modifier
                    .offset(x = xDp + 4.dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun TimelineClipBlock(
    clip: TimelineClip,
    isSelected: Boolean,
    pixelsPerSecond: Float,
    pxPerSecPx: Float,
    rowHeightPx: Float,
    onSelect: () -> Unit,
    onBeginDrag: () -> Unit,
    onMove: (newStartMs: Long, newTrackIndex: Int) -> Unit,
    onTrim: (newTrimStartMs: Long, newTrimEndMs: Long) -> Unit
) {
    val currentClip by rememberUpdatedState(clip)
    val currentPxPerSec by rememberUpdatedState(pxPerSecPx)

    var isDragging by remember { mutableStateOf(false) }
    var dragBaseStartMs by remember { mutableLongStateOf(clip.timelineStartMs) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    val widthDp = ((clip.effectiveDurationMs / 1000f) * pixelsPerSecond).dp.coerceAtLeast(42.dp)
    val xOffsetDp = ((clip.timelineStartMs / 1000f) * pixelsPerSecond).dp

    val accentColor = when (clip.kind) {
        ClipKind.MAIN_VIDEO -> VelocityCyan
        ClipKind.OVERLAY_VIDEO -> LayerCoral
        ClipKind.AUDIO -> if (clip.trackIndex == 3) AudioViolet else AudioMint
    }
    val containerColor = when (clip.kind) {
        ClipKind.MAIN_VIDEO -> VelocityCyanContainer
        ClipKind.OVERLAY_VIDEO -> LayerCoralContainer
        ClipKind.AUDIO -> if (clip.trackIndex == 3) AudioVioletContainer else AudioMintContainer
    }

    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = 4.dp)
            .width(widthDp)
            .height(TrackRowHeight - 8.dp)
            .zIndex(if (isDragging) 15f else if (isSelected) 8f else 2f)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                width = if (isSelected || isDragging) 2.dp else 1.dp,
                color = if (isSelected || isDragging) accentColor else accentColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp)
            )
            .clipToBounds()
            .pointerInput(clip.id) {
                detectTapGestures { onSelect() }
            }
            .pointerInput(clip.id) {
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                        onBeginDrag()
                        isDragging = true
                        dragBaseStartMs = currentClip.timelineStartMs
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y
                        val deltaMs = ((dragAccumX / currentPxPerSec) * 1000f).roundToLong()
                        val newStart = (dragBaseStartMs + deltaMs).coerceAtLeast(0L)
                        val trackDelta = (dragAccumY / rowHeightPx).roundToInt()
                        val newTrack = (currentClip.trackIndex + trackDelta)
                            .coerceIn(0, DEFAULT_TRACKS.lastIndex)
                        onMove(newStart, newTrack)
                    }
                )
            }
            .testTag("timeline_clip_${clip.id}")
    ) {
        // Filmstrip Thumbnails for video clips
        if (clip.kind != ClipKind.AUDIO && clip.thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                clip.thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }
            }
            // Dark scrim for legible text badges
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg.copy(alpha = 0.35f))
            )
        }

        // Audio Waveform Visualization for audio clips & video clips with sound
        if (clip.waveform.isNotEmpty() && !clip.isMuted) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (clip.kind == ClipKind.AUDIO) 26.dp else 14.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val bars = clip.waveform
                val barWidth = (size.width / bars.size.coerceAtLeast(1)).coerceAtLeast(2f)
                bars.forEachIndexed { idx, amp ->
                    val x = idx * barWidth
                    val h = (size.height * amp).coerceAtLeast(2f)
                    drawLine(
                        color = accentColor.copy(alpha = if (clip.kind == ClipKind.AUDIO) 0.9f else 0.7f),
                        start = Offset(x, size.height),
                        end = Offset(x, size.height - h),
                        strokeWidth = (barWidth * 0.65f).coerceIn(1.5f, 5f)
                    )
                }
            }
        }

        // Top info strip inside clip
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (clip.effectiveSpeed != 1.0) {
                Surface(
                    color = SpeedGold.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = formatSpeedMultiplier(clip.effectiveSpeed),
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            if (clip.isMuted) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = "Muted",
                    tint = StudioTextPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
            if (clip.audioFx != AudioFxPreset.NONE) {
                Surface(
                    color = AudioViolet,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = clip.audioFx.shortTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = ObsidianBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = clip.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Floating Drag Timecode Tooltip
        if (isDragging) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            ) {
                Text(
                    text = formatTimecode(clip.timelineStartMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Left & Right Interactive Trim Handles when Selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(accentColor)
                    .pointerInput(clip.id) {
                        var accumMs = 0L
                        detectDragGestures(
                            onDragStart = {
                                onBeginDrag()
                                accumMs = currentClip.trimStartMs
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaSourceMs = ((dragAmount.x / currentPxPerSec) * 1000f * currentClip.effectiveSpeed).roundToLong()
                                accumMs = (accumMs + deltaSourceMs).coerceIn(0L, currentClip.trimEndMs - 250L)
                                onTrim(accumMs, currentClip.trimEndMs)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(ObsidianBg, CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(accentColor)
                    .pointerInput(clip.id) {
                        var accumMs = 0L
                        detectDragGestures(
                            onDragStart = {
                                onBeginDrag()
                                accumMs = currentClip.trimEndMs
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaSourceMs = ((dragAmount.x / currentPxPerSec) * 1000f * currentClip.effectiveSpeed).roundToLong()
                                accumMs = (accumMs + deltaSourceMs).coerceIn(currentClip.trimStartMs + 250L, currentClip.sourceDurationMs)
                                onTrim(currentClip.trimStartMs, accumMs)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(ObsidianBg, CircleShape)
                )
            }
        }
    }
}

private fun trackAccentColor(track: TimelineTrack): Color {
    return when (track.type) {
        TrackType.MAIN_VIDEO -> VelocityCyan
        TrackType.OVERLAY_VIDEO -> LayerCoral
        TrackType.AUDIO -> if (track.index == 3) AudioViolet else AudioMint
    }
}
