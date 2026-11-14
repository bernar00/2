package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.ExportState
import com.example.ui.theme.LayerCoral
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SpeedGold
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.VelocityCyan

@Composable
fun ExportDialog(
    exportState: ExportState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRepeatWithExported: (android.net.Uri) -> Unit
) {
    if (exportState is ExportState.Idle) return
    val context = LocalContext.current

    Dialog(onDismissRequest = {
        if (exportState !is ExportState.Processing) onDismiss()
    }) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (exportState) {
                    is ExportState.Processing -> {
                        Text(
                            text = if (exportState.isBakeAndRepeat) "Baking Speed Pass" else "Exporting MP4",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = exportState.statusLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        LinearProgressIndicator(
                            progress = { (exportState.progressPercent / 100f).coerceIn(0.02f, 1f) },
                            color = if (exportState.isBakeAndRepeat) LayerCoral else VelocityCyan,
                            trackColor = ObsidianBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Text(
                            text = "${exportState.progressPercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = VelocityCyan
                        )
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = StudioTextPrimary)
                        }
                    }

                    is ExportState.Completed -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Export Complete",
                            tint = VelocityCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Video Saved to Gallery!",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Saved under Movies/VelocityCut with speed ${exportState.effectiveSpeedSummary}. Ready to share or feed back in for another speed pass!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { onRepeatWithExported(exportState.outputUri) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpeedGold,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("repeat_exported_video_button")
                        ) {
                            Icon(Icons.Rounded.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Speed Up Export Again (Repeat)")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, exportState.outputUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VelocityCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Done")
                            }
                        }
                    }

                    is ExportState.Failed -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Export Failed",
                            tint = StudioError,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Export Issue",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VelocityCyan,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }

                    ExportState.Idle -> Unit
                }
            }
        }
    }
}
