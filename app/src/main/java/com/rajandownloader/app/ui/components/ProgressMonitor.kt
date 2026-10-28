package com.rajandownloader.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rajandownloader.app.model.DownloadProgress
import com.rajandownloader.app.model.DownloadStage

@Composable
fun ProgressMonitor(
    progress: DownloadProgress,
    onCancel: () -> Unit,
    onOpenFile: (() -> Unit)? = null,
    onShareFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (progress.stage == DownloadStage.IDLE) return

    val isRunning = progress.stage in listOf(
        DownloadStage.FETCHING_METADATA,
        DownloadStage.DOWNLOADING_VIDEO,
        DownloadStage.DOWNLOADING_AUDIO,
        DownloadStage.DOWNLOADING,
        DownloadStage.MERGING,
        DownloadStage.SAVING
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (progress.stage) {
                DownloadStage.COMPLETED -> Color(0xFF1B382B)
                DownloadStage.FAILED -> Color(0xFF381B1B)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Stage Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (progress.stage) {
                    DownloadStage.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DownloadStage.FAILED, DownloadStage.CANCELLED -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = when (progress.stage) {
                        DownloadStage.FETCHING_METADATA -> "Fetching YouTube streams..."
                        DownloadStage.DOWNLOADING_VIDEO -> "Downloading video stream..."
                        DownloadStage.DOWNLOADING_AUDIO -> "Downloading audio stream..."
                        DownloadStage.DOWNLOADING -> "Downloading media..."
                        DownloadStage.MERGING -> "FFmpeg: stitching video & audio streams..."
                        DownloadStage.SAVING -> "Saving output file to destination..."
                        DownloadStage.COMPLETED -> "Download & Packaging Complete!"
                        DownloadStage.FAILED -> "Download Failed"
                        DownloadStage.CANCELLED -> "Download Cancelled"
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (progress.stage) {
                        DownloadStage.COMPLETED -> Color(0xFF00E676)
                        DownloadStage.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isRunning) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            if (isRunning) {
                val isIndeterminate = progress.stage in listOf(
                    DownloadStage.FETCHING_METADATA,
                    DownloadStage.MERGING,
                    DownloadStage.SAVING
                ) || progress.percentage <= 0

                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stats row: Percentage, MBs, Speed, ETA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${progress.percentage}%" +
                                if (progress.totalBytes > 0) " (%.1f / %.1f MB)".format(progress.downloadedMb, progress.totalMb) else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    if (progress.speedStr.isNotEmpty() || progress.etaStr.isNotEmpty()) {
                        Text(
                            text = "${progress.speedStr}${if (progress.etaStr.isNotEmpty()) " • ETA: ${progress.etaStr}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Failure or cancellation message
            if (progress.stage == DownloadStage.FAILED && progress.stageMessage.isNotEmpty()) {
                Text(
                    text = progress.stageMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Success action buttons
            if (progress.stage == DownloadStage.COMPLETED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenFile != null) {
                        Button(
                            onClick = onOpenFile,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open File")
                        }
                    }

                    if (onShareFile != null) {
                        OutlinedButton(
                            onClick = onShareFile,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}

