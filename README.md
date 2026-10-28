# Rajan Downloader 🎬

A standalone, high-performance Android application built with **Jetpack Compose** and an **embedded CPython + yt-dlp + FFmpeg runtime** for downloading YouTube videos up to 8K Ultra HD entirely on-device.

No Termux, no root, no external server, and no cookies required.

---

## ✨ Features

- **True 100% On-Device Standalone**: Downloads and merges video/audio streams directly on Android. No PC, VPS, or Termux required.
- **Up to 8K Resolution**: Supports all available video qualities:
  - 8K (4320p)
  - 4K (2160p)
  - 2K (1440p)
  - 1080p Full HD
  - 720p HD
  - 480p / 360p / 240p / 144p
- **Lossless Stream Merging**: Bundles native **FFmpeg 8.0.1** to losslessly merge `bestvideo` and `bestaudio` without transcoding.
- **Signature Decryption with QuickJS**: Bundles **QuickJS** (`libqjs.so`) to solve YouTube's client challenges locally without Node.js.
- **Audio Extraction Mode**: Preserves original audio streams (Opus/AAC/M4A) without lossy conversion.
- **Android Scoped Storage (Android 10 - 15+)**: Automatically exports completed downloads to the public `Download/` directory via `MediaStore`, immediately visible in the gallery and file manager.
- **Foreground Service**: Reliable background downloads with a persistent progress notification.
- **Share Intent Support**: Paste links directly or share videos from the official YouTube app.

---

## 🛠️ Architecture & Tech Stack

```
Jetpack Compose Material 3 UI
          │ (Kotlin Coroutines / StateFlow)
Download Foreground Service
          │ (MediaStore Scoped Storage)
Embedded CPython 3.11 Runtime (/system/bin/linker64)
  ├── yt-dlp Engine (2026.08.19)
  ├── QuickJS (libqjs.so - JavaScript Challenge Solver)
  └── FFmpeg 8.0.1 (Lossless Stream Merger)
```

| Component | Technology |
|---|---|
| **Mobile Interface** | Kotlin 2.0, Jetpack Compose, Material 3, Coil |
| **Download Pipeline** | Android Foreground Service (`DATA_SYNC`), MediaStore API |
| **Python Runtime** | Embedded CPython 3.11.11 (Linux ARM64-v8a) |
| **Extractor** | yt-dlp 2026.08.19 |
| **JS Engine** | Embedded QuickJS (`libqjs.so`) |
| **Media Processor** | Embedded FFmpeg 8.0.1 & FFprobe |

---

## 🚀 Building & Installing

### Prerequisites
- Android Studio Ladybug / Meerkat or Command Line Tools
- JDK 17
- Android SDK (API 35, Min SDK 26)
- Connected physical ARM64 Android device (Android 8.0+)

### Build APK
```bash
# Clone the repository
git clone https://github.com/<your-username>/rajan-downloader.git
cd rajan-downloader

# Assemble Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

