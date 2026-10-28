package com.rajandownloader.app.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.rajandownloader.app.model.DownloadedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadHistoryManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DownloadHistoryManager"
        private const val HISTORY_FILE_NAME = "download_history.json"

        @Volatile
        private var INSTANCE: DownloadHistoryManager? = null

        fun getInstance(context: Context): DownloadHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val historyFile: File = File(context.filesDir, HISTORY_FILE_NAME)
    private val thumbnailsDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }

    private val _items = MutableStateFlow<List<DownloadedItem>>(emptyList())
    val items: StateFlow<List<DownloadedItem>> = _items.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        if (!historyFile.exists()) {
            _items.value = emptyList()
            return
        }
        try {
            val content = historyFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<DownloadedItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsList = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(j))
                    }
                }

                list.add(
                    DownloadedItem(
                        id = obj.getString("id"),
                        videoId = obj.optString("videoId", ""),
                        title = obj.getString("title"),
                        uploader = obj.optString("uploader", "Unknown Channel"),
                        duration = obj.optLong("duration", 0L),
                        formattedDuration = obj.optString("formattedDuration", ""),
                        description = obj.optString("description", ""),
                        tags = tagsList,
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        localThumbnailPath = obj.optString("localThumbnailPath").takeIf { it.isNotBlank() },
                        isVideo = obj.optBoolean("isVideo", true),
                        formatLabel = obj.optString("formatLabel", "Merged"),
                        container = obj.optString("container", "mp4"),
                        fileSizeFormatted = obj.optString("fileSizeFormatted", "Unknown"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        fileUriString = obj.optString("fileUriString", ""),
                        localFilePath = obj.optString("localFilePath", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _items.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load download history", e)
            _items.value = emptyList()
        }
    }

    private fun persistHistory(list: List<DownloadedItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("videoId", item.videoId)
                    put("title", item.title)
                    put("uploader", item.uploader)
                    put("duration", item.duration)
                    put("formattedDuration", item.formattedDuration)
                    put("description", item.description)
                    put("tags", JSONArray(item.tags))
                    put("thumbnailUrl", item.thumbnailUrl ?: "")
                    put("localThumbnailPath", item.localThumbnailPath ?: "")
                    put("isVideo", item.isVideo)
                    put("formatLabel", item.formatLabel)
                    put("container", item.container)
                    put("fileSizeFormatted", item.fileSizeFormatted)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("fileUriString", item.fileUriString)
                    put("localFilePath", item.localFilePath)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            historyFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist download history", e)
        }
    }

    suspend fun saveDownloadedThumbnail(
        thumbnailUrl: String?,
        videoId: String,
        title: String = "",
        cachedLocalPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val safeId = videoId.ifEmpty { System.currentTimeMillis().toString() }
        val cleanTitle = title.replace(Regex("[\\\\/*?:\"<>|\\r\\n\\t]"), " ").trim().take(50).ifEmpty { safeId }
        val fileName = "${cleanTitle}_thumb_${safeId.takeLast(6)}.jpg"

        // 1. Automatically save to public Pictures/YtMax so it appears in device photo gallery
        try {
            val localSource = cachedLocalPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YtMax")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        if (localSource != null) {
                            localSource.inputStream().use { input -> input.copyTo(output) }
                        } else if (!thumbnailUrl.isNullOrBlank()) {
                            URL(thumbnailUrl).openStream().use { input -> input.copyTo(output) }
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "YtMax").apply { mkdirs() }
                val targetFile = File(appDir, fileName)
                FileOutputStream(targetFile).use { output ->
                    if (localSource != null) {
                        localSource.inputStream().use { input -> input.copyTo(output) }
                    } else if (!thumbnailUrl.isNullOrBlank()) {
                        URL(thumbnailUrl).openStream().use { input -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-gallery export skipped: ${e.message}")
        }

        // 2. Cache in app thumbnailsDir for 100% offline instant in-app viewing
        val thumbFile = File(thumbnailsDir, "thumb_${safeId}.jpg")
        try {
            if (!thumbFile.exists() || thumbFile.length() == 0L) {
                val localSource = cachedLocalPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                if (localSource != null && localSource.absolutePath != thumbFile.absolutePath) {
                    localSource.copyTo(thumbFile, overwrite = true)
                } else if (!thumbnailUrl.isNullOrBlank()) {
                    URL(thumbnailUrl).openStream().use { input ->
                        FileOutputStream(thumbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            if (thumbFile.exists() && thumbFile.length() > 0) {
                return@withContext thumbFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache thumbnail locally: ${e.message}")
        }
        cachedLocalPath
    }

    suspend fun addItem(item: DownloadedItem) = withContext(Dispatchers.IO) {
        val current = _items.value.toMutableList()
        current.removeAll { it.id == item.id || (it.localFilePath.isNotEmpty() && it.localFilePath == item.localFilePath) }
        current.add(0, item)
        _items.value = current
        persistHistory(current)
    }

    suspend fun renameItem(item: DownloadedItem, newNameWithoutExt: String): Result<DownloadedItem> = withContext(Dispatchers.IO) {
        try {
            val cleanName = newNameWithoutExt.replace(Regex("[\\\\/*?:\"<>|\\r\\n\\t]"), " ").trim()
            if (cleanName.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            }

            var updatedLocalPath = item.localFilePath
            var updatedUriString = item.fileUriString

            // 1. Rename physical file if it exists
            if (item.localFilePath.isNotEmpty()) {
                val oldFile = File(item.localFilePath)
                if (oldFile.exists()) {
                    val ext = oldFile.extension.ifEmpty { item.container }
                    val newFile = File(oldFile.parentFile, "$cleanName.$ext")
                    if (oldFile.renameTo(newFile)) {
                        updatedLocalPath = newFile.absolutePath
                    }
                }
            }

            // 2. Rename MediaStore entry if content:// URI
            if (item.fileUriString.startsWith("content://")) {
                try {
                    val uri = Uri.parse(item.fileUriString)
                    val ext = item.fileExtension
                    val fullName = "$cleanName.$ext"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    }
                    context.contentResolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore rename failed: ${e.message}")
                }
            }

            val updatedItem = item.copy(
                title = cleanName,
                localFilePath = updatedLocalPath,
                fileUriString = updatedUriString
            )

            val current = _items.value.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                current[idx] = updatedItem
                _items.value = current
                persistHistory(current)
            }

            Result.success(updatedItem)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rename item", e)
            Result.failure(e)
        }
    }

    suspend fun renameThumbnail(item: DownloadedItem, newNameWithoutExt: String): Result<DownloadedItem> = withContext(Dispatchers.IO) {
        try {
            val cleanName = newNameWithoutExt.replace(Regex("[\\\\/*?:\"<>|\\r\\n\\t]"), " ").trim()
            if (cleanName.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            }

            var updatedThumbPath = item.localThumbnailPath ?: return@withContext Result.failure(IllegalStateException("No thumbnail found"))
            val oldFile = File(updatedThumbPath)
            if (oldFile.exists()) {
                val ext = oldFile.extension.ifEmpty { "jpg" }
                val newFile = File(oldFile.parentFile, "$cleanName.$ext")
                if (oldFile.renameTo(newFile)) {
                    updatedThumbPath = newFile.absolutePath
                }
            }

            val updatedItem = item.copy(localThumbnailPath = updatedThumbPath)
            val current = _items.value.toMutableList()
            val idx = current.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                current[idx] = updatedItem
                _items.value = current
                persistHistory(current)
            }
            Result.success(updatedItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteItem(item: DownloadedItem) = withContext(Dispatchers.IO) {
        deleteItems(listOf(item))
    }

    suspend fun deleteItems(itemsToDelete: List<DownloadedItem>) = withContext(Dispatchers.IO) {
        val deleteIds = itemsToDelete.map { it.id }.toSet()
        val current = _items.value.toMutableList()

        for (item in itemsToDelete) {
            // Delete actual file if accessible
            if (item.fileUriString.isNotEmpty()) {
                try {
                    val uri = Uri.parse(item.fileUriString)
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
            if (item.localFilePath.isNotEmpty()) {
                try {
                    File(item.localFilePath).delete()
                } catch (_: Exception) {}
            }
            // Delete local thumbnail
            if (!item.localThumbnailPath.isNullOrEmpty()) {
                try {
                    File(item.localThumbnailPath).delete()
                } catch (_: Exception) {}
            }
        }

        current.removeAll { it.id in deleteIds }
        _items.value = current
        persistHistory(current)
    }

    /**
     * Deletes ONLY the cached thumbnail file on disk, without touching or deleting
     * the downloaded video or audio file.
     */
    suspend fun deleteThumbnailOnly(item: DownloadedItem) = withContext(Dispatchers.IO) {
        deleteThumbnailsOnly(listOf(item))
    }

    suspend fun deleteThumbnailsOnly(itemsToDelete: List<DownloadedItem>) = withContext(Dispatchers.IO) {
        val deleteIds = itemsToDelete.map { it.id }.toSet()
        val current = _items.value.toMutableList()

        for (item in itemsToDelete) {
            if (!item.localThumbnailPath.isNullOrEmpty()) {
                try {
                    File(item.localThumbnailPath).delete()
                } catch (_: Exception) {}
            }
        }

        // Update items to clear localThumbnailPath while keeping the media file completely intact
        for (i in current.indices) {
            val it = current[i]
            if (it.id in deleteIds) {
                current[i] = it.copy(localThumbnailPath = null)
            }
        }

        // Only remove from history if the item has no actual downloaded video/audio file
        current.removeAll { it.id in deleteIds && it.fileUriString.isEmpty() && it.localFilePath.isEmpty() }

        _items.value = current
        persistHistory(current)
    }
}
