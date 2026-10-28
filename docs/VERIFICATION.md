# Rajan Downloader — Verification & Testing Protocol

## 1. Minimal Runtime Proof

The app implements a diagnostic test suite that executes directly on a physical Android device or emulator. It tests the 6 essential runtime layers without requiring external tooling or a laptop connection.

### Diagnostic Matrix

| Test Layer | Expected Result | Verification Command / API |
|---|---|---|
| **Python Binary** | `Python 3.11.11 (Embedded ARM64) [OK]` | `python3.11 --version` via `/system/bin/linker64` |
| **yt-dlp Engine** | `2025.xx.xx [OK]` | `import yt_dlp; yt_dlp.version.__version__` |
| **FFmpeg Binary** | `FFmpeg 8.0.1 (Android ARM64) [OK]` | `ffmpeg -version` |
| **FFprobe Wrapper** | `FFprobe Wrapper [OK]` | Shell wrapper / binary check |
| **JS Runtime (QuickJS)**| `QuickJS (qjs ELF) [OK]` | `QuickJsRuntime` challenge test |
| **Metadata Extraction** | `Successfully extracted sample metadata [OK]` | Extraction test on public YouTube video URL |

To run the verification suite inside the app:
1. Tap the **Bug / Diagnostics** icon in the top right corner of the app bar.
2. The app executes each diagnostic check asynchronously and displays green checkmarks for all layers.
3. Tap **Copy Logs** to copy the complete execution log to the clipboard for auditing.

---

## 2. End-to-End Download Verification Scenarios

### Scenario A: Best Merged Video + Audio (1080p, 4K, 8K)
1. Paste a public YouTube link (e.g. `https://www.youtube.com/watch?v=...`).
2. Tap **Fetch Info**.
3. Verify title, channel, duration, and thumbnail appear.
4. Select **Merged** mode.
5. Select desired resolution (e.g., 1080p or 4K).
6. Tap **Start Download**.
7. Observe live percentage, MB/s speed, ETA, and stage transition:
   `Downloading video stream -> Downloading audio stream -> Merging with FFmpeg -> Saving to destination`.
8. Once finished, tap **Open File** to play the merged video with sound in your device's video player.

### Scenario B: Audio Only (Original Codec Preservation)
1. Tap **Audio Only** mode.
2. Tap **Start Download**.
3. Verify the file downloads directly without video streams and saves as an audio file (`.m4a` or `.opus` or `.mp3`).
4. Tap **Open File** to play in a music player.

### Scenario C: Custom Storage Folder (Storage Access Framework)
1. Tap **Change** in the Destination card.
2. Pick any folder on device internal storage or SD card (e.g. `Movies` or `Music`).
3. Download a video and verify the output file appears in the exact folder selected.

### Scenario D: Background Service & Notification Control
1. Start downloading a large 4K/8K video.
2. Press the Home button to send the app to the background.
3. Pull down the Android notification shade:
   - Notice the ongoing notification displaying live percentage, speed, and ETA.
   - Tap **Cancel** to abort the download cleanly.

