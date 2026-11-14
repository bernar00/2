package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AudioFxPreset
import com.example.model.ClipKind
import com.example.model.DEFAULT_SPEED_PRESETS
import com.example.model.EditorToolTab
import com.example.model.OverlayTransform
import com.example.model.SpeedPreset
import com.example.model.TimelineClip
import com.example.model.formatSpeedMultiplier
import com.example.model.formatTimecode
import com.example.ui.theme.AudioViolet
import com.example.ui.theme.AudioVioletContainer
import com.example.ui.theme.LayerCoral
import com.example.ui.theme.LayerCoralContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeedGold
import com.example.ui.theme.SpeedGoldContainer
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.VelocityCyan
import com.example.ui.theme.VelocityCyanContainer
import kotlin.math.roundToInt

@Composable
fun ToolDockSection(
    activeTool: EditorToolTab,
    selectedClip: TimelineClip?,
    onSelectTool: (EditorToolTab) -> Unit,
    onApplySpeedPass: (clipId: String, multiplier: Double, label: String) -> Unit,
    onRepeatLastSpeed: (clipId: String) -> Unit,
    onRemoveSpeedPass: (clipId: String, passId: String) -> Unit,
    onResetSpeed: (clipId: String) -> Unit,
    onBakeAndRepeat: (clipId: String) -> Unit,
    onTogglePreservePitch: (clipId: String, preserve: Boolean) -> Unit,
    onAddOverlayVideo: () -> Unit,
    onUpdateOverlayTransform: (clipId: String, OverlayTransform) -> Unit,
    onAddAudioClip: () -> Unit,
    onExtractAudio: (clipId: String) -> Unit,
    onUpdateSound: (
        clipId: String,
        volume: Float?,
        isMuted: Boolean?,
        fadeInMs: Long?,
        fadeOutMs: Long?,
        audioFx: AudioFxPreset?
    ) -> Unit,
    onSplitClip: (clipId: String) -> Unit,
    onDuplicateClip: (clipId: String) -> Unit,
    onResetTrim: (clipId: String) -> Unit,
    onDeleteClip: (clipId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
    ) {
        // Active Tool Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (activeTool) {
                EditorToolTab.SPEED -> SpeedStudioPanel(
                    clip = selectedClip,
                    onApplySpeedPass = onApplySpeedPass,
                    onRepeatLastSpeed = onRepeatLastSpeed,
                    onRemoveSpeedPass = onRemoveSpeedPass,
                    onResetSpeed = onResetSpeed,
                    onBakeAndRepeat = onBakeAndRepeat,
                    onTogglePreservePitch = onTogglePreservePitch
                )
                EditorToolTab.LAYER -> LayerStudioPanel(
                    clip = selectedClip,
                    onAddOverlayVideo = onAddOverlayVideo,
                    onUpdateOverlayTransform = onUpdateOverlayTransform
                )
                EditorToolTab.SOUND -> SoundStudioPanel(
                    clip = selectedClip,
                    onAddAudioClip = onAddAudioClip,
                    onExtractAudio = onExtractAudio,
                    onUpdateSound = onUpdateSound
                )
                EditorToolTab.CLIP -> ClipEditPanel(
                    clip = selectedClip,
                    onSplitClip = onSplitClip,
                    onDuplicateClip = onDuplicateClip,
                    onResetTrim = onResetTrim,
                    onDeleteClip = onDeleteClip
                )
            }
        }

        // Bottom Tool Dock Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBg)
                .border(1.dp, StudioBorder.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorToolTab.entries.forEach { tab ->
                val isSelected = tab == activeTool
                val icon = when (tab) {
                    EditorToolTab.SPEED -> Icons.Rounded.Speed
                    EditorToolTab.LAYER -> Icons.Rounded.Layers
                    EditorToolTab.SOUND -> Icons.Rounded.GraphicEq
                    EditorToolTab.CLIP -> Icons.Rounded.Tune
                }
                val accent = when (tab) {
                    EditorToolTab.SPEED -> VelocityCyan
                    EditorToolTab.LAYER -> LayerCoral
                    EditorToolTab.SOUND -> AudioViolet
                    EditorToolTab.CLIP -> SpeedGold
                }
                Surface(
                    onClick = { onSelectTool(tab) },
                    color = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tool_tab_${tab.name.lowercase()}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) accent else StudioTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) accent else StudioTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedStudioPanel(
    clip: TimelineClip?,
    onApplySpeedPass: (String, Double, String) -> Unit,
    onRepeatLastSpeed: (String) -> Unit,
    onRemoveSpeedPass: (String, String) -> Unit,
    onResetSpeed: (String) -> Unit,
    onBakeAndRepeat: (String) -> Unit,
    onTogglePreservePitch: (String, Boolean) -> Unit
) {
    if (clip == null) {
        EmptyToolHint(
            icon = Icons.Rounded.Speed,
            title = "Select or import a clip to multiply speed",
            subtitle = "Apply 2x, CapCut 5.4.0, 7.50, 5.5.0, or any custom number and stack endlessly."
        )
        return
    }

    var customInput by remember { mutableStateOf("2.0") }
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Live Compounded Speed Readout Card
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, VelocityCyan.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TOTAL SPEED",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatSpeedMultiplier(clip.effectiveSpeed),
                            style = MaterialTheme.typography.headlineSmall,
                            color = VelocityCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("effective_speed_readout")
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${formatTimecode(clip.trimmedSourceDurationMs)} → ${formatTimecode(clip.effectiveDurationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioTextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onRepeatLastSpeed(clip.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpeedGold,
                            contentColor = ObsidianBg
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("repeat_last_speed_button")
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Repeat ×",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (clip.speedPasses.isNotEmpty()) {
                        IconButton(
                            onClick = { onResetSpeed(clip.id) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianBg)
                                .testTag("reset_speed_button")
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Reset speed to 1x",
                                tint = StudioTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // CapCut & Speed Presets Grid (Tap any preset repeatedly to compound!)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TAP TO STACK SPEED (REPEAT AS MUCH AS YOU LIKE)",
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DEFAULT_SPEED_PRESETS.forEach { preset ->
                    SpeedPresetChip(
                        preset = preset,
                        onClick = {
                            onApplySpeedPass(clip.id, preset.multiplier, preset.label)
                        }
                    )
                }
            }
        }

        // Custom Numeric Multiplier Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customInput,
                onValueChange = { raw ->
                    if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d*$"))) {
                        customInput = raw
                    }
                },
                label = { Text("Number (e.g. 2, 5.4, 7.5)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = customInput.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            onApplySpeedPass(clip.id, parsed, "${customInput}x")
                        }
                        focusManager.clearFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VelocityCyan,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary
                ),
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_speed_input")
            )

            Button(
                onClick = {
                    val parsed = customInput.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onApplySpeedPass(clip.id, parsed, "${customInput}x")
                        focusManager.clearFocus()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VelocityCyan,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("apply_custom_speed_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stack", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Stacked Pass Chain (shows every stacked multiplication pass)
        if (clip.speedPasses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "APPLIED SPEED CHAIN (${clip.speedPasses.size} PASSES)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpeedGold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    clip.speedPasses.forEachIndexed { idx, pass ->
                        Surface(
                            color = SpeedGoldContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SpeedGold.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1} ${pass.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpeedGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove pass",
                                    tint = SpeedGold,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(14.dp)
                                        .clickable { onRemoveSpeedPass(clip.id, pass.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bake & Repeat + Preserve Pitch Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (clip.kind != ClipKind.AUDIO) {
                OutlinedButton(
                    onClick = { onBakeAndRepeat(clip.id) },
                    border = BorderStroke(1.dp, LayerCoral),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("bake_and_repeat_button")
                ) {
                    Icon(
                        Icons.Rounded.Autorenew,
                        contentDescription = null,
                        tint = LayerCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bake & Repeat",
                        color = LayerCoral,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Keep Pitch",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextSecondary
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = clip.preservePitch,
                    onCheckedChange = { onTogglePreservePitch(clip.id, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = VelocityCyan
                    ),
                    modifier = Modifier.testTag("preserve_pitch_switch")
                )
            }
        }
    }
}

@Composable
private fun SpeedPresetChip(
    preset: SpeedPreset,
    onClick: () -> Unit
) {
    val accent = if (preset.isCapCutSignature) LayerCoral else VelocityCyan
    val container = if (preset.isCapCutSignature) LayerCoralContainer else VelocityCyanContainer
    Surface(
        onClick = onClick,
        color = container,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier.testTag("speed_preset_${preset.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = preset.subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun LayerStudioPanel(
    clip: TimelineClip?,
    onAddOverlayVideo: () -> Unit,
    onUpdateOverlayTransform: (String, OverlayTransform) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VIDEO LAYERS (PICTURE-IN-PICTURE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = LayerCoral,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Upload another video to stack over your main clip. Drag directly on preview!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAddOverlayVideo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LayerCoral,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("add_layer_video_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Layer", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (clip != null && clip.kind == ClipKind.OVERLAY_VIDEO) {
            val t = clip.overlayTransform
            // Position Quick Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val positions = listOf(
                    "Top-L" to Pair(-0.45f, -0.45f),
                    "Top-R" to Pair(0.45f, -0.45f),
                    "Center" to Pair(0f, 0f),
                    "Bot-L" to Pair(-0.45f, 0.45f),
                    "Bot-R" to Pair(0.45f, 0.45f)
                )
                positions.forEach { (label, coords) ->
                    Surface(
                        onClick = {
                            onUpdateOverlayTransform(
                                clip.id,
                                t.copy(normX = coords.first, normY = coords.second)
                            )
                        },
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextPrimary
                            )
                        }
                    }
                }
            }

            LabeledSlider(
                label = "Scale",
                valueText = "${(t.scale * 100).roundToInt()}%",
                value = t.scale,
                range = 0.15f..1.25f,
                accentColor = LayerCoral,
                onValueChange = { onUpdateOverlayTransform(clip.id, t.copy(scale = it)) }
            )
            LabeledSlider(
                label = "Opacity",
                valueText = "${(t.alpha * 100).roundToInt()}%",
                value = t.alpha,
                range = 0.1f..1.0f,
                accentColor = LayerCoral,
                onValueChange = { onUpdateOverlayTransform(clip.id, t.copy(alpha = it)) }
            )
            LabeledSlider(
                label = "Rotation",
                valueText = "${t.rotationDeg.roundToInt()}°",
                value = t.rotationDeg,
                range = -180f..180f,
                accentColor = LayerCoral,
                onValueChange = { onUpdateOverlayTransform(clip.id, t.copy(rotationDeg = it)) }
            )
        } else {
            Surface(
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (clip == null) "Tap 'Add Layer' to upload an overlay video."
                    else "Select a Video Layer clip on Track V2 or V3 to adjust its scale, opacity, and rotation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SoundStudioPanel(
    clip: TimelineClip?,
    onAddAudioClip: () -> Unit,
    onExtractAudio: (String) -> Unit,
    onUpdateSound: (String, Float?, Boolean?, Long?, Long?, AudioFxPreset?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddAudioClip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AudioViolet,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("add_sound_button")
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import Sound", style = MaterialTheme.typography.labelLarge)
            }

            if (clip != null && clip.kind != ClipKind.AUDIO && clip.hasAudioStream) {
                OutlinedButton(
                    onClick = { onExtractAudio(clip.id) },
                    border = BorderStroke(1.dp, AudioViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("extract_audio_button")
                ) {
                    Text(
                        text = "Extract Audio",
                        color = AudioViolet,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (clip == null) {
            EmptyToolHint(
                icon = Icons.Rounded.GraphicEq,
                title = "No clip selected for sound mixing",
                subtitle = "Import an audio track or select any video/sound clip to adjust volume, fades, and Sound FX."
            )
            return
        }

        // Mute & Volume Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onUpdateSound(clip.id, null, !clip.isMuted, null, null, null) },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (clip.isMuted) LayerCoralContainer else AudioVioletContainer)
                    .testTag("mute_toggle_button")
            ) {
                Icon(
                    imageVector = if (clip.isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Mute toggle",
                    tint = if (clip.isMuted) LayerCoral else AudioViolet
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume Boost", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                    Text(
                        text = if (clip.isMuted) "MUTED" else "${(clip.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (clip.isMuted) LayerCoral else AudioViolet
                    )
                }
                Slider(
                    value = clip.volume,
                    onValueChange = { onUpdateSound(clip.id, it, false, null, null, null) },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = AudioViolet,
                        activeTrackColor = AudioViolet
                    ),
                    modifier = Modifier.testTag("volume_slider")
                )
            }
        }

        // Fade In & Fade Out Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LabeledSlider(
                    label = "Fade In",
                    valueText = String.format("%.1fs", clip.fadeInMs / 1000f),
                    value = clip.fadeInMs / 1000f,
                    range = 0f..5f,
                    accentColor = AudioViolet,
                    onValueChange = { sec ->
                        onUpdateSound(clip.id, null, null, (sec * 1000f).toLong(), null, null)
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                LabeledSlider(
                    label = "Fade Out",
                    valueText = String.format("%.1fs", clip.fadeOutMs / 1000f),
                    value = clip.fadeOutMs / 1000f,
                    range = 0f..5f,
                    accentColor = AudioViolet,
                    onValueChange = { sec ->
                        onUpdateSound(clip.id, null, null, null, (sec * 1000f).toLong(), null)
                    }
                )
            }
        }

        // Sound FX Presets
        Text(
            text = "SOUND FX & EQ PRESETS",
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioFxPreset.entries.forEach { fx ->
                val selected = clip.audioFx == fx
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateSound(clip.id, null, null, null, null, fx) },
                    label = {
                        Text(
                            text = fx.displayName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AudioVioletContainer,
                        selectedLabelColor = AudioViolet,
                        containerColor = StudioSurfaceElevated,
                        labelColor = StudioTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = StudioBorder,
                        selectedBorderColor = AudioViolet
                    ),
                    modifier = Modifier.testTag("audio_fx_${fx.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun ClipEditPanel(
    clip: TimelineClip?,
    onSplitClip: (String) -> Unit,
    onDuplicateClip: (String) -> Unit,
    onResetTrim: (String) -> Unit,
    onDeleteClip: (String) -> Unit
) {
    if (clip == null) {
        EmptyToolHint(
            icon = Icons.Rounded.Tune,
            title = "No clip selected",
            subtitle = "Tap any clip on the timeline to split, duplicate, trim, or delete it."
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clip.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = "Trim: ${formatTimecode(clip.trimStartMs)} – ${formatTimecode(clip.trimEndMs)} · Speed: ${formatSpeedMultiplier(clip.effectiveSpeed)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClipActionButton(
                icon = Icons.Rounded.ContentCut,
                label = "Split",
                accent = VelocityCyan,
                onClick = { onSplitClip(clip.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("clip_split_button")
            )
            ClipActionButton(
                icon = Icons.Rounded.ContentCopy,
                label = "Duplicate",
                accent = SpeedGold,
                onClick = { onDuplicateClip(clip.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("clip_duplicate_button")
            )
            ClipActionButton(
                icon = Icons.Rounded.Refresh,
                label = "Reset Trim",
                accent = AudioViolet,
                onClick = { onResetTrim(clip.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("clip_reset_trim_button")
            )
            ClipActionButton(
                icon = Icons.Rounded.Delete,
                label = "Delete",
                accent = StudioError,
                onClick = { onDeleteClip(clip.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("clip_delete_button")
            )
        }
    }
}

@Composable
private fun ClipActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextPrimary
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = accentColor)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun EmptyToolHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = VelocityCyan, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
            }
        }
    }
}
