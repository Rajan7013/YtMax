package com.rajandownloader.app.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.rajandownloader.app.download.DownloadHistoryManager
import com.rajandownloader.app.model.DownloadedItem
import com.rajandownloader.app.ui.theme.RajanDownloaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_PLAYLIST_IDS = "extra_playlist_ids"

        fun start(context: Context, item: DownloadedItem, playlistIds: List<String>? = null) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, item.id)
                putExtra(EXTRA_URI, item.fileUriString.ifEmpty { item.localFilePath })
                putExtra(EXTRA_TITLE, item.title)
                putExtra(EXTRA_IS_VIDEO, item.isVideo)
                if (!playlistIds.isNullOrEmpty()) {
                    putStringArrayListExtra(EXTRA_PLAYLIST_IDS, ArrayList(playlistIds))
                }
            }
            context.startActivity(intent)
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager
    private var sleepTimer: CountDownTimer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val initialItemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        val initialUriStr = intent.getStringExtra(EXTRA_URI) ?: ""
        val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Media Player"
        val initialIsVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, true)
        val passedPlaylistIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_IDS)

        initPlayer(initialUriStr)

        setContent {
            RajanDownloaderTheme {
                val allHistory by DownloadHistoryManager.getInstance(applicationContext).items.collectAsStateWithLifecycle()

                // Form playlist of playable media items
                val playlist = remember(allHistory, initialIsVideo, passedPlaylistIds) {
                    if (!passedPlaylistIds.isNullOrEmpty()) {
                        val idSet = passedPlaylistIds.toSet()
                        val filtered = allHistory.filter { it.id in idSet }
                        if (filtered.isNotEmpty()) filtered else allHistory
                    } else {
                        val matching = allHistory.filter {
                            it.isVideo == initialIsVideo &&
                                    (it.fileUriString.isNotEmpty() || (it.localFilePath.isNotEmpty() && File(it.localFilePath).exists()))
                        }
                        if (matching.isNotEmpty()) matching else allHistory.filter {
                            it.fileUriString.isNotEmpty() || (it.localFilePath.isNotEmpty() && File(it.localFilePath).exists())
                        }
                    }
                }

                var currentIndex by rememberSaveable(playlist) {
                    val idx = playlist.indexOfFirst { it.id == initialItemId }
                    mutableIntStateOf(if (idx >= 0) idx else 0)
                }

                val currentItem = playlist.getOrNull(currentIndex) ?: DownloadedItem(
                    id = initialItemId,
                    title = initialTitle,
                    isVideo = initialIsVideo,
                    fileUriString = initialUriStr
                )

                // Load media into player when current item changes
                LaunchedEffect(currentItem.id) {
                    val targetUri = currentItem.fileUriString.ifEmpty { currentItem.localFilePath }
                    if (targetUri.isNotEmpty()) {
                        playUri(targetUri)
                        val speed = PlayerSettings.getPlaybackSpeed(applicationContext)
                        player?.setPlaybackSpeed(speed)
                    }
                }

                // Handle auto-advance when media playback ends
                DisposableEffect(player, playlist, currentIndex) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                if (currentIndex < playlist.size - 1) {
                                    currentIndex++
                                }
                            }
                        }
                    }
                    player?.addListener(listener)
                    onDispose { player?.removeListener(listener) }
                }

                PlayerScreen(
                    item = currentItem,
                    playlistSize = playlist.size,
                    currentIndex = currentIndex,
                    hasNext = currentIndex < playlist.size - 1,
                    hasPrevious = currentIndex > 0,
                    onNext = {
                        if (currentIndex < playlist.size - 1) {
                            currentIndex++
                        }
                    },
                    onPrevious = {
                        if ((player?.currentPosition ?: 0L) > 3000L) {
                            player?.seekTo(0L)
                        } else if (currentIndex > 0) {
                            currentIndex--
                        }
                    },
                    player = player,
                    onBrightnessChange = { delta -> adjustBrightness(delta) },
                    onVolumeChange = { delta -> adjustVolume(delta) },
                    onToggleOrientation = { toggleOrientation() },
                    onSleepTimerSelected = { minutes -> startSleepTimer(minutes) },
                    onOpenExternal = {
                        val currentUri = currentItem.fileUriString.ifEmpty { currentItem.localFilePath }
                        openInExternalPlayer(currentUri, currentItem.isVideo, currentItem.title)
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun initPlayer(uriStr: String) {
        val playerInstance = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = playerInstance

        if (uriStr.isNotEmpty()) {
            playUri(uriStr)
        }
    }

    private fun playUri(uriStr: String) {
        try {
            val uri = if (uriStr.startsWith("content://")) {
                Uri.parse(uriStr)
            } else {
                val file = File(uriStr)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                } else {
                    Uri.fromFile(file)
                }
            }

            val mediaItem = MediaItem.fromUri(uri)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            val speed = PlayerSettings.getPlaybackSpeed(this)
            player?.setPlaybackSpeed(speed)
            player?.playWhenReady = true
        } catch (e: Exception) {
            Toast.makeText(this, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustBrightness(delta: Float): Float {
        val lp = window.attributes
        var current = lp.screenBrightness
        if (current < 0) current = 0.5f
        val newBrightness = (current + delta).coerceIn(0.01f, 1.0f)
        lp.screenBrightness = newBrightness
        window.attributes = lp
        return newBrightness
    }

    private fun adjustVolume(delta: Float): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = if (delta > 0) 1 else -1
        val newVol = (current + step).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        return newVol.toFloat() / max.toFloat()
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            Toast.makeText(this, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
            return
        }
        val millis = minutes * 60 * 1000L
        sleepTimer = object : CountDownTimer(millis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                player?.pause()
                Toast.makeText(applicationContext, "Sleep timer: Playback paused", Toast.LENGTH_LONG).show()
            }
        }.start()
        Toast.makeText(this, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
    }

    private fun openInExternalPlayer(uriStr: String, isVideo: Boolean, title: String) {
        try {
            val uri = if (uriStr.startsWith("content://")) {
                Uri.parse(uriStr)
            } else {
                val file = File(uriStr)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                } else {
                    Uri.fromFile(file)
                }
            }
            val mime = if (isVideo) "video/*" else "audio/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open in Player"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open external player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimer?.cancel()
        player?.release()
        player = null
    }
}

object PlayerSettings {
    private const val PREFS_NAME = "player_settings"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"

    fun getPlaybackSpeed(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    }

    fun setPlaybackSpeed(context: Context, speed: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
    }
}

enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    FIT_TO_SCREEN("FIT TO SCREEN", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    STRETCH("STRETCH", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    CROP("CROP", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ORIGINAL_100("100%", AspectRatioFrameLayout.RESIZE_MODE_FIT);

    fun next(): AspectRatioMode = when (this) {
        FIT_TO_SCREEN -> STRETCH
        STRETCH -> CROP
        CROP -> ORIGINAL_100
        ORIGINAL_100 -> FIT_TO_SCREEN
    }
}

private enum class DragMode {
    NONE, BRIGHTNESS, VOLUME, SEEK
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: DownloadedItem,
    playlistSize: Int,
    currentIndex: Int,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    player: ExoPlayer?,
    onBrightnessChange: (Float) -> Float,
    onVolumeChange: (Float) -> Float,
    onToggleOrientation: () -> Unit,
    onSleepTimerSelected: (Int) -> Unit,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isSpeed2x by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(PlayerSettings.getPlaybackSpeed(context)) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Aspect Ratio state (MX Player style: FIT TO SCREEN, STRETCH, CROP, 100%)
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT_TO_SCREEN) }
    var aspectRatioHudText by remember { mutableStateOf<String?>(null) }

    // Pinch-to-Zoom state for Video Player (1.0x to 4.0x)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomHudText by remember { mutableStateOf<String?>(null) }

    // Reset zoom when item changes
    LaunchedEffect(item.id) {
        zoomScale = 1.0f
        panOffset = Offset.Zero
    }

    val onCycleAspectRatio: () -> Unit = {
        val next = aspectRatioMode.next()
        aspectRatioMode = next
        aspectRatioHudText = next.label
    }

    // HUD overlays
    var hudText by remember { mutableStateOf<String?>(null) }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }

    // Gesture tracking state
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var seekTargetPosition by remember { mutableLongStateOf(0L) }

    // Double tap tracking
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Player state sync
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // UNIFIED GESTURE LISTENER: Left swipe=Brightness, Right swipe=Volume, Horizontal=Seek, 2-Finger=Zoom, LongPress=2X, Tap=Controls
            .pointerInput(item.id) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    val startPos = firstDown.position
                    val screenWidth = size.width
                    val screenHeight = size.height

                    var isMultiTouch = false
                    var initialDistance = 0f
                    var baseScale = zoomScale
                    var hasDragged = false
                    var speedBoostActive = false
                    var initialSeekPosition = 0L

                    // Touch & Hold for 2 seconds (1800ms) triggers 2X speed automatically
                    val holdJob: Job = coroutineScope.launch {
                        delay(1800L)
                        if (!hasDragged && !isMultiTouch) {
                            speedBoostActive = true
                            isSpeed2x = true
                            player?.setPlaybackSpeed(2.0f)
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }

                        if (activePointers.isEmpty()) {
                            holdJob.cancel()
                            // All touches released
                            if (speedBoostActive) {
                                isSpeed2x = false
                                player?.setPlaybackSpeed(playbackSpeed)
                                speedBoostActive = false
                            }
                            if (dragMode == DragMode.SEEK) {
                                player?.seekTo(seekTargetPosition)
                            }

                            // Quick tap handling (not dragged, not multi-touch)
                            if (!hasDragged && !isMultiTouch && (System.currentTimeMillis() - startTime < 350)) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 350 && (startPos - lastTapPos).getDistance() < 120) {
                                    // DOUBLE TAP
                                    lastTapTime = 0L
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1.0f
                                        panOffset = Offset.Zero
                                        zoomHudText = "100%"
                                    } else {
                                        if (startPos.x > screenWidth / 2) {
                                            player?.let { p ->
                                                val target = (p.currentPosition + 10000L).coerceAtMost(p.duration.coerceAtLeast(0L))
                                                p.seekTo(target)
                                                hudText = "+10s"
                                                hudIcon = Icons.Default.FastForward
                                            }
                                        } else {
                                            player?.let { p ->
                                                val target = (p.currentPosition - 10000L).coerceAtLeast(0L)
                                                p.seekTo(target)
                                                hudText = "-10s"
                                                hudIcon = Icons.Default.FastRewind
                                            }
                                        }
                                    }
                                } else {
                                    lastTapTime = now
                                    lastTapPos = startPos
                                    showControls = !showControls
                                }
                            }

                            dragMode = DragMode.NONE
                            break
                        }

                        if (activePointers.size >= 2) {
                            // 2-FINGER PINCH ZOOM & PAN
                            isMultiTouch = true
                            holdJob.cancel()
                            if (speedBoostActive) {
                                isSpeed2x = false
                                player?.setPlaybackSpeed(playbackSpeed)
                                speedBoostActive = false
                            }
                            val p1 = activePointers[0].position
                            val p2 = activePointers[1].position
                            val currentDist = (p1 - p2).getDistance()

                            if (initialDistance == 0f) {
                                initialDistance = currentDist
                                baseScale = zoomScale
                            } else if (initialDistance > 0f) {
                                val factor = currentDist / initialDistance
                                val newScale = (baseScale * factor).coerceIn(1.0f, 4.0f)
                                zoomScale = newScale
                                zoomHudText = "${(newScale * 100).toInt()}%"

                                if (newScale <= 1.05f) {
                                    panOffset = Offset.Zero
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } else if (activePointers.size == 1 && !isMultiTouch) {
                            // 1-FINGER GESTURE: Left vertical=Brightness, Right vertical=Volume, Horizontal=Seek
                            val pointer = activePointers[0]
                            val currentPos = pointer.position
                            val totalDeltaX = currentPos.x - startPos.x
                            val totalDeltaY = currentPos.y - startPos.y

                            if (!hasDragged && (kotlin.math.abs(totalDeltaX) > 28 || kotlin.math.abs(totalDeltaY) > 28)) {
                                hasDragged = true
                                holdJob.cancel()
                                if (speedBoostActive) {
                                    isSpeed2x = false
                                    player?.setPlaybackSpeed(playbackSpeed)
                                    speedBoostActive = false
                                }
                                if (kotlin.math.abs(totalDeltaX) > kotlin.math.abs(totalDeltaY)) {
                                    dragMode = DragMode.SEEK
                                    initialSeekPosition = player?.currentPosition ?: 0L
                                    seekTargetPosition = initialSeekPosition
                                } else {
                                    dragMode = if (startPos.x < screenWidth / 2) DragMode.BRIGHTNESS else DragMode.VOLUME
                                }
                            }

                            if (hasDragged) {
                                pointer.consume()
                                when (dragMode) {
                                    DragMode.BRIGHTNESS -> {
                                        val stepDelta = -(pointer.position.y - pointer.previousPosition.y) / screenHeight
                                        val currentB = onBrightnessChange(stepDelta)
                                        hudIcon = Icons.Default.BrightnessMedium
                                        hudText = "Brightness ${(currentB * 100).toInt()}%"
                                    }
                                    DragMode.VOLUME -> {
                                        val stepDelta = -(pointer.position.y - pointer.previousPosition.y) / screenHeight
                                        val currentV = onVolumeChange(stepDelta)
                                        hudIcon = if (currentV > 0) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute
                                        hudText = "Volume ${(currentV * 100).toInt()}%"
                                    }
                                    DragMode.SEEK -> {
                                        player?.let { p ->
                                            val duration = p.duration.coerceAtLeast(1L)
                                            val scrubMs = (totalDeltaX / screenWidth * 90000L).toLong()
                                            val target = (initialSeekPosition + scrubMs).coerceIn(0L, duration)
                                            seekTargetPosition = target
                                            val diffSec = (target - initialSeekPosition) / 1000
                                            val sign = if (diffSec >= 0) "+$diffSec" else "$diffSec"
                                            hudIcon = if (diffSec >= 0) Icons.Default.FastForward else Icons.Default.FastRewind
                                            hudText = "$sign s (${formatDuration(target)})"
                                        }
                                    }
                                    DragMode.NONE -> {}
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (item.isVideo) {
            // Video Player Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = aspectRatioMode.resizeMode
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                        playerView.resizeMode = aspectRatioMode.resizeMode
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val baseAspectScale = if (aspectRatioMode == AspectRatioMode.ORIGINAL_100) 0.72f else 1.0f
                            scaleX = zoomScale * baseAspectScale
                            scaleY = zoomScale * baseAspectScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                )
            }
        } else {
            // Dedicated Audio Player UI with Large Thumbnail Artwork
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF222430),
                                Color(0xFF14151C),
                                Color.Black
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Album Artwork Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 16.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(if (isLandscape) 170.dp else 240.dp)
                    ) {
                        val thumbModel = item.localThumbnailPath?.let { File(it) } ?: item.thumbnailUrl
                        if (thumbModel != null) {
                            AsyncImage(
                                model = thumbModel,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    // Song / Audio Title
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Uploader / Channel Name & Format Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.uploader.ifEmpty { "Audio Track" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${item.container.uppercase()} • ${item.fileSizeFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Audio Speed Chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f)),
                            onClick = { showSpeedDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1x Speed" else "${playbackSpeed}x Speed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Zoom Reset Floating Badge (visible when video is zoomed in)
        if (item.isVideo && zoomScale > 1.05f) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                onClick = {
                    zoomScale = 1.0f
                    panOffset = Offset.Zero
                    zoomHudText = "100%"
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        "${(zoomScale * 100).toInt()}% (Reset)",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2X Speed Overlay Indicator (Touch & Hold)
        AnimatedVisibility(
            visible = isSpeed2x,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    Text("2X Speed", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Gesture HUD Feedback (Brightness / Volume / Scrub / Seek)
        hudText?.let { text ->
            LaunchedEffect(text) {
                delay(1200)
                hudText = null
                hudIcon = null
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    hudIcon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Pinch Zoom Percentage HUD Feedback
        zoomHudText?.let { text ->
            LaunchedEffect(text) {
                delay(1000)
                zoomHudText = null
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Aspect Ratio HUD Overlay (MX Player Style - Centered Extra-Bold Text)
        aspectRatioHudText?.let { text ->
            LaunchedEffect(text) {
                delay(1200)
                aspectRatioHudText = null
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.9f),
                            offset = Offset(2f, 4f),
                            blurRadius = 8f
                        )
                    )
                )
            }
        }

        // Overlay Controls (Top & Bottom bars)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playlistSize > 1) {
                            Text(
                                text = "Playing ${currentIndex + 1} of $playlistSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }

                    // Playback Speed Button (Clean badge icon)
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Sleep Timer Button
                    IconButton(onClick = { showSleepTimerMenu = true }) {
                        Icon(Icons.Default.Bedtime, contentDescription = "Sleep Timer", tint = Color.White)
                    }

                    // Aspect Ratio Button (MX Player style: FIT TO SCREEN, STRETCH, CROP, 100%)
                    if (item.isVideo) {
                        IconButton(onClick = onCycleAspectRatio) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio: ${aspectRatioMode.label}",
                                tint = Color.White
                            )
                        }
                    }

                    // Landscape / Portrait Toggle Button
                    IconButton(onClick = onToggleOrientation) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscape) "Portrait" else "Fullscreen Landscape",
                            tint = Color.White
                        )
                    }

                    // Open in External Player (VLC / MX Player)
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in External Player", tint = Color.White)
                    }
                }

                // Center Play/Pause, Rewind, Forward, Previous, Next Controls Row
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track Button
                    IconButton(
                        onClick = onPrevious,
                        enabled = hasPrevious,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // -10s Rewind Button
                    IconButton(
                        onClick = {
                            player?.let { p -> p.seekTo((p.currentPosition - 10000L).coerceAtLeast(0L)) }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    // Main Play/Pause Button
                    IconButton(
                        onClick = {
                            player?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // +10s Forward Button
                    IconButton(
                        onClick = {
                            player?.let { p ->
                                val target = (p.currentPosition + 10000L).coerceAtMost(p.duration.coerceAtLeast(0L))
                                p.seekTo(target)
                            }
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    // Next Track Button
                    IconButton(
                        onClick = onNext,
                        enabled = hasNext,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Bottom Timeline & Controls
                PlayerBottomBar(
                    player = player,
                    aspectRatioLabel = if (item.isVideo) aspectRatioMode.label else null,
                    onToggleAspectRatio = if (item.isVideo) onCycleAspectRatio else null,
                    isLandscape = isLandscape,
                    onToggleOrientation = onToggleOrientation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerMenu) {
        AlertDialog(
            onDismissRequest = { showSleepTimerMenu = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    listOf(
                        "15 minutes" to 15,
                        "30 minutes" to 30,
                        "45 minutes" to 45,
                        "60 minutes" to 60,
                        "Turn Off" to 0
                    ).forEach { (label, mins) ->
                        TextButton(
                            onClick = {
                                onSleepTimerSelected(mins)
                                showSleepTimerMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerMenu = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Playback Speed Selection Dialog (Applies to all videos & audio until changed)
    if (showSpeedDialog) {
        val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Playback Speed")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Speed applies to all videos and audio tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    speedOptions.forEach { speed ->
                        val isSelected = kotlin.math.abs(playbackSpeed - speed) < 0.01f
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            onClick = {
                                playbackSpeed = speed
                                PlayerSettings.setPlaybackSpeed(context, speed)
                                player?.setPlaybackSpeed(speed)
                                hudIcon = Icons.Default.Speed
                                hudText = "Speed: ${if (speed == 1.0f) "1.0x (Normal)" else "${speed}x"}"
                                showSpeedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PlayerBottomBar(
    player: ExoPlayer?,
    aspectRatioLabel: String? = null,
    onToggleAspectRatio: (() -> Unit)? = null,
    isLandscape: Boolean = false,
    onToggleOrientation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Slider(
            value = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac ->
                player?.let { p ->
                    val target = (frac * duration).toLong()
                    p.seekTo(target)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.Red,
                activeTrackColor = Color.Red,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatDuration(position)} / ${formatDuration(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // MX Player Aspect Ratio Button [<->]
                if (aspectRatioLabel != null && onToggleAspectRatio != null) {
                    IconButton(
                        onClick = onToggleAspectRatio,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio: $aspectRatioLabel",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Landscape / Portrait fullscreen toggle [⛶]
                if (onToggleOrientation != null) {
                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isLandscape) "Portrait" else "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s) else "%02d:%02d".format(m, s)
}
