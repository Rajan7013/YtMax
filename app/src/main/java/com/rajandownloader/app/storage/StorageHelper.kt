package com.rajandownloader.app.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class StorageHelper(private val context: Context) {

    companion object {
        private const val TAG = "StorageHelper"
    }

    val stagingDir: File
        get() = File(context.filesDir, "staging").apply { mkdirs() }

    val defaultDownloadDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    suspend fun exportStagingFileToDestination(
        stagingFile: File,
        destinationTreeUri: String?,
        targetMimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!stagingFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Source staging file does not exist: ${stagingFile.absolutePath}"))
        }

        // 1. If user picked a custom SAF directory:
        if (!destinationTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(destinationTreeUri)
                val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    val newDocFile = pickedDir.createFile(targetMimeType, stagingFile.name)
                    if (newDocFile != null) {
                        context.contentResolver.openOutputStream(newDocFile.uri)?.use { out ->
                            FileInputStream(stagingFile).use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                                out.flush()
                            }
                        } ?: throw IllegalStateException("Unable to open output stream to SAF document")

                        Log.d(TAG, "Exported successfully via SAF to: ${newDocFile.uri}")
                        return@withContext Result.success(newDocFile.uri.toString())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "SAF export failed, falling back to public Downloads: ${e.message}")
            }
        }

        // 2. Default: Export to public Downloads directory
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ Scoped Storage: Use MediaStore.Downloads
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, stagingFile.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, targetMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val downloadCollection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = context.contentResolver.insert(downloadCollection, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry in Downloads")

                context.contentResolver.openOutputStream(itemUri)?.use { output ->
                    FileInputStream(stagingFile).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                } ?: throw IllegalStateException("Unable to open stream for MediaStore URI")

                // Mark as no longer pending so other apps can access it
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, values, null, null)

                Log.d(TAG, "Exported successfully via MediaStore to: $itemUri")
                Result.success(itemUri.toString())
            } else {
                // Android 9 and lower: Direct file write + MediaScanner
                val targetDir = defaultDownloadDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, stagingFile.name)

                FileInputStream(stagingFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf(targetMimeType),
                    null
                )

                Log.d(TAG, "Exported successfully to file: ${destFile.absolutePath}")
                Result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export to Downloads failed", e)
            Result.failure(e)
        }
    }

    fun cleanStagingFiles() {
        try {
            stagingDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed cleaning staging directory", e)
        }
    }
}
