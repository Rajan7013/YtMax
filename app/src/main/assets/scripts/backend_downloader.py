#!/usr/bin/env python3
"""
Rajan Downloader - Android Embedded Python Downloader Backend
Derived from original desktop script (downloader.py).
Handles metadata extraction, format parsing, live progress tracking,
and download execution for Jetpack Compose UI.
"""

from __future__ import annotations

import argparse
import atexit
import json
import os
import re
import subprocess
import sys
import traceback

# Intercept subprocess.Popen to route Android app-dir scripts through /system/bin/sh
_orig_popen = subprocess.Popen

class AndroidSubprocessPopen(_orig_popen):
    def __init__(self, cmd, *args, **kwargs):
        if isinstance(cmd, (list, tuple)) and len(cmd) > 0:
            exe = str(cmd[0])
            if exe in ("ffmpeg", "ffprobe", "node") or exe.endswith(("/ffmpeg", "/ffprobe", "/node")):
                cmd = ["/system/bin/sh", exe] + list(cmd[1:])
        super().__init__(cmd, *args, **kwargs)

subprocess.Popen = AndroidSubprocessPopen

try:
    import yt_dlp
except ImportError:
    print(json.dumps({"status": "error", "reason": "IMPORT_ERROR", "details": traceback.format_exc()}), flush=True)
    sys.exit(1)


# Default headers simulating a standard desktop browser
_DEFAULT_HEADERS = {
    'User-Agent': (
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/124.0.0.0 Safari/537.36'
    ),
    'Accept-Language': 'en-US,en;q=0.9',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
}


def sanitize_filename(name: str) -> str:
    """Sanitizes filename for Android filesystem."""
    sanitized = re.sub(r'[\\/*?:"<>|]', "", name)
    sanitized = re.sub(r'[\r\n\t]', " ", sanitized)
    sanitized = " ".join(sanitized.split())
    return sanitized[:120].strip() or "video"


def progress_hook(d: dict):
    """Outputs machine-readable progress lines for Kotlin foreground service."""
    status = d.get('status')
    if status == 'downloading':
        total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
        downloaded = d.get('downloaded_bytes', 0)
        percentage = int((downloaded / total) * 100) if total > 0 else 0
        speed = d.get('_speed_str', 'N/A')
        eta = d.get('_eta_str', 'N/A')

        data = {
            "type": "progress",
            "percentage": percentage,
            "downloaded_bytes": downloaded,
            "total_bytes": total,
            "speed_str": speed,
            "eta_str": eta,
            "stage": "downloading"
        }
        print("\nPROGRESS:" + json.dumps(data), flush=True)

    elif status == 'finished':
        data = {
            "type": "progress",
            "percentage": 100,
            "stage": "merging"
        }
        print("\nPROGRESS:" + json.dumps(data), flush=True)


def apply_runtime_opts(ydl_opts: dict) -> dict:
    """Configures QuickJS, FFmpeg, and isolated cookies from environment."""
    qjs_path = os.environ.get("YTDLP_QJS_PATH", "").strip()
    node_path = os.environ.get("YTDLP_NODE_PATH", "").strip()
    ffmpeg_path = os.environ.get("YTDLP_FFMPEG_PATH", "").strip()
    cookies_path = os.environ.get("YTDLP_COOKIES_FILE", "").strip()

    runtimes = {}
    if qjs_path and os.path.exists(qjs_path):
        runtimes['quickjs'] = {'path': qjs_path}
    if node_path and os.path.exists(node_path):
        runtimes['node'] = {'path': node_path}
    if runtimes:
        ydl_opts['js_runtimes'] = runtimes

    if ffmpeg_path and os.path.exists(ffmpeg_path):
        ydl_opts['ffmpeg_location'] = ffmpeg_path

    # Enable EJS remote components so QuickJS can execute yt-dlp challenge solver
    ydl_opts['remote_components'] = ['ejs:github']

    # Isolate cookies so yt-dlp response headers never mutate the user's master file
    if cookies_path and os.path.exists(cookies_path):
        try:
            temp_dir = os.environ.get("TMPDIR", "/data/local/tmp")
            os.makedirs(temp_dir, exist_ok=True)
            isolated_file = os.path.join(temp_dir, f"ytdlp_isolated_cookies_{os.getpid()}.txt")
            with open(cookies_path, 'rb') as src, open(isolated_file, 'wb') as dst:
                dst.write(src.read())
            atexit.register(lambda: os.remove(isolated_file) if os.path.exists(isolated_file) else None)
            ydl_opts['cookiefile'] = isolated_file
        except Exception:
            ydl_opts['cookiefile'] = cookies_path

    ydl_opts['http_headers'] = _DEFAULT_HEADERS.copy()
    return ydl_opts


def cmd_info(url: str):
    """Fetches video metadata and formats, printing JSON to stdout."""
    ydl_opts = {
        'format': 'all',
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'no_color': True,
        'noplaylist': True,
        'retries': 3,
        'socket_timeout': 20,
    }
    apply_runtime_opts(ydl_opts)

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

        if not info:
            print(json.dumps({"status": "error", "reason": "EMPTY_METADATA"}), flush=True)
            return

        title = info.get('title', 'Unknown Title')
        description = info.get('description', '') or ''
        tags = info.get('tags', []) or []
        thumbnail = info.get('thumbnail')
        uploader = info.get('uploader') or info.get('channel') or 'Unknown Channel'
        duration = info.get('duration', 0) or 0
        video_id = info.get('id', '')

        # Collect unique video resolutions descending
        raw_formats = info.get('formats', [])
        seen_heights = set()
        resolution_list = []

        # Filter out storyboard thumbnail sheets (like 27, 45, 90, 180 or mhtml)
        for f in raw_formats:
            if f.get('format_note') == 'storyboard' or f.get('protocol') == 'mhtml' or f.get('ext') == 'mhtml':
                continue
            h = f.get('height')
            vc = f.get('vcodec')
            if h and vc and vc != 'none' and h >= 144:
                if h not in seen_heights:
                    seen_heights.add(h)
                    resolution_list.append(h)

        resolution_list.sort(reverse=True)

        resolution_names = {
            4320: "8K (4320p)",
            2160: "4K (2160p)",
            1440: "2K (1440p)",
            1080: "1080p (FHD)",
            720: "720p (HD)",
            480: "480p (SD)",
            360: "360p",
            240: "240p",
            144: "144p",
        }

        # Build options for UI selector
        options = []
        # 1. Highest available maximum quality
        options.append({
            "id": "bestvideo+bestaudio/best",
            "label": "Best Available (Max)",
            "height": 0,
            "mode": "merged"
        })

        # 2. Each detected height
        for h in resolution_list:
            lbl = resolution_names.get(h, f"{h}p")
            options.append({
                "id": f"bestvideo[height<={h}]+bestaudio/best[height<={h}]/best",
                "label": lbl,
                "height": h,
                "mode": "merged"
            })

        # 3. Audio only
        options.append({
            "id": "bestaudio/best",
            "label": "Audio Only",
            "height": 0,
            "mode": "audio_only"
        })

        out = {
            "status": "success",
            "id": video_id,
            "title": title,
            "description": description[:300],
            "uploader": uploader,
            "duration": duration,
            "thumbnail": thumbnail,
            "tags": tags[:6] if tags else [],
            "options": options,
            "resolutions": resolution_list
        }
        print("METADATA:" + json.dumps(out, ensure_ascii=False), flush=True)

    except Exception as e:
        print(json.dumps({
            "status": "error",
            "reason": "METADATA_FETCH_FAILED",
            "details": str(e),
            "traceback": traceback.format_exc()
        }, ensure_ascii=False), flush=True)


def cmd_download(args):
    """Executes the download based on requested mode and quality."""
    url = args.url
    mode = args.mode  # merged, audio_only, separate
    fmt = args.format_id or "bestvideo+bestaudio/best"
    output_dir = args.output_dir
    filename_base = sanitize_filename(args.filename_base or "download")
    container = (args.container or "mp4").lower()

    os.makedirs(output_dir, exist_ok=True)

    base_opts = {
        'progress_hooks': [progress_hook],
        'quiet': True,
        'no_warnings': False,
        'no_color': True,
        'overwrites': True,
        'noplaylist': True,
        'retries': 5,
        'fragment_retries': 5,
        'socket_timeout': 30,
    }
    apply_runtime_opts(base_opts)

    try:
        if mode in ("audio", "audio_only"):
            # Best audio only, preserving original codec
            base_opts['format'] = 'bestaudio/best'
            base_opts['outtmpl'] = os.path.join(output_dir, f"{filename_base}.%(ext)s")
            with yt_dlp.YoutubeDL(base_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(info)
                print(f"\nCOMPLETE:{path}\n", flush=True)

        elif mode == "separate":
            # Download video track and audio track as separate files
            # 1. Video track
            vid_opts = base_opts.copy()
            vid_fmt = fmt.split('+')[0] if '+' in fmt else fmt
            if vid_fmt == "best":
                vid_fmt = "bestvideo/best"
            vid_opts['format'] = vid_fmt
            vid_opts['outtmpl'] = os.path.join(output_dir, f"{filename_base}_video.%(ext)s")
            with yt_dlp.YoutubeDL(vid_opts) as ydl_v:
                info_v = ydl_v.extract_info(url, download=True)
                v_path = ydl_v.prepare_filename(info_v)

            # 2. Audio track
            aud_opts = base_opts.copy()
            aud_opts['format'] = 'bestaudio/best'
            aud_opts['outtmpl'] = os.path.join(output_dir, f"{filename_base}_audio.%(ext)s")
            with yt_dlp.YoutubeDL(aud_opts) as ydl_a:
                info_a = ydl_a.extract_info(url, download=True)
                a_path = ydl_a.prepare_filename(info_a)

            print(f"\nCOMPLETE_SEPARATE:{v_path}|{a_path}\n", flush=True)

        else:
            # Merged: bestvideo+bestaudio merged via FFmpeg into requested container
            base_opts['format'] = fmt
            base_opts['outtmpl'] = os.path.join(output_dir, f"{filename_base}.%(ext)s")
            base_opts['merge_output_format'] = container if container in ('mp4', 'mkv') else 'mp4'

            with yt_dlp.YoutubeDL(base_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(info)
                # If merged into container, check final filename
                merged_path = os.path.splitext(path)[0] + f".{container}"
                final_path = merged_path if os.path.exists(merged_path) else path
                print(f"\nCOMPLETE:{final_path}\n", flush=True)

    except Exception as e:
        err_str = str(e)
        if "403" in err_str or "cookies" in err_str.lower() or "forbidden" in err_str.lower():
            err_str = "YouTube Anti-Bot (HTTP 403): Active cookies required. Please tap the Cookie icon (top right) to import a fresh cookies.txt from your browser."
        print(json.dumps({
            "status": "error",
            "reason": "DOWNLOAD_FAILED",
            "details": err_str,
            "traceback": traceback.format_exc()
        }, ensure_ascii=False), flush=True)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="Rajan Downloader CLI Engine")
    subparsers = parser.add_subparsers(dest="cmd")

    # info
    info_parser = subparsers.add_parser("info")
    info_parser.add_argument("url", help="YouTube video URL")

    # download
    dl_parser = subparsers.add_parser("download")
    dl_parser.add_argument("url", help="YouTube video URL")
    dl_parser.add_argument("--mode", default="merged", choices=["merged", "audio", "audio_only", "separate"])
    dl_parser.add_argument("--format-id", default="bestvideo+bestaudio/best")
    dl_parser.add_argument("--output-dir", required=True)
    dl_parser.add_argument("--filename-base", default="video")
    dl_parser.add_argument("--container", default="mp4", choices=["mp4", "mkv"])

    args = parser.parse_args()

    if args.cmd == "info":
        cmd_info(args.url)
    elif args.cmd == "download":
        cmd_download(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

