# Rajan Downloader — Setup & Installation Guide

## 1. Prerequisites

To build and install the APK on an Android device:
- **Operating System**: Windows 10/11, macOS, or Linux.
- **Java Development Kit**: JDK 17 (or newer).
- **Android SDK**: Android API 35 SDK installed (Platform tools, Build tools 35.0.0+).
- **Physical Device or Emulator**: Android 8.0+ (API 26+) with `arm64-v8a` CPU architecture.

---

## 2. Project Directory Structure

```
C:\Users\rajan\Downloads\RajanDownloader\
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   ├── bin/                    # Embedded binaries: python3.11, ffmpeg, qjs
│   │   │   ├── python311-stdlib/       # Python 3.11 standard library + dynload C-extensions
│   │   │   ├── site-packages.zip       # yt-dlp, certifi
│   │   │   ├── scripts/                # backend_downloader.py CLI bridge
│   │   │   └── cookies/                # default_cookies.txt
│   │   ├── java/com/rajandownloader/app/
│   │   │   ├── RajanApp.kt             # Application class initializing runtime
│   │   │   ├── MainActivity.kt         # Jetpack Compose host + Share intent receiver
│   │   │   ├── download/               # DownloadService + Foreground Notification Helper
│   │   │   ├── model/                  # Data classes: MediaMetadata, VideoOption, DownloadProgress
│   │   │   ├── runtime/                # RuntimeManager, RuntimePaths, PythonProcessRunner
│   │   │   ├── settings/               # AppPreferences (DataStore)
│   │   │   ├── storage/                # StorageHelper (SAF + Staging Export)
│   │   │   └── ui/                     # Jetpack Compose UI (MainScreen, Components, Theme)
│   │   └── res/                        # Drawables, mipmaps, strings, xml file provider paths
├── reference/
│   ├── downloader.py                   # Original desktop reference script
│   └── downloader_cleaned.py           # Syntax-verified clean reference copy
├── docs/                               # Architecture, Setup, Verification, Troubleshooting
├── build.gradle.kts                    # Root build configuration
└── settings.gradle.kts                 # Project settings
```

---

## 3. Building the Debug APK

Open PowerShell or terminal in `C:\Users\rajan\Downloads\RajanDownloader` and run:

```powershell
.\gradlew assembleDebug
```

The resulting APK will be placed at:
```
app\build\outputs\apk\debug\app-debug.apk
```

---

## 4. Installing on Device via ADB

1. Connect your Android phone via USB and enable **USB Debugging** in Developer Options.
2. Verify the connected device:
   ```powershell
   adb devices
   ```
3. (Optional) Disable Google Play Protect verification prompt during USB installs:
   ```powershell
   adb shell settings put global verifier_verify_adb_installs 0
   ```
4. Install the debug APK directly:
   ```powershell
   adb install -r -d -g app\build\outputs\apk\debug\app-debug.apk
   ```
5. Launch the app:
   ```powershell
   adb shell monkey -p com.rajandownloader.app.debug -c android.intent.category.LAUNCHER 1
   ```

