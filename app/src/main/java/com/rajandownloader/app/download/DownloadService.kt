package com.rajandownloader.app.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.rajandownloader.app.model.DownloadJob
import com.rajandownloader.app.model.DownloadProgress
import com.rajandownloader.app.model.DownloadStage
import com.rajandownloader.app.model.VideoOption
import com.rajandownloader.app.runtime.PythonProcessRunner
import com.rajandownloader.app.runtime.RuntimeManager
import com.rajandownloader.app.storage.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        const val ACTION_START = "com.rajandownloader.app.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.rajandownloader.app.CANCEL_DOWNLOAD"

        private val _currentProgress = MutableStateFlow(DownloadProgress())
        val currentProgress = _currentProgress.asStateFlow()

        private val _activeJob = MutableStateFlow<DownloadJob?>(null)
        val activeJob = _activeJob.asStateFlow()

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            optionId: String,
            optionLabel: String,
            height: Int,
            mode: String,
            container: String,
            destinationUri: String?,
            cookiesPath: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra("url", url)
                putExtra("title", title)
                putExtra("optionId", optionId)
                putExtra("optionLabel", optionLabel)
                putExtra("height", height)
                putExtra("mode", mode)
                putExtra("container", container)
                putExtra("destinationUri", destinationUri)
                putExtra("cookiesPath", cookiesPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationHelper: DownloadNotificationHelper
    private lateinit var runtimeManager: RuntimeManager
    private lateinit var storageHelper: StorageHelper
    private var processRunner: PythonProcessRunner? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this)
        runtimeManager = RuntimeManager(this)
        storageHelper = StorageHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel requested")
            processRunner?.cancel()
            _currentProgress.value = DownloadProgress(stage = DownloadStage.CANCELLED)
            notificationHelper.update(_activeJob.value?.title ?: "Download", _currentProgress.value)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
            val title = intent.getStringExtra("title") ?: "Video"
            val optionId = intent.getStringExtra("optionId") ?: "bestvideo+bestaudio/best"
            val optionLabel = intent.getStringExtra("optionLabel") ?: "Best Quality"
            val height = intent.getIntExtra("height", 0)
            val mode = intent.getStringExtra("mode") ?: "merged"
            val container = intent.getStringExtra("container") ?: "mp4"
            val destinationUri = intent.getStringExtra("destinationUri")
            val cookiesPath = intent.getStringExtra("cookiesPath")

            val option = VideoOption(id = optionId, label = optionLabel, height = height, mode = mode)
            val job = DownloadJob(url = url, title = title, option = option, container = container)
            _activeJob.value = job

            val initialNotification = notificationHelper.buildNotification(
                title,
                DownloadProgress(stage = DownloadStage.DOWNLOADING)
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadNotificationHelper.NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(DownloadNotificationHelper.NOTIFICATION_ID, initialNotification)
            }

            serviceScope.launch {
                executeDownload(job, destinationUri, cookiesPath)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun executeDownload(
        job: DownloadJob,
        destinationUri: String?,
        cookiesPath: String?
    ) {
        val paths = runtimeManager.paths
        val runner = PythonProcessRunner(paths)
        processRunner = runner

        _currentProgress.value = DownloadProgress(stage = DownloadStage.DOWNLOADING, stageMessage = "Starting download...")
        notificationHelper.update(job.title, _currentProgress.value)

        val stagingDir = storageHelper.stagingDir
        val cleanTitle = job.title.replace(Regex("[\\\\/*?:\"<>|\\r\\n\\t]"), " ").trim().take(80)
        val filenameBase = "${cleanTitle}_${System.currentTimeMillis() % 10000}"
        val effectiveMode = if (job.option.mode == "audio") "audio_only" else job.option.mode

        val args = mutableListOf(
            paths.backendScript,
            "download",
            job.url,
            "--mode", effectiveMode,
            "--format-id", job.option.id,
            "--output-dir", stagingDir.absolutePath,
            "--filename-base", filenameBase,
            "--container", job.container
        )

        var completedFilePath: String? = null

        val res = runner.runPython(args, cookiesPath) { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("PROGRESS:")) {
                try {
                    val json = JSONObject(trimmed.substring("PROGRESS:".length))
                    val percentage = json.optInt("percentage", 0)
                    val downloaded = json.optLong("downloaded_bytes", 0L)
                    val total = json.optLong("total_bytes", 0L)
                    val speed = json.optString("speed_str", "")
                    val eta = json.optString("eta_str", "")
                    val stageStr = json.optString("stage", "downloading")

                    val stage = if (stageStr == "merging") DownloadStage.MERGING else DownloadStage.DOWNLOADING

                    val progress = DownloadProgress(
                        percentage = percentage,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedStr = speed,
                        etaStr = eta,
                        stage = stage,
                        stageMessage = if (stage == DownloadStage.MERGING) "Merging streams..." else "Downloading..."
                    )
                    _currentProgress.value = progress
                    notificationHelper.update(job.title, progress)
                } catch (_: Exception) {}
            } else if (trimmed.startsWith("COMPLETE:")) {
                completedFilePath = trimmed.substring("COMPLETE:".length).trim()
            } else if (trimmed.startsWith("COMPLETE_SEPARATE:")) {
                completedFilePath = trimmed.substring("COMPLETE_SEPARATE:".length).trim()
            }
        }

        // Check stdout as fallback for COMPLETE marker
        if (completedFilePath.isNullOrEmpty()) {
            for (line in res.stdout.lines()) {
                val t = line.trim()
                if (t.contains("COMPLETE:")) {
                    val idx = t.indexOf("COMPLETE:")
                    completedFilePath = t.substring(idx + "COMPLETE:".length).trim()
                    break
                }
            }
        }

        if (res.exitCode == 0 && !completedFilePath.isNullOrEmpty()) {
            _currentProgress.value = DownloadProgress(stage = DownloadStage.SAVING, stageMessage = "Saving to destination...")
            notificationHelper.update(job.title, _currentProgress.value)

            val mimeType = if (effectiveMode == "audio_only") "audio/*" else "video/${job.container}"
            val finalTarget = File(completedFilePath!!)

            val exportResult = storageHelper.exportStagingFileToDestination(finalTarget, destinationUri, mimeType)

            if (exportResult.isSuccess) {
                val finalSavedPath = exportResult.getOrNull()
                _activeJob.value = job.copy(outputPath = finalSavedPath)
                _currentProgress.value = DownloadProgress(
                    percentage = 100,
                    stage = DownloadStage.COMPLETED,
                    stageMessage = "Download completed successfully!"
                )
                notificationHelper.update(job.title, _currentProgress.value)
                storageHelper.cleanStagingFiles()
            } else {
                val err = exportResult.exceptionOrNull()?.message ?: "Export to destination failed"
                _currentProgress.value = DownloadProgress(stage = DownloadStage.FAILED, stageMessage = err)
                _activeJob.value = job.copy(errorDetails = err)
                notificationHelper.update(job.title, _currentProgress.value)
            }
        } else {
            var errorMsg: String? = null
            for (line in res.stdout.lines()) {
                val t = line.trim()
                if (t.startsWith("{") && t.contains("\"status\": \"error\"")) {
                    try {
                        val obj = JSONObject(t)
                        if (obj.has("details")) {
                            errorMsg = obj.getString("details")
                        }
                    } catch (_: Exception) {}
                }
            }
            if (errorMsg.isNullOrEmpty()) {
                val realErrors = res.stderr.lines().filter { 
                    val low = it.lowercase()
                    !low.startsWith("warning:") && (low.contains("error:") || low.contains("traceback") || low.contains("exception") || low.contains("permission"))
                }
                errorMsg = if (realErrors.isNotEmpty()) {
                    realErrors.joinToString("\n").take(250)
                } else {
                    res.stderr.lines().firstOrNull { it.isNotBlank() && !it.startsWith("WARNING:") }
                        ?: res.stderr.ifEmpty { res.stdout }.take(250)
                }
            }
            _currentProgress.value = DownloadProgress(stage = DownloadStage.FAILED, stageMessage = errorMsg)
            _activeJob.value = job.copy(errorDetails = errorMsg)
            notificationHelper.update(job.title, _currentProgress.value)
        }

        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

