package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AspectRatioOption
import com.example.model.ClipKind
import com.example.model.TrackType
import com.example.model.formatTimecode
import com.example.ui.components.ExportDialog
import com.example.ui.components.TimelineSection
import com.example.ui.components.ToolDockSection
import com.example.ui.components.VideoPreviewStage
import com.example.ui.theme.AudioViolet
import com.example.ui.theme.LayerCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.VelocityCyan
import com.example.viewmodel.EditorViewModel

@Composable
fun VelocityCutEditorScreen(
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingVideoKind by remember { mutableStateOf(ClipKind.MAIN_VIDEO) }
    var pendingTrackIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    // Zero-permission Photo Picker for Videos (Main & Overlay Layers)
    val videoPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importMedia(uri, pendingVideoKind, pendingTrackIndex)
        }
    }

    // Storage Access Framework fallback for browsing video files directly
    val videoDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importMedia(uri, pendingVideoKind, pendingTrackIndex)
        }
    }

    // Storage Access Framework picker for Audio / Sound / Music files
    val audioDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importMedia(uri, ClipKind.AUDIO, pendingTrackIndex)
        }
    }

    fun launchVideoPicker(kind: ClipKind, trackIdx: Int) {
        pendingVideoKind = kind
        pendingTrackIndex = trackIdx
        videoPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    fun launchVideoFileBrowser(kind: ClipKind, trackIdx: Int) {
        pendingVideoKind = kind
        pendingTrackIndex = trackIdx
        videoDocumentPicker.launch(arrayOf("video/*"))
    }

    fun launchAudioPicker(trackIdx: Int) {
        pendingTrackIndex = trackIdx
        audioDocumentPicker.launch(arrayOf("audio/*"))
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 720.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Top Studio Header Bar
                StudioTopBar(
                    isLoadingMedia = uiState.isLoadingMedia,
                    hasClips = uiState.clips.isNotEmpty(),
                    aspectRatio = uiState.aspectRatio,
                    onCycleAspectRatio = {
                        val entries = AspectRatioOption.entries
                        val next = entries[(entries.indexOf(uiState.aspectRatio) + 1) % entries.size]
                        viewModel.setAspectRatio(next)
                    },
                    onExportClick = { viewModel.exportFullProject() }
                )

                if (isWideScreen) {
                    // Adaptive Tablet / Landscape Layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                        ) {
                            VideoPreviewStage(
                                clips = uiState.clips,
                                selectedClipId = uiState.selectedClipId,
                                playheadMs = uiState.playheadMs,
                                isPlaying = uiState.isPlaying,
                                aspectRatio = uiState.aspectRatio,
                                onSelectClip = viewModel::selectClip,
                                onUpdateOverlayTransform = viewModel::updateOverlayTransform,
                                onImportMainVideo = { launchVideoPicker(ClipKind.MAIN_VIDEO, 0) },
                                onBrowseMainVideo = { launchVideoFileBrowser(ClipKind.MAIN_VIDEO, 0) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                            TransportControlBar(
                                isPlaying = uiState.isPlaying,
                                playheadMs = uiState.playheadMs,
                                totalDurationMs = uiState.totalDurationMs,
                                canUndo = uiState.canUndo,
                                canRedo = uiState.canRedo,
                                hasClips = uiState.clips.isNotEmpty(),
                                onTogglePlay = viewModel::togglePlayPause,
                                onUndo = viewModel::undo,
                                onRedo = viewModel::redo,
                                onAddLayer = { launchVideoPicker(ClipKind.OVERLAY_VIDEO, 1) },
                                onAddSound = { launchAudioPicker(3) }
                            )
                        }

                        ToolDockSection(
                            activeTool = uiState.activeTool,
                            selectedClip = uiState.selectedClip,
                            onSelectTool = viewModel::selectTool,
                            onApplySpeedPass = viewModel::applySpeedPass,
                            onRepeatLastSpeed = viewModel::repeatLastSpeedPass,
                            onRemoveSpeedPass = viewModel::removeSpeedPass,
                            onResetSpeed = viewModel::resetSpeedPasses,
                            onBakeAndRepeat = viewModel::bakeAndRepeatClip,
                            onTogglePreservePitch = { id, keep ->
                                viewModel.updateClipSound(id, preservePitch = keep)
                            },
                            onAddOverlayVideo = { launchVideoPicker(ClipKind.OVERLAY_VIDEO, 1) },
                            onUpdateOverlayTransform = viewModel::updateOverlayTransform,
                            onAddAudioClip = { launchAudioPicker(3) },
                            onExtractAudio = viewModel::extractAudioFromVideo,
                            onUpdateSound = { id, vol, mute, fadeIn, fadeOut, fx ->
                                viewModel.updateClipSound(
                                    clipId = id,
                                    volume = vol,
                                    isMuted = mute,
                                    fadeInMs = fadeIn,
                                    fadeOutMs = fadeOut,
                                    audioFx = fx
                                )
                            },
                            onSplitClip = viewModel::splitClipAtPlayhead,
                            onDuplicateClip = viewModel::duplicateClip,
                            onResetTrim = { id ->
                                val c = uiState.clips.firstOrNull { it.id == id }
                                if (c != null) viewModel.trimClip(id, 0L, c.sourceDurationMs)
                            },
                            onDeleteClip = viewModel::deleteClip,
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                        )
                    }

                    TimelineSection(
                        clips = uiState.clips,
                        selectedClipId = uiState.selectedClipId,
                        playheadMs = uiState.playheadMs,
                        totalDurationMs = uiState.totalDurationMs,
                        pixelsPerSecond = uiState.pixelsPerSecond,
                        snapEnabled = uiState.snapEnabled,
                        onSeekTo = viewModel::seekTo,
                        onSelectClip = viewModel::selectClip,
                        onBeginDragGesture = viewModel::beginDragGestureSnapshot,
                        onMoveClip = viewModel::moveClip,
                        onTrimClip = viewModel::trimClip,
                        onZoomChange = viewModel::adjustZoom,
                        onToggleSnap = viewModel::toggleSnap,
                        onAddClipToTrack = { track ->
                            when (track.type) {
                                TrackType.MAIN_VIDEO -> launchVideoPicker(ClipKind.MAIN_VIDEO, track.index)
                                TrackType.OVERLAY_VIDEO -> launchVideoPicker(ClipKind.OVERLAY_VIDEO, track.index)
                                TrackType.AUDIO -> launchAudioPicker(track.index)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                } else {
                    // Mobile Portrait Studio Layout
                    VideoPreviewStage(
                        clips = uiState.clips,
                        selectedClipId = uiState.selectedClipId,
                        playheadMs = uiState.playheadMs,
                        isPlaying = uiState.isPlaying,
                        aspectRatio = uiState.aspectRatio,
                        onSelectClip = viewModel::selectClip,
                        onUpdateOverlayTransform = viewModel::updateOverlayTransform,
                        onImportMainVideo = { launchVideoPicker(ClipKind.MAIN_VIDEO, 0) },
                        onBrowseMainVideo = { launchVideoFileBrowser(ClipKind.MAIN_VIDEO, 0) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f)
                    )

                    TransportControlBar(
                        isPlaying = uiState.isPlaying,
                        playheadMs = uiState.playheadMs,
                        totalDurationMs = uiState.totalDurationMs,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        hasClips = uiState.clips.isNotEmpty(),
                        onTogglePlay = viewModel::togglePlayPause,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onAddLayer = { launchVideoPicker(ClipKind.OVERLAY_VIDEO, 1) },
                        onAddSound = { launchAudioPicker(3) }
                    )

                    TimelineSection(
                        clips = uiState.clips,
                        selectedClipId = uiState.selectedClipId,
                        playheadMs = uiState.playheadMs,
                        totalDurationMs = uiState.totalDurationMs,
                        pixelsPerSecond = uiState.pixelsPerSecond,
                        snapEnabled = uiState.snapEnabled,
                        onSeekTo = viewModel::seekTo,
                        onSelectClip = viewModel::selectClip,
                        onBeginDragGesture = viewModel::beginDragGestureSnapshot,
                        onMoveClip = viewModel::moveClip,
                        onTrimClip = viewModel::trimClip,
                        onZoomChange = viewModel::adjustZoom,
                        onToggleSnap = viewModel::toggleSnap,
                        onAddClipToTrack = { track ->
                            when (track.type) {
                                TrackType.MAIN_VIDEO -> launchVideoPicker(ClipKind.MAIN_VIDEO, track.index)
                                TrackType.OVERLAY_VIDEO -> launchVideoPicker(ClipKind.OVERLAY_VIDEO, track.index)
                                TrackType.AUDIO -> launchAudioPicker(track.index)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.32f)
                    )

                    ToolDockSection(
                        activeTool = uiState.activeTool,
                        selectedClip = uiState.selectedClip,
                        onSelectTool = viewModel::selectTool,
                        onApplySpeedPass = viewModel::applySpeedPass,
                        onRepeatLastSpeed = viewModel::repeatLastSpeedPass,
                        onRemoveSpeedPass = viewModel::removeSpeedPass,
                        onResetSpeed = viewModel::resetSpeedPasses,
                        onBakeAndRepeat = viewModel::bakeAndRepeatClip,
                        onTogglePreservePitch = { id, keep ->
                            viewModel.updateClipSound(id, preservePitch = keep)
                        },
                        onAddOverlayVideo = { launchVideoPicker(ClipKind.OVERLAY_VIDEO, 1) },
                        onUpdateOverlayTransform = viewModel::updateOverlayTransform,
                        onAddAudioClip = { launchAudioPicker(3) },
                        onExtractAudio = viewModel::extractAudioFromVideo,
                        onUpdateSound = { id, vol, mute, fadeIn, fadeOut, fx ->
                            viewModel.updateClipSound(
                                clipId = id,
                                volume = vol,
                                isMuted = mute,
                                fadeInMs = fadeIn,
                                fadeOutMs = fadeOut,
                                audioFx = fx
                            )
                        },
                        onSplitClip = viewModel::splitClipAtPlayhead,
                        onDuplicateClip = viewModel::duplicateClip,
                        onResetTrim = { id ->
                            val c = uiState.clips.firstOrNull { it.id == id }
                            if (c != null) viewModel.trimClip(id, 0L, c.sourceDurationMs)
                        },
                        onDeleteClip = viewModel::deleteClip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.33f)
                    )
                }
            }
        }
    }

    ExportDialog(
        exportState = uiState.exportState,
        onCancel = viewModel::cancelExport,
        onDismiss = viewModel::dismissExportState,
        onRepeatWithExported = viewModel::reimportExportedVideoForRepeat
    )
}

@Composable
private fun StudioTopBar(
    isLoadingMedia: Boolean,
    hasClips: Boolean,
    aspectRatio: AspectRatioOption,
    onCycleAspectRatio: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VelocityCyan.copy(alpha = 0.16f))
                    .border(1.dp, VelocityCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = VelocityCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "VelocityCut",
                style = MaterialTheme.typography.titleMedium,
                color = StudioTextPrimary,
                fontWeight = FontWeight.Bold
            )
            AnimatedVisibility(visible = isLoadingMedia) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = VelocityCyan
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onCycleAspectRatio,
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.testTag("aspect_ratio_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AspectRatio,
                        contentDescription = "Canvas aspect ratio",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = aspectRatio.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextPrimary
                    )
                }
            }

            Button(
                onClick = onExportClick,
                enabled = hasClips,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VelocityCyan,
                    contentColor = ObsidianBg,
                    disabledContainerColor = StudioSurfaceElevated,
                    disabledContentColor = StudioTextMuted
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("export_video_button")
            ) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun TransportControlBar(
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    hasClips: Boolean,
    onTogglePlay: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddLayer: () -> Unit,
    onAddSound: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Play/Pause + Timecode
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onTogglePlay,
                enabled = hasClips,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (hasClips) VelocityCyan.copy(alpha = 0.16f) else StudioSurface)
                    .testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (hasClips) VelocityCyan else StudioTextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${formatTimecode(playheadMs)} / ${formatTimecode(totalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextPrimary,
                modifier = Modifier.testTag("timecode_readout")
            )
        }

        // Right: Quick Add Layer / Sound + Undo / Redo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = onAddLayer,
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, LayerCoral.copy(alpha = 0.55f)),
                modifier = Modifier.testTag("quick_add_layer_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, tint = LayerCoral, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Layer", style = MaterialTheme.typography.labelSmall, color = LayerCoral)
                }
            }

            Surface(
                onClick = onAddSound,
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AudioViolet.copy(alpha = 0.55f)),
                modifier = Modifier.testTag("quick_add_sound_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AudioViolet, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Sound", style = MaterialTheme.typography.labelSmall, color = AudioViolet)
                }
            }

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) StudioTextPrimary else StudioTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) StudioTextPrimary else StudioTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
