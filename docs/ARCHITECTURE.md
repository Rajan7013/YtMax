# Rajan Downloader — Technical Architecture

## 1. System Overview

**Rajan Downloader** is a standalone, fully offline-capable (no external server, no desktop companion, no Termux) Android application engineered to download YouTube videos and audio at maximum quality (up to 8K Ultra HD) directly on an Android device.

```
+---------------------------------------------------------------------------------+
|                                 ANDROID OS                                      |
|                                                                                 |
|  +---------------------------------------------------------------------------+  |
|  |                  Jetpack Compose UI (Material 3)                          |  |
|  |  [URL Input / Paste] [Metadata Card] [Quality Selector] [Progress Bar]    |  |
|  |             [Diagnostics Dialog]  |  [Cookies Settings Dialog]            |  |
|  +-----------------------------------+---------------------------------------+  |
|                                      |                                          |
|                                      v                                          |
|  +---------------------------------------------------------------------------+  |
|  |                 MainViewModel & StateFlow Architecture                    |  |
|  +-----------------------------------+---------------------------------------+  |
|                                      |                                          |
|                                      v                                          |
|  +---------------------------------------------------------------------------+  |
|  |             Foreground Service (`DownloadService` - dataSync)             |  |
|  |   - Persistent Notification with Progress & Cancel Action                 |  |
|  |   - Coroutine IO Execution & WakeLock Protection                          |  |
|  +-----------------------------------+---------------------------------------+  |
|                                      |                                          |
|                 +--------------------+---------------------+                    |
|                 |                                          |                    |
|                 v                                          v                    |
|  +------------------------------+       +------------------------------------+  |
|  | PythonProcessRunner          |       | StorageHelper                      |  |
|  | - Spawns embedded python3.11 |       | - Staging in app private cache     |  |
|  | - Parses PROGRESS: JSON      |       | - Stream export to user SAF folder |  |
|  | - Direct stdout/err pipe     |       | - Fallback to public Downloads     |  |
|  +--------------+---------------+       +------------------------------------+  |
|                 |                                                               |
+-----------------|---------------------------------------------------------------+
                  v
+---------------------------------------------------------------------------------+
|                       EMBEDDED NATIVE RUNTIME (ARM64-v8a)                       |
|                                                                                 |
|  +-----------------------------+  +-------------------+  +-------------------+  |
|  |       CPython 3.11          |  |     QuickJS       |  |      FFmpeg       |  |
|  | (python3.11 + dynload .so)  |  |    (qjs ELF)      |  | (ffmpeg + ffprobe)|  |
|  +--------------+--------------+  +---------+---------+  +---------+---------+  |
|                 |                           |                      |            |
|                 +---------------------+     |                      |            |
|                                       v     v                      |            |
|                            +---------------------+                 |            |
|                            |  yt-dlp Engine      |                 |            |
|                            |  + isolated cookies |                 |            |
|                            +----------+----------+                 |            |
|                                       |                            |            |
|                                       +-------------> <------------+            |
|                                                     |                           |
|                                             Output Media File                   |
+---------------------------------------------------------------------------------+
```

---

## 2. Core Components

### 2.1 Embedded CPython 3.11 & Native Binaries
On Android 10+ (API 29+), executing files within writable application data directories is restricted by SELinux `W^X` policies unless executed via the dynamic linker (`/system/bin/linker64`).
- **Python Runtime**: `python3.11` binary paired with `libpython3.11.so`, `libssl.so.3`, `libcrypto.so.3`, and `libffi.so`.
- **Standard Library**: Extracted into `$APP_FILES/python311-stdlib` with dynload C-extensions (`_socket.so`, `_ssl.so`, `_ctypes.so`, etc.).
- **Site Packages**: Packaged zip containing modern `yt-dlp` and `certifi`.

### 2.2 JavaScript Challenge Solver (QuickJS)
YouTube embeds JavaScript challenges (`n` signature transformations and bot checks).
- In standard environments, Node.js or Deno is used.
- On Android ARM64, we bundle **QuickJS** (`qjs`), an ultra-lightweight (1 MB) native ELF binary.
- `yt-dlp` automatically detects `YTDLP_QJS_PATH` and invokes QuickJS to evaluate JavaScript code without any network overhead.

### 2.3 FFmpeg & FFprobe Stream Merging
- High-definition streams (1080p, 1440p, 4K, 8K) on YouTube are provided as separate adaptive video and audio streams (DASH/HLS).
- We bundle native ARM64 **FFmpeg 8.0.1**.
- Once both streams finish downloading to the app staging directory, FFmpeg merges them into an MP4 or MKV container using stream-copy (`-c copy`), completing in seconds without re-encoding quality loss.

### 2.4 Cookie Security & Isolation
- YouTube frequently returns HTTP 429 ("Sign in to confirm you're not a bot") on mobile IP ranges.
- To prevent YouTube response headers from modifying or corrupting the user's master cookie file, `backend_downloader.py` clones the cookie file to an isolated PID-specific file (`ytdlp_isolated_cookies_<pid>.txt`) in `TMPDIR` before execution, and automatically deletes it upon process exit.

### 2.5 Storage Access Framework (SAF)
- Works seamlessly on Android 11, 12, 13, 14, and 15 (Scoped Storage).
- The user can select any folder on internal storage or an SD card using Android's native folder picker (`ACTION_OPEN_DOCUMENT_TREE`).
- Files are staged in the application's private cache while downloading, then streamed via `DocumentFile` output stream directly to the user's chosen folder.

