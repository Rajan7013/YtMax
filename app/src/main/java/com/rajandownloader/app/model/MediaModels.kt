package com.rajandownloader.app.model

data class MediaMetadata(
    val id: String,
    val title: String,
    val description: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String?,
    val tags: List<String>,
    val options: List<VideoOption>,
    val resolutions: List<Int>
) {
    val formattedDuration: String
        get() {
            if (duration <= 0) return "Live / Unknown"
            val m = duration / 60
            val s = duration % 60
            val h = m / 60
            return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s) else "%02d:%02d".format(m, s)
        }
}

data class VideoOption(
    val id: String,
    val label: String,
    val height: Int,
    val mode: String
)

enum class DownloadStage {
    IDLE,
    FETCHING_METADATA,
    DOWNLOADING_VIDEO,
    DOWNLOADING_AUDIO,
    DOWNLOADING,
    MERGING,
    SAVING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgress(
    val percentage: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedStr: String = "",
    val etaStr: String = "",
    val stage: DownloadStage = DownloadStage.IDLE,
    val stageMessage: String = ""
) {
    val downloadedMb: Double get() = downloadedBytes / (1024.0 * 1024.0)
    val totalMb: Double get() = totalBytes / (1024.0 * 1024.0)
}

data class DownloadJob(
    val url: String,
    val title: String,
    val option: VideoOption,
    val container: String = "mp4",
    val outputPath: String? = null,
    val errorDetails: String? = null
)

