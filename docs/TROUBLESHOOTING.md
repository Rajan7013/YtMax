# Rajan Downloader — Troubleshooting & Common Issues

## 1. YouTube Anti-Bot & "Sign in to confirm you're not a bot" (HTTP 429)

### Cause:
YouTube continuously updates anti-scraping and bot-detection heuristics on known datacenter or mobile IP blocks. Unauthenticated requests may be blocked or restricted to lower resolutions (360p).

### Resolution:
Import your own session `cookies.txt`:
1. On your PC (Chrome, Firefox, Edge) or on Android using Kiwi Browser:
   - Install the extension: **"Get cookies.txt LOCALLY"** (open-source) or **"Cookie-Editor"**.
2. Open [youtube.com](https://www.youtube.com/watch?v=) and log into your account.
3. Click the extension and export `cookies.txt` in Netscape format.
4. Transfer the file to your phone.
5. In **Rajan Downloader**, tap the **Cookie** icon in the top right.
6. Tap **Import cookies.txt File** and select your file.
7. Future requests and downloads will use this authenticated session.

> **Note on Security:** Rajan Downloader uses an isolated temporary copy of the cookie file (`TMPDIR/ytdlp_isolated_cookies_<pid>.txt`) for each download process and deletes it automatically on completion. YouTube response headers cannot overwrite your original master cookie file.

---

## 2. Storage & "Permission Denied" Errors

### Cause:
Android 11+ enforces Scoped Storage. Direct POSIX filesystem paths (such as `/sdcard/Download`) cannot be written directly by native processes.

### Resolution:
- Rajan Downloader handles this automatically: downloads are staged in the application's private cache (`$APP_FILES/staging`), then streamed using Android's **Storage Access Framework** (`DocumentFile`) to your selected folder.
- If you change folders, ensure you grant access to the folder in the system file picker dialog.

---

## 3. Foreground Service Stopped in Background

### Cause:
Aggressive OEM battery optimizers (OnePlus OxygenOS, Xiaomi MIUI, Samsung OneUI) may kill background processes.

### Resolution:
1. Open phone **Settings** > **Apps** > **Rajan Downloader**.
2. Tap **Battery** (or Battery usage).
3. Set background usage to **Unrestricted** / **Don't optimize**.

---

## 4. Diagnostics & Reporting Errors

If any error occurs:
1. Tap the **Bug** icon in the top app bar.
2. Tap **Refresh** to run diagnostics.
3. Tap **Copy Logs** to copy the full diagnostic report and runtime logs to the clipboard.

