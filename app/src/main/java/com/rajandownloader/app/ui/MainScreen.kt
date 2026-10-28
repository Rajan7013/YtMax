package com.rajandownloader.app.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajandownloader.app.RajanApp
import com.rajandownloader.app.model.DownloadStage
import com.rajandownloader.app.ui.components.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val url by viewModel.url.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val fetchError by viewModel.fetchError.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val selectedContainer by viewModel.preferredContainer.collectAsStateWithLifecycle()
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val activeJob by viewModel.activeJob.collectAsStateWithLifecycle()
    val destinationDisplayName by viewModel.destinationDisplayName.collectAsStateWithLifecycle()
    val runtimeReady by viewModel.runtimeReady.collectAsStateWithLifecycle()
    val runtimeMsg by viewModel.runtimeStatusMessage.collectAsStateWithLifecycle()

    var showDiagnostics by remember { mutableStateOf(false) }
    var showCookies by remember { mutableStateOf(false) }

    // SAF directory picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}

            val docFile = DocumentFile.fromTreeUri(context, uri)
            val name = docFile?.name ?: "Selected Folder"
            viewModel.setDestination(uri.toString(), name)
        }
    }

    val isDownloading = currentProgress.stage in listOf(
        DownloadStage.DOWNLOADING,
        DownloadStage.DOWNLOADING_VIDEO,
        DownloadStage.DOWNLOADING_AUDIO,
        DownloadStage.MERGING,
        DownloadStage.SAVING
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Rajan Downloader",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCookies = true }) {
                        Icon(
                            imageVector = Icons.Default.Cookie,
                            contentDescription = "Cookies Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDiagnostics = true }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Runtime Status (if extracting)
            if (!runtimeReady) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = runtimeMsg.ifEmpty { "Initializing embedded Python & FFmpeg engine..." },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // URL Input Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "YouTube Video URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Paste from clipboard button
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                                    if (text.isNotEmpty()) {
                                        viewModel.onUrlChanged(text)
                                        Toast.makeText(context, "URL pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste")
                        }

                        // Fetch button
                        Button(
                            onClick = { viewModel.fetchMetadata() },
                            enabled = !isFetching && url.isNotBlank(),
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Info")
                            }
                        }
                    }
                }
            }

            // Storage Destination Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download Destination",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = destinationDisplayName ?: "Downloads (Default)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Change")
                    }
                }
            }

            // Error display (if any)
            if (!fetchError.isNullOrEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = fetchError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Metadata Card (when fetched)
            if (metadata != null) {
                MetadataCard(metadata = metadata!!)

                QualitySelector(
                    options = metadata!!.options,
                    selectedOption = selectedOption,
                    onOptionSelected = { viewModel.setOption(it) },
                    selectedMode = selectedMode,
                    onModeSelected = { viewModel.setMode(it) },
                    selectedContainer = selectedContainer,
                    onContainerSelected = { viewModel.setContainer(it) }
                )

                // Start Download Button
                Button(
                    onClick = { viewModel.startDownload() },
                    enabled = !isDownloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDownloading) "Download in Progress..." else "Start Download",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live Download Progress Monitor
            ProgressMonitor(
                progress = currentProgress,
                onCancel = { viewModel.cancelDownload() },
                onOpenFile = {
                    val outPath = activeJob?.outputPath
                    if (!outPath.isNullOrEmpty()) {
                        try {
                            if (outPath.startsWith("content://")) {
                                val itemUri = Uri.parse(outPath)
                                val resolvedMime = context.contentResolver.getType(itemUri) 
                                    ?: if (activeJob?.option?.mode in listOf("audio", "audio_only")) "audio/*" else "video/*"
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(itemUri, resolvedMime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open Media"))
                            } else {
                                val file = File(outPath)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, if (file.extension in listOf("mp3", "m4a", "opus")) "audio/*" else "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open Media"))
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onShareFile = {
                    val outPath = activeJob?.outputPath
                    if (!outPath.isNullOrEmpty()) {
                        try {
                            val uri = if (outPath.startsWith("content://")) Uri.parse(outPath)
                            else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(outPath))

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Downloaded Media"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Diagnostics Dialog
    if (showDiagnostics) {
        DiagnosticsDialog(
            runtimeManager = RajanApp.instance.runtimeManager,
            onDismiss = { showDiagnostics = false }
        )
    }

    // Cookies Dialog
    if (showCookies) {
        CookiesDialog(
            preferences = viewModel.preferences,
            runtimePaths = RajanApp.instance.runtimeManager.paths,
            onDismiss = { showCookies = false }
        )
    }
}

