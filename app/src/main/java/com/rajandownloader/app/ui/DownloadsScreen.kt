package com.rajandownloader.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rajandownloader.app.model.DownloadJob
import com.rajandownloader.app.model.DownloadProgress
import com.rajandownloader.app.model.DownloadStage
import com.rajandownloader.app.model.DownloadedItem
import com.rajandownloader.app.player.PlayerActivity
import java.io.File

enum class DownloadTab {
    ALL, VIDEO, AUDIO, IMAGES
}

enum class DownloadSortOrder(val label: String) {
    DATE_DESC("Date: Newest First"),
    DATE_ASC("Date: Oldest First"),
    NAME_ASC("Name: A to Z"),
    NAME_DESC("Name: Z to A"),
    SIZE_DESC("Size: Largest First"),
    SIZE_ASC("Size: Smallest First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(DownloadTab.ALL) }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var currentSortOrder by rememberSaveable { mutableStateOf(DownloadSortOrder.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var inspectingItem by remember { mutableStateOf<DownloadedItem?>(null) }
    var viewingImageItem by remember { mutableStateOf<DownloadedItem?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val isDownloading = currentProgress.stage in listOf(
        DownloadStage.DOWNLOADING,
        DownloadStage.DOWNLOADING_VIDEO,
        DownloadStage.DOWNLOADING_AUDIO,
        DownloadStage.MERGING,
        DownloadStage.SAVING
    )

    val activeIsVideo = activeJob?.let { it.option.mode != "audio" && it.option.mode != "audio_only" } ?: true

    // Filter and Sort items
    val filteredHistory = remember(downloadHistory, selectedTab, searchQuery, currentSortOrder) {
        var list = when (selectedTab) {
            DownloadTab.ALL -> downloadHistory
            DownloadTab.VIDEO -> downloadHistory.filter { it.isVideo }
            DownloadTab.AUDIO -> downloadHistory.filter { !it.isVideo }
            DownloadTab.IMAGES -> downloadHistory.filter { !it.localThumbnailPath.isNullOrEmpty() }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || it.uploader.lowercase().contains(q)
            }
        }

        when (currentSortOrder) {
            DownloadSortOrder.DATE_DESC -> list.sortedByDescending { it.timestamp }
            DownloadSortOrder.DATE_ASC -> list.sortedBy { it.timestamp }
            DownloadSortOrder.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            DownloadSortOrder.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            DownloadSortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
            DownloadSortOrder.SIZE_ASC -> list.sortedBy { it.fileSizeBytes }
        }
    }

    val totalVideoCount = remember(downloadHistory) { downloadHistory.count { it.isVideo } }
    val totalAudioCount = remember(downloadHistory) { downloadHistory.count { !it.isVideo } }
    val totalImageCount = remember(downloadHistory) { downloadHistory.count { !it.localThumbnailPath.isNullOrEmpty() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Selection Toolbar (Only visible when Selection Mode is active)
        if (isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedItemIds.clear()
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close selection")
                    }

                    Text(
                        text = "${selectedItemIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Play Selected Items as a Playlist
                    if (selectedItemIds.isNotEmpty()) {
                        IconButton(onClick = {
                            val selectedItems = filteredHistory.filter { it.id in selectedItemIds }
                            if (selectedItems.isNotEmpty()) {
                                PlayerActivity.start(context, selectedItems.first(), selectedItems.map { it.id })
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Select All
                    IconButton(onClick = {
                        selectedItemIds.clear()
                        selectedItemIds.addAll(filteredHistory.map { it.id })
                    }) {
                        Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                    }

                    // Clear Selection
                    if (selectedItemIds.isNotEmpty()) {
                        IconButton(onClick = { selectedItemIds.clear() }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Selection")
                        }
                    }

                    // Delete Selected
                    IconButton(
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) {
                                showBatchDeleteConfirm = true
                            }
                        },
                        enabled = selectedItemIds.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected",
                            tint = if (selectedItemIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        } else {
            // Top Bar with Title, Search, Sort & Grid/List Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search title or channel...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchActive = false
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    )
                } else {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Grid / List View Toggle Button
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View"
                            )
                        }

                        // Search Button
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search downloads"
                            )
                        }

                        // Sort Menu Button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort downloads"
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DownloadSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.label,
                                                fontWeight = if (currentSortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                                color = if (currentSortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        trailingIcon = {
                                            if (currentSortOrder == order) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            currentSortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact Filter Tabs Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == DownloadTab.ALL,
                onClick = {
                    selectedTab = DownloadTab.ALL
                    selectedItemIds.clear()
                },
                text = {
                    Text(
                        text = "All (${downloadHistory.size})",
                        fontWeight = if (selectedTab == DownloadTab.ALL) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == DownloadTab.VIDEO,
                onClick = {
                    selectedTab = DownloadTab.VIDEO
                    selectedItemIds.clear()
                },
                text = {
                    Text(
                        text = "Video ($totalVideoCount)",
                        fontWeight = if (selectedTab == DownloadTab.VIDEO) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == DownloadTab.AUDIO,
                onClick = {
                    selectedTab = DownloadTab.AUDIO
                    selectedItemIds.clear()
                },
                text = {
                    Text(
                        text = "Audio ($totalAudioCount)",
                        fontWeight = if (selectedTab == DownloadTab.AUDIO) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == DownloadTab.IMAGES,
                onClick = {
                    selectedTab = DownloadTab.IMAGES
                    selectedItemIds.clear()
                },
                text = {
                    Text(
                        text = "Images ($totalImageCount)",
                        fontWeight = if (selectedTab == DownloadTab.IMAGES) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        val showActiveCard = isDownloading && activeJob != null && (
                selectedTab == DownloadTab.ALL ||
                        (selectedTab == DownloadTab.VIDEO && activeIsVideo) ||
                        (selectedTab == DownloadTab.AUDIO && !activeIsVideo)
                )

        if (isGridView) {
            // Grid View (2 Columns)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (showActiveCard) {
                    item(span = { GridItemSpan(2) }, key = "active_download_progress") {
                        ActiveDownloadCard(
                            job = activeJob!!,
                            progress = currentProgress,
                            onCancel = { viewModel.cancelDownload() }
                        )
                    }
                }

                if (filteredHistory.isEmpty() && !showActiveCard) {
                    item(span = { GridItemSpan(2) }, key = "empty_state") {
                        EmptyDownloadsView(tab = selectedTab)
                    }
                } else {
                    items(
                        items = filteredHistory,
                        key = { it.id }
                    ) { item ->
                        val isSelected = selectedItemIds.contains(item.id)

                        DownloadItemGridCard(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isImagesTab = selectedTab == DownloadTab.IMAGES,
                            onToggleSelect = {
                                if (isSelected) selectedItemIds.remove(item.id)
                                else selectedItemIds.add(item.id)
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedItemIds.add(item.id)
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedItemIds.remove(item.id)
                                    else selectedItemIds.add(item.id)
                                } else {
                                    if (selectedTab == DownloadTab.IMAGES) {
                                        viewingImageItem = item
                                    } else {
                                        // DIRECT 1-TAP PLAY!
                                        val playableIds = filteredHistory
                                            .filter { it.fileUriString.isNotEmpty() || it.localFilePath.isNotEmpty() }
                                            .map { it.id }
                                        PlayerActivity.start(context, item, playableIds)
                                    }
                                }
                            },
                            onViewDetails = { inspectingItem = item }
                        )
                    }
                }
            }
        } else {
            // List View (Horizontal Cards)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (showActiveCard) {
                    item(key = "active_download_progress") {
                        ActiveDownloadCard(
                            job = activeJob!!,
                            progress = currentProgress,
                            onCancel = { viewModel.cancelDownload() }
                        )
                    }
                }

                if (filteredHistory.isEmpty() && !showActiveCard) {
                    item(key = "empty_state") {
                        EmptyDownloadsView(tab = selectedTab)
                    }
                } else {
                    items(
                        items = filteredHistory,
                        key = { it.id }
                    ) { item ->
                        val isSelected = selectedItemIds.contains(item.id)

                        DownloadItemCard(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isImagesTab = selectedTab == DownloadTab.IMAGES,
                            onToggleSelect = {
                                if (isSelected) selectedItemIds.remove(item.id)
                                else selectedItemIds.add(item.id)
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedItemIds.add(item.id)
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedItemIds.remove(item.id)
                                    else selectedItemIds.add(item.id)
                                } else {
                                    if (selectedTab == DownloadTab.IMAGES) {
                                        viewingImageItem = item
                                    } else {
                                        // DIRECT 1-TAP PLAY!
                                        val playableIds = filteredHistory
                                            .filter { it.fileUriString.isNotEmpty() || it.localFilePath.isNotEmpty() }
                                            .map { it.id }
                                        PlayerActivity.start(context, item, playableIds)
                                    }
                                }
                            },
                            onPlayDirect = {
                                val playableIds = filteredHistory
                                    .filter { it.fileUriString.isNotEmpty() || it.localFilePath.isNotEmpty() }
                                    .map { it.id }
                                PlayerActivity.start(context, item, playableIds)
                            },
                            onViewDetails = { inspectingItem = item }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet for Media Details (Offline, original ratio thumbnail)
    inspectingItem?.let { item ->
        MediaDetailSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = { inspectingItem = null },
            onDelete = { deleted ->
                viewModel.deleteHistoryItems(listOf(deleted))
                inspectingItem = null
            }
        )
    }

    // In-App Fullscreen Gallery Viewer (Zoom, pan, rename, share, delete)
    viewingImageItem?.let { item ->
        ImageViewerDialog(
            item = item,
            viewModel = viewModel,
            onDismiss = { viewingImageItem = null },
            onDelete = { deleted ->
                // Deleting from image gallery only removes the thumbnail, keeping video/audio completely safe!
                viewModel.deleteThumbnailOnly(deleted)
                viewingImageItem = null
            }
        )
    }

    // Confirmation dialog for batch delete
    if (showBatchDeleteConfirm) {
        val count = selectedItemIds.size
        val isImagesTab = selectedTab == DownloadTab.IMAGES
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(if (isImagesTab) "Delete $count thumbnail${if (count > 1) "s" else ""}?" else "Delete $count item${if (count > 1) "s" else ""}?")
            },
            text = {
                Text(
                    if (isImagesTab)
                        "Are you sure you want to delete the selected thumbnails? The downloaded audio and video files will NOT be deleted."
                    else
                        "Are you sure you want to delete the selected files? This will remove them permanently from storage and history."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val itemsToDelete = downloadHistory.filter { it.id in selectedItemIds }
                        if (isImagesTab) {
                            viewModel.deleteThumbnailsOnly(itemsToDelete)
                        } else {
                            viewModel.deleteHistoryItems(itemsToDelete)
                        }
                        selectedItemIds.clear()
                        isSelectionMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    job: DownloadJob,
    progress: DownloadProgress,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (progress.stageMessage.isNotEmpty()) progress.stageMessage else "Downloading...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = job.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.percentage}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                if (progress.speedStr.isNotEmpty()) {
                    Text(
                        text = "${progress.speedStr} • ${progress.etaStr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemCard(
    item: DownloadedItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isImagesTab: Boolean = false,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onPlayDirect: () -> Unit = {},
    onViewDetails: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox in Selection Mode
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }

            // Thumbnail (Original Ratio 16:9, fit, 100% offline)
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.localThumbnailPath?.let { File(it) } ?: item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon overlay in center for video / audio
                if (!isImagesTab) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.Headphones,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Type Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isImagesTab) Color(0xFF4CAF50).copy(alpha = 0.85f)
                            else if (item.isVideo) Color(0xFFE50914).copy(alpha = 0.85f)
                            else Color(0xFF00B0FF).copy(alpha = 0.85f)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isImagesTab) "IMAGE" else if (item.isVideo) "VIDEO" else "AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration badge (only for video/audio)
                if (!isImagesTab && item.formattedDuration.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // Details Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (isImagesTab) "YouTube Original Thumbnail" else item.uploader,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isImagesTab) "JPG" else item.formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isImagesTab) "Saved in Photos" else item.fileSizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions on right (Play + Info)
            if (!isSelectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!isImagesTab) {
                        IconButton(
                            onClick = onPlayDirect,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onViewDetails,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isImagesTab) Icons.Default.ZoomIn else Icons.Default.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadItemGridCard(
    item: DownloadedItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isImagesTab: Boolean = false,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onViewDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.localThumbnailPath?.let { File(it) } ?: item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Selection Checkbox
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                    }
                }

                // Play icon overlay for media
                if (!isImagesTab) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.Headphones,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Type badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isImagesTab) Color(0xFF4CAF50).copy(alpha = 0.85f)
                            else if (item.isVideo) Color(0xFFE50914).copy(alpha = 0.85f)
                            else Color(0xFF00B0FF).copy(alpha = 0.85f)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isImagesTab) "IMAGE" else if (item.isVideo) "VIDEO" else "AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration badge
                if (!isImagesTab && item.formattedDuration.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isImagesTab) "JPG" else item.fileSizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = onViewDetails,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsView(tab: DownloadTab) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = when (tab) {
                DownloadTab.ALL -> Icons.Default.CloudDownload
                DownloadTab.VIDEO -> Icons.Default.Videocam
                DownloadTab.AUDIO -> Icons.Default.Headphones
                DownloadTab.IMAGES -> Icons.Default.Image
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Text(
            text = when (tab) {
                DownloadTab.ALL -> "No downloads yet"
                DownloadTab.VIDEO -> "No video downloads"
                DownloadTab.AUDIO -> "No audio downloads"
                DownloadTab.IMAGES -> "No thumbnail images yet"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Downloaded media will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
