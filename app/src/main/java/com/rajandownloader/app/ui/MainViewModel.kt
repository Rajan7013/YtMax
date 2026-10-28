package com.rajandownloader.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rajandownloader.app.RajanApp
import com.rajandownloader.app.download.DownloadService
import com.rajandownloader.app.model.DownloadJob
import com.rajandownloader.app.model.DownloadProgress
import com.rajandownloader.app.model.DownloadStage
import com.rajandownloader.app.model.MediaMetadata
import com.rajandownloader.app.model.VideoOption
import com.rajandownloader.app.runtime.PythonProcessRunner
import com.rajandownloader.app.runtime.RuntimeManager
import com.rajandownloader.app.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val runtimeManager: RuntimeManager = (application as RajanApp).runtimeManager
    val preferences = AppPreferences(application)

    val currentProgress: StateFlow<DownloadProgress> = DownloadService.currentProgress
    val activeJob: StateFlow<DownloadJob?> = DownloadService.activeJob

    val destinationUri: StateFlow<String?> = preferences.destinationUri.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val destinationDisplayName: StateFlow<String?> = preferences.destinationDisplayName.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val preferredContainer: StateFlow<String> = preferences.preferredContainer.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "mp4"
    )

    private val _url = MutableStateFlow("")
    val url = _url.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching = _isFetching.asStateFlow()

    private val _metadata = MutableStateFlow<MediaMetadata?>(null)
    val metadata = _metadata.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError = _fetchError.asStateFlow()

    private val _selectedMode = MutableStateFlow("merged") // merged, audio, separate
    val selectedMode = _selectedMode.asStateFlow()

    private val _selectedOption = MutableStateFlow<VideoOption?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    private val _runtimeReady = MutableStateFlow(runtimeManager.isRuntimeReady())
    val runtimeReady = _runtimeReady.asStateFlow()

    private val _runtimeStatusMessage = MutableStateFlow("")
    val runtimeStatusMessage = _runtimeStatusMessage.asStateFlow()

    init {
        checkAndInitRuntime()
    }

    fun onUrlChanged(newUrl: String) {
        _url.value = newUrl
        _fetchError.value = null
    }

    fun setMode(mode: String) {
        _selectedMode.value = mode
        val meta = _metadata.value ?: return
        if (mode == "audio" || mode == "audio_only") {
            _selectedOption.value = meta.options.firstOrNull { it.mode == "audio_only" || it.mode == "audio" }
                ?: VideoOption("bestaudio/best", "Audio Only (Original Audio)", 0, "audio_only")
        } else {
            _selectedOption.value = meta.options.firstOrNull { it.mode == "merged" }
        }
    }

    fun setOption(opt: VideoOption) {
        _selectedOption.value = opt
    }

    fun setContainer(container: String) {
        viewModelScope.launch {
            preferences.setPreferredContainer(container)
        }
    }

    fun setDestination(uriString: String, displayName: String) {
        viewModelScope.launch {
            preferences.setDestination(uriString, displayName)
        }
    }

    fun checkAndInitRuntime() {
        viewModelScope.launch {
            if (!runtimeManager.isRuntimeReady()) {
                _runtimeStatusMessage.value = "Extracting embedded Python runtime..."
                runtimeManager.ensureRuntimeInitialized { msg ->
                    _runtimeStatusMessage.value = msg
                }
            }
            _runtimeReady.value = runtimeManager.isRuntimeReady()
            _runtimeStatusMessage.value = if (_runtimeReady.value) "Runtime Ready" else "Runtime Init Failed"
        }
    }

    fun fetchMetadata() {
        val targetUrl = _url.value.trim()
        if (targetUrl.isEmpty()) {
            _fetchError.value = "Please enter or paste a valid YouTube URL"
            return
        }

        viewModelScope.launch {
            _isFetching.value = true
            _fetchError.value = null
            _metadata.value = null

            try {
                if (!_runtimeReady.value) {
                    runtimeManager.ensureRuntimeInitialized()
                    _runtimeReady.value = runtimeManager.isRuntimeReady()
                }

                val runner = PythonProcessRunner(runtimeManager.paths)
                val customCookies = preferences.customCookiesPath.first()

                var metadataJsonStr: String? = null

                val result = runner.runPython(
                    listOf(runtimeManager.paths.backendScript, "info", targetUrl),
                    cookiesFilePath = customCookies,
                    onOutputLine = { line ->
                        if (line.startsWith("METADATA:")) {
                            metadataJsonStr = line.removePrefix("METADATA:").trim()
                        }
                    }
                )

                if (metadataJsonStr != null) {
                    val parsed = parseMetadataJson(metadataJsonStr!!)
                    if (parsed != null) {
                        _metadata.value = parsed
                        _selectedOption.value = parsed.options.firstOrNull()
                    } else {
                        _fetchError.value = "Failed to parse metadata from YouTube"
                    }
                } else {
                    val errDetails = result.stderr.ifEmpty { result.stdout }
                    _fetchError.value = if (errDetails.contains("cookies")) {
                        "YouTube anti-bot challenge: Valid cookies required. Open Cookies settings to configure."
                    } else if (errDetails.isNotEmpty()) {
                        errDetails.lines().lastOrNull { it.isNotBlank() } ?: "Failed to fetch metadata"
                    } else {
                        "Failed to connect to YouTube. Please check network."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching metadata", e)
                _fetchError.value = "Error: ${e.message}"
            } finally {
                _isFetching.value = false
            }
        }
    }

    private fun parseMetadataJson(jsonStr: String): MediaMetadata? {
        return try {
            val root = JSONObject(jsonStr)
            val id = root.optString("id", "")
            val title = root.optString("title", "Untitled")
            val description = root.optString("description", "")
            val uploader = root.optString("uploader", "Unknown Channel")
            val duration = root.optLong("duration", 0)
            val thumbnail = if (root.has("thumbnail") && !root.isNull("thumbnail")) root.getString("thumbnail") else null

            val tagsList = mutableListOf<String>()
            val tagsArr = root.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.getString(i))
                }
            }

            val optionsList = mutableListOf<VideoOption>()
            val optArr = root.optJSONArray("options")
            if (optArr != null) {
                for (i in 0 until optArr.length()) {
                    val item = optArr.getJSONObject(i)
                    optionsList.add(
                        VideoOption(
                            id = item.getString("id"),
                            label = item.getString("label"),
                            height = item.optInt("height", 0),
                            mode = item.optString("mode", "merged")
                        )
                    )
                }
            }

            val resolutionsList = mutableListOf<Int>()
            val resArr = root.optJSONArray("resolutions")
            if (resArr != null) {
                for (i in 0 until resArr.length()) {
                    resolutionsList.add(resArr.getInt(i))
                }
            }

            MediaMetadata(
                id = id,
                title = title,
                description = description,
                uploader = uploader,
                duration = duration,
                thumbnail = thumbnail,
                tags = tagsList,
                options = optionsList,
                resolutions = resolutionsList
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
            null
        }
    }

    fun startDownload() {
        val meta = _metadata.value ?: return
        val currentUrl = _url.value.trim()
        val destUri = destinationUri.value
        val container = preferredContainer.value
        val mode = _selectedMode.value
        val opt = if (mode == "audio" || mode == "audio_only") {
            meta.options.firstOrNull { it.mode == "audio" || it.mode == "audio_only" }
                ?: VideoOption("bestaudio/best", "Audio Only (Original Audio)", 0, "audio_only")
        } else {
            _selectedOption.value ?: meta.options.firstOrNull { it.mode == "merged" } ?: meta.options.firstOrNull() ?: return
        }

        viewModelScope.launch {
            val customCookies = preferences.customCookiesPath.first()

            DownloadService.startDownload(
                context = getApplication(),
                url = currentUrl,
                title = meta.title,
                optionId = opt.id,
                optionLabel = opt.label,
                height = opt.height,
                mode = if (mode == "audio") "audio_only" else mode,
                container = container,
                destinationUri = destUri,
                cookiesPath = customCookies
            )
        }
    }

    fun cancelDownload() {
        DownloadService.cancelDownload(getApplication())
    }
}

