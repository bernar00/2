package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.media.MediaInspector
import com.example.media.VideoExportEngine
import com.example.model.AspectRatioOption
import com.example.model.AudioFxPreset
import com.example.model.ClipKind
import com.example.model.DEFAULT_TRACKS
import com.example.model.EditorToolTab
import com.example.model.ExportState
import com.example.model.OverlayTransform
import com.example.model.SpeedPass
import com.example.model.TimelineClip
import com.example.model.TrackType
import com.example.model.formatSpeedMultiplier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class EditorUiState(
    val clips: List<TimelineClip> = emptyList(),
    val selectedClipId: String? = null,
    val activeTool: EditorToolTab = EditorToolTab.SPEED,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isLoadingMedia: Boolean = false,
    val pixelsPerSecond: Float = 72f,
    val snapEnabled: Boolean = true,
    val aspectRatio: AspectRatioOption = AspectRatioOption.PORTRAIT_9_16,
    val exportState: ExportState = ExportState.Idle,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusBanner: String? = null
) {
    val selectedClip: TimelineClip?
        get() = clips.firstOrNull { it.id == selectedClipId }

    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs }?.coerceAtLeast(1000L) ?: 0L
}

@OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val exportEngine = VideoExportEngine(application.applicationContext)
    private val undoStack = ArrayDeque<List<TimelineClip>>()
    private val redoStack = ArrayDeque<List<TimelineClip>>()

    private var playbackJob: Job? = null

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun selectTool(tool: EditorToolTab) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { state ->
            val clip = state.clips.firstOrNull { it.id == clipId }
            val suggestedTool = when (clip?.kind) {
                ClipKind.OVERLAY_VIDEO -> if (state.activeTool == EditorToolTab.SOUND) EditorToolTab.LAYER else state.activeTool
                ClipKind.AUDIO -> EditorToolTab.SOUND
                else -> state.activeTool
            }
            state.copy(selectedClipId = clipId, activeTool = suggestedTool)
        }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        _uiState.update { it.copy(aspectRatio = option) }
    }

    fun adjustZoom(factor: Float) {
        _uiState.update { state ->
            val next = (state.pixelsPerSecond * factor).coerceIn(22f, 220f)
            state.copy(pixelsPerSecond = next)
        }
    }

    fun toggleSnap() {
        _uiState.update { it.copy(snapEnabled = !it.snapEnabled) }
    }

    fun seekTo(ms: Long) {
        _uiState.update { state ->
            val maxDur = state.totalDurationMs.coerceAtLeast(0L)
            state.copy(playheadMs = ms.coerceIn(0L, maxDur))
        }
    }

    fun togglePlayPause() {
        val current = _uiState.value
        if (current.clips.isEmpty()) return
        if (current.isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    private fun startPlayback() {
        playbackJob?.cancel()
        val startState = _uiState.value
        val totalDur = startState.totalDurationMs
        if (totalDur <= 0L) return

        val initialPos = if (startState.playheadMs >= totalDur - 40L) 0L else startState.playheadMs
        _uiState.update { it.copy(isPlaying = true, playheadMs = initialPos) }

        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(33L)
                val now = System.currentTimeMillis()
                val delta = (now - lastTick).coerceIn(0L, 120L)
                lastTick = now

                var shouldStop = false
                _uiState.update { state ->
                    if (!state.isPlaying) {
                        shouldStop = true
                        state
                    } else {
                        val nextPos = state.playheadMs + delta
                        val endMs = state.totalDurationMs
                        if (nextPos >= endMs) {
                            shouldStop = true
                            state.copy(playheadMs = endMs, isPlaying = false)
                        } else {
                            state.copy(playheadMs = nextPos)
                        }
                    }
                }
                if (shouldStop) break
            }
        }
    }

    fun importMedia(uri: Uri, targetKind: ClipKind, preferredTrackIndex: Int? = null) {
        pausePlayback()
        _uiState.update { it.copy(isLoadingMedia = true) }

        viewModelScope.launch {
            val inspected = MediaInspector.inspectMedia(
                context = getApplication(),
                uri = uri,
                isAudioOnly = targetKind == ClipKind.AUDIO
            )

            _uiState.update { state ->
                pushUndo(state.clips)
                val trackIdx = preferredTrackIndex ?: when (targetKind) {
                    ClipKind.MAIN_VIDEO -> 0
                    ClipKind.OVERLAY_VIDEO -> {
                        val v2Busy = state.clips.any { it.trackIndex == 1 }
                        if (v2Busy && state.clips.none { it.trackIndex == 2 }) 2 else 1
                    }
                    ClipKind.AUDIO -> {
                        val a1Busy = state.clips.any { it.trackIndex == 3 }
                        if (a1Busy && state.clips.none { it.trackIndex == 4 }) 4 else 3
                    }
                }

                val startMs = when (targetKind) {
                    ClipKind.MAIN_VIDEO -> {
                        state.clips.filter { it.trackIndex == 0 }
                            .maxOfOrNull { it.timelineEndMs } ?: 0L
                    }
                    ClipKind.OVERLAY_VIDEO, ClipKind.AUDIO -> {
                        val trackClips = state.clips.filter { it.trackIndex == trackIdx }
                        val candidate = state.playheadMs
                        val overlaps = trackClips.any {
                            candidate >= it.timelineStartMs && candidate < it.timelineEndMs
                        }
                        if (!overlaps) candidate else (trackClips.maxOfOrNull { it.timelineEndMs } ?: 0L)
                    }
                }

                val defaultTransform = if (targetKind == ClipKind.OVERLAY_VIDEO) {
                    val existingOverlays = state.clips.count { it.kind == ClipKind.OVERLAY_VIDEO }
                    when (existingOverlays % 3) {
                        0 -> OverlayTransform(normX = 0.42f, normY = -0.40f, scale = 0.38f, alpha = 1.0f)
                        1 -> OverlayTransform(normX = -0.42f, normY = 0.40f, scale = 0.36f, alpha = 0.95f)
                        else -> OverlayTransform(normX = 0f, normY = 0f, scale = 0.48f, alpha = 0.9f)
                    }
                } else {
                    OverlayTransform()
                }

                val newClip = TimelineClip(
                    trackIndex = trackIdx,
                    kind = targetKind,
                    uri = uri,
                    displayName = inspected.displayName,
                    sourceDurationMs = inspected.durationMs,
                    trimStartMs = 0L,
                    trimEndMs = inspected.durationMs,
                    timelineStartMs = startMs,
                    hasAudioStream = inspected.hasAudio,
                    width = inspected.width,
                    height = inspected.height,
                    thumbnails = inspected.thumbnails,
                    waveform = inspected.waveform,
                    overlayTransform = defaultTransform
                )

                val nextTool = when (targetKind) {
                    ClipKind.MAIN_VIDEO -> EditorToolTab.SPEED
                    ClipKind.OVERLAY_VIDEO -> EditorToolTab.LAYER
                    ClipKind.AUDIO -> EditorToolTab.SOUND
                }

                val updatedClips = state.clips + newClip
                state.copy(
                    clips = updatedClips,
                    selectedClipId = newClip.id,
                    activeTool = nextTool,
                    isLoadingMedia = false,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    statusBanner = "Added ${inspected.displayName}"
                )
            }
        }
    }

    fun applySpeedPass(clipId: String, multiplier: Double, label: String) {
        if (multiplier <= 0.0 || multiplier.isNaN() || multiplier.isInfinite()) return
        val clampedMultiplier = multiplier.coerceIn(0.05, 500.0)
        pausePlayback()

        _uiState.update { state ->
            val target = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            pushUndo(state.clips)

            val newPass = SpeedPass(
                multiplier = clampedMultiplier,
                label = label
            )
            val updatedTarget = target.copy(speedPasses = target.speedPasses + newPass)
            val updatedClips = reflowMainTrackIfNeeded(
                state.clips.map { if (it.id == clipId) updatedTarget else it },
                changedClip = updatedTarget
            )

            state.copy(
                clips = updatedClips,
                playheadMs = state.playheadMs.coerceIn(0L, updatedClips.maxOfOrNull { it.timelineEndMs } ?: 0L),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Stacked $label -> ${formatSpeedMultiplier(updatedTarget.effectiveSpeed)}"
            )
        }
    }

    fun repeatLastSpeedPass(clipId: String) {
        val clip = _uiState.value.clips.firstOrNull { it.id == clipId } ?: return
        val lastPass = clip.speedPasses.lastOrNull()
            ?: SpeedPass(multiplier = 2.0, label = "2x")
        applySpeedPass(clipId, lastPass.multiplier, lastPass.label)
    }

    fun removeSpeedPass(clipId: String, passId: String) {
        pausePlayback()
        _uiState.update { state ->
            val target = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            pushUndo(state.clips)
            val updatedTarget = target.copy(
                speedPasses = target.speedPasses.filterNot { it.id == passId }
            )
            val updatedClips = reflowMainTrackIfNeeded(
                state.clips.map { if (it.id == clipId) updatedTarget else it },
                changedClip = updatedTarget
            )
            state.copy(
                clips = updatedClips,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun resetSpeedPasses(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val target = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            if (target.speedPasses.isEmpty()) return@update state
            pushUndo(state.clips)
            val updatedTarget = target.copy(speedPasses = emptyList())
            val updatedClips = reflowMainTrackIfNeeded(
                state.clips.map { if (it.id == clipId) updatedTarget else it },
                changedClip = updatedTarget
            )
            state.copy(
                clips = updatedClips,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Speed reset to 1.00x"
            )
        }
    }

    fun beginDragGestureSnapshot() {
        pushUndo(_uiState.value.clips)
    }

    fun moveClip(clipId: String, proposedStartMs: Long, proposedTrackIndex: Int) {
        _uiState.update { state ->
            val clip = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            val targetTrack = DEFAULT_TRACKS.firstOrNull { it.index == proposedTrackIndex }
            val allowedTrackIdx = if (targetTrack != null && isTrackCompatible(clip.kind, targetTrack.type)) {
                targetTrack.index
            } else {
                clip.trackIndex
            }

            var snappedStart = proposedStartMs.coerceAtLeast(0L)
            if (state.snapEnabled) {
                val snapThresholdMs = ((12f / state.pixelsPerSecond) * 1000f).toLong().coerceIn(80L, 400L)
                val snapPoints = mutableListOf(0L, state.playheadMs)
                state.clips.filter { it.id != clipId }.forEach { other ->
                    snapPoints.add(other.timelineStartMs)
                    snapPoints.add(other.timelineEndMs)
                }
                val clipDur = clip.effectiveDurationMs
                for (pt in snapPoints) {
                    if (abs(snappedStart - pt) <= snapThresholdMs) {
                        snappedStart = pt
                        break
                    }
                    if (abs((snappedStart + clipDur) - pt) <= snapThresholdMs) {
                        snappedStart = (pt - clipDur).coerceAtLeast(0L)
                        break
                    }
                }
            }

            val updatedClips = state.clips.map {
                if (it.id == clipId) {
                    it.copy(timelineStartMs = snappedStart, trackIndex = allowedTrackIdx)
                } else {
                    it
                }
            }
            state.copy(
                clips = updatedClips,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun trimClip(clipId: String, newTrimStartMs: Long, newTrimEndMs: Long) {
        _uiState.update { state ->
            val clip = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            val safeStart = newTrimStartMs.coerceIn(0L, (clip.sourceDurationMs - 200L).coerceAtLeast(0L))
            val safeEnd = newTrimEndMs.coerceIn((safeStart + 200L).coerceAtMost(clip.sourceDurationMs), clip.sourceDurationMs)
            val updated = clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
            val updatedClips = reflowMainTrackIfNeeded(
                state.clips.map { if (it.id == clipId) updated else it },
                changedClip = updated
            )
            state.copy(
                clips = updatedClips,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun updateOverlayTransform(clipId: String, transform: OverlayTransform) {
        _uiState.update { state ->
            state.copy(
                clips = state.clips.map {
                    if (it.id == clipId) it.copy(overlayTransform = transform) else it
                }
            )
        }
    }

    fun updateClipSound(
        clipId: String,
        volume: Float? = null,
        isMuted: Boolean? = null,
        preservePitch: Boolean? = null,
        fadeInMs: Long? = null,
        fadeOutMs: Long? = null,
        audioFx: AudioFxPreset? = null
    ) {
        _uiState.update { state ->
            state.copy(
                clips = state.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            volume = volume ?: clip.volume,
                            isMuted = isMuted ?: clip.isMuted,
                            preservePitch = preservePitch ?: clip.preservePitch,
                            fadeInMs = fadeInMs ?: clip.fadeInMs,
                            fadeOutMs = fadeOutMs ?: clip.fadeOutMs,
                            audioFx = audioFx ?: clip.audioFx
                        )
                    } else {
                        clip
                    }
                }
            )
        }
    }

    fun extractAudioFromVideo(videoClipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val source = state.clips.firstOrNull { it.id == videoClipId } ?: return@update state
            if (source.kind == ClipKind.AUDIO || !source.hasAudioStream) return@update state
            pushUndo(state.clips)

            val audioTrackIdx = if (state.clips.any { it.trackIndex == 3 }) 4 else 3
            val extracted = source.copy(
                id = UUID.randomUUID().toString(),
                trackIndex = audioTrackIdx,
                kind = ClipKind.AUDIO,
                displayName = "${source.displayName.substringBeforeLast('.')} (Audio)",
                isMuted = false,
                thumbnails = emptyList()
            )
            val mutedVideo = source.copy(isMuted = true)
            val nextClips = state.clips.map { if (it.id == videoClipId) mutedVideo else it } + extracted
            state.copy(
                clips = nextClips,
                selectedClipId = extracted.id,
                activeTool = EditorToolTab.SOUND,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Extracted audio to Sound Track"
            )
        }
    }

    fun splitClipAtPlayhead(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val clip = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            val splitTimelineMs = state.playheadMs
            if (splitTimelineMs <= clip.timelineStartMs + 120L || splitTimelineMs >= clip.timelineEndMs - 120L) {
                return@update state.copy(statusBanner = "Move playhead inside the clip to split.")
            }
            pushUndo(state.clips)

            val timelineOffsetMs = splitTimelineMs - clip.timelineStartMs
            val sourceOffsetMs = (timelineOffsetMs * clip.effectiveSpeed).toLong()
            val splitSourceMs = (clip.trimStartMs + sourceOffsetMs)
                .coerceIn(clip.trimStartMs + 100L, clip.trimEndMs - 100L)

            val firstHalf = clip.copy(trimEndMs = splitSourceMs)
            val secondHalf = clip.copy(
                id = UUID.randomUUID().toString(),
                trimStartMs = splitSourceMs,
                timelineStartMs = firstHalf.timelineEndMs
            )

            val nextClips = state.clips.flatMap {
                if (it.id == clipId) listOf(firstHalf, secondHalf) else listOf(it)
            }
            state.copy(
                clips = nextClips,
                selectedClipId = secondHalf.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Split clip at ${com.example.model.formatTimecode(splitTimelineMs)}"
            )
        }
    }

    fun duplicateClip(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val clip = state.clips.firstOrNull { it.id == clipId } ?: return@update state
            pushUndo(state.clips)
            val copy = clip.copy(
                id = UUID.randomUUID().toString(),
                timelineStartMs = clip.timelineEndMs
            )
            state.copy(
                clips = state.clips + copy,
                selectedClipId = copy.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Duplicated ${clip.displayName}"
            )
        }
    }

    fun deleteClip(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            if (state.clips.none { it.id == clipId }) return@update state
            pushUndo(state.clips)
            val remaining = state.clips.filterNot { it.id == clipId }
            state.copy(
                clips = remaining,
                selectedClipId = remaining.firstOrNull()?.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Removed clip"
            )
        }
    }

    fun bakeAndRepeatClip(clipId: String) {
        val state = _uiState.value
        val clip = state.clips.firstOrNull { it.id == clipId } ?: return
        if (clip.kind == ClipKind.AUDIO) return
        pausePlayback()

        val speedSummary = formatSpeedMultiplier(clip.effectiveSpeed)
        _uiState.update {
            it.copy(
                exportState = ExportState.Processing(
                    progressPercent = 0,
                    isBakeAndRepeat = true,
                    statusLabel = "Baking $speedSummary pass..."
                )
            )
        }

        exportEngine.exportProject(
            clips = state.clips,
            aspectRatio = state.aspectRatio,
            isBakeAndRepeat = true,
            bakeTargetClipId = clip.id,
            onProgress = { pct, label ->
                _uiState.update { s ->
                    s.copy(
                        exportState = ExportState.Processing(
                            progressPercent = pct,
                            isBakeAndRepeat = true,
                            statusLabel = label
                        )
                    )
                }
            },
            onComplete = { outputUri, _ ->
                viewModelScope.launch {
                    val inspected = MediaInspector.inspectMedia(
                        context = getApplication(),
                        uri = outputUri,
                        isAudioOnly = false
                    )
                    _uiState.update { current ->
                        pushUndo(current.clips)
                        val oldClip = current.clips.firstOrNull { it.id == clipId }
                        val nextGen = (oldClip?.bakeGeneration ?: 0) + 1
                        val bakedClip = TimelineClip(
                            id = clipId,
                            trackIndex = oldClip?.trackIndex ?: 0,
                            kind = oldClip?.kind ?: ClipKind.MAIN_VIDEO,
                            uri = outputUri,
                            displayName = "${oldClip?.displayName?.substringBefore(" [Pass") ?: "Clip"} [Pass #$nextGen @ $speedSummary]",
                            sourceDurationMs = inspected.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = inspected.durationMs,
                            timelineStartMs = oldClip?.timelineStartMs ?: 0L,
                            speedPasses = emptyList(),
                            volume = 1.0f,
                            isMuted = oldClip?.isMuted ?: false,
                            preservePitch = oldClip?.preservePitch ?: true,
                            hasAudioStream = inspected.hasAudio,
                            width = inspected.width,
                            height = inspected.height,
                            thumbnails = inspected.thumbnails,
                            waveform = inspected.waveform,
                            overlayTransform = oldClip?.overlayTransform ?: OverlayTransform(),
                            bakeGeneration = nextGen
                        )
                        val updatedClips = reflowMainTrackIfNeeded(
                            current.clips.map { if (it.id == clipId) bakedClip else it },
                            changedClip = bakedClip
                        )
                        current.copy(
                            clips = updatedClips,
                            selectedClipId = bakedClip.id,
                            playheadMs = bakedClip.timelineStartMs,
                            exportState = ExportState.Idle,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            statusBanner = "Baked $speedSummary! Ready to stack speed again (Gen #$nextGen)"
                        )
                    }
                }
            },
            onError = { msg ->
                _uiState.update { it.copy(exportState = ExportState.Failed(msg)) }
            }
        )
    }

    fun exportFullProject() {
        val state = _uiState.value
        if (state.clips.isEmpty()) return
        pausePlayback()

        val selectedSpeedSummary = state.selectedClip?.let {
            formatSpeedMultiplier(it.effectiveSpeed)
        } ?: "1.00x"

        _uiState.update {
            it.copy(
                exportState = ExportState.Processing(
                    progressPercent = 0,
                    isBakeAndRepeat = false,
                    statusLabel = "Starting Media3 render..."
                )
            )
        }

        exportEngine.exportProject(
            clips = state.clips,
            aspectRatio = state.aspectRatio,
            isBakeAndRepeat = false,
            bakeTargetClipId = null,
            onProgress = { pct, label ->
                _uiState.update { s ->
                    s.copy(
                        exportState = ExportState.Processing(
                            progressPercent = pct,
                            isBakeAndRepeat = false,
                            statusLabel = label
                        )
                    )
                }
            },
            onComplete = { uri, path ->
                _uiState.update { s ->
                    s.copy(
                        exportState = ExportState.Completed(
                            outputUri = uri,
                            outputPath = path,
                            isBakeAndRepeat = false,
                            effectiveSpeedSummary = selectedSpeedSummary
                        )
                    )
                }
            },
            onError = { msg ->
                _uiState.update { it.copy(exportState = ExportState.Failed(msg)) }
            }
        )
    }

    fun reimportExportedVideoForRepeat(uri: Uri) {
        dismissExportState()
        _uiState.update { it.copy(clips = emptyList(), selectedClipId = null, playheadMs = 0L) }
        importMedia(uri, ClipKind.MAIN_VIDEO, preferredTrackIndex = 0)
    }

    fun cancelExport() {
        exportEngine.cancel()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun dismissExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        pausePlayback()
        _uiState.update { state ->
            redoStack.addLast(state.clips)
            val prev = undoStack.removeLast()
            state.copy(
                clips = prev,
                selectedClipId = prev.firstOrNull { it.id == state.selectedClipId }?.id ?: prev.firstOrNull()?.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        pausePlayback()
        _uiState.update { state ->
            undoStack.addLast(state.clips)
            val next = redoStack.removeLast()
            state.copy(
                clips = next,
                selectedClipId = next.firstOrNull { it.id == state.selectedClipId }?.id ?: next.firstOrNull()?.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    private fun pushUndo(snapshot: List<TimelineClip>) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun isTrackCompatible(kind: ClipKind, trackType: TrackType): Boolean {
        return when (kind) {
            ClipKind.MAIN_VIDEO -> trackType == TrackType.MAIN_VIDEO
            ClipKind.OVERLAY_VIDEO -> trackType == TrackType.OVERLAY_VIDEO
            ClipKind.AUDIO -> trackType == TrackType.AUDIO
        }
    }

    private fun reflowMainTrackIfNeeded(
        clips: List<TimelineClip>,
        changedClip: TimelineClip
    ): List<TimelineClip> {
        if (changedClip.kind != ClipKind.MAIN_VIDEO) return clips
        val mainSorted = clips.filter { it.kind == ClipKind.MAIN_VIDEO }.sortedBy { it.timelineStartMs }
        var cursor = 0L
        val reflowedMap = mutableMapOf<String, TimelineClip>()
        for (c in mainSorted) {
            val updated = c.copy(timelineStartMs = cursor)
            reflowedMap[c.id] = updated
            cursor = updated.timelineEndMs
        }
        return clips.map { reflowedMap[it.id] ?: it }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        exportEngine.cancel()
    }
}
