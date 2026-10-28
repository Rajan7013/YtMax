import os
import sys
import shutil
import threading
import traceback
import tkinter as tk
from tkinter import messagebox, ttk
from io import BytesIO
from PIL import Image, ImageTk
import requests
from yt_dlp import YoutubeDL

# Hardcoded target download path
DOWNLOAD_DIR = r"C:\Users\rajan\Downloads\Video"


class QuietLogger:
    """Suppresses noisy internal extractor warnings and PO token spam."""
    def debug(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        print(f"[yt-dlp Error] {msg}", file=sys.stderr)


def get_base_ydl_opts():
    """Builds base yt-dlp options, auto-detecting node runtime if available."""
    opts = {
        'quiet': True,
        'no_warnings': True,
        'logger': QuietLogger(),
    }
    # If node is installed, configure it to eliminate missing JS runtime warnings
    if shutil.which("node"):
        opts['js_runtimes'] = {'node': {}}
    return opts


class YouTubeDownloaderApp(tk.Tk):
    def __init__(self):
        try:
            super().__init__()
            self.title("High Quality YouTube MP4 Downloader")
            self.geometry("780x720")
            self.minsize(720, 640)
            self.configure(bg="#f3f4f6")

            self.video_url = ""
            self.formats_data = []
            self.selected_format_id = None

            # Ensure the download directory exists
            if not os.path.exists(DOWNLOAD_DIR):
                os.makedirs(DOWNLOAD_DIR, exist_ok=True)

            self.create_widgets()
        except Exception as e:
            print("\n[WINDOW INITIALIZATION CRASH]:")
            traceback.print_exc()
            sys.exit(1)

    def create_widgets(self):
        # --- Top Header Banner ---
        header_frame = tk.Frame(self, bg="#2563eb", pady=12)
        header_frame.pack(fill="x")
        tk.Label(
            header_frame,
            text="YouTube MP4 Downloader",
            font=("Segoe UI", 16, "bold"),
            fg="white",
            bg="#2563eb"
        ).pack()
        tk.Label(
            header_frame,
            text=f"Save Location: {DOWNLOAD_DIR}",
            font=("Segoe UI", 9),
            fg="#dbeafe",
            bg="#2563eb"
        ).pack(pady=(2, 0))

        # --- URL Input Frame ---
        url_frame = tk.LabelFrame(
            self,
            text=" YouTube Video Link ",
            font=("Segoe UI", 9, "bold"),
            bg="#ffffff",
            padx=12,
            pady=10
        )
        url_frame.pack(fill="x", padx=16, pady=10)

        self.url_entry = ttk.Entry(url_frame, font=("Segoe UI", 10))
        self.url_entry.pack(side="left", padx=(0, 10), expand=True, fill="x")
        self.url_entry.focus()

        self.fetch_btn = ttk.Button(
            url_frame,
            text="Fetch Metadata",
            command=self.start_fetch_metadata
        )
        self.fetch_btn.pack(side="right")

        # --- Video Metadata & Options Frame ---
        self.meta_frame = tk.LabelFrame(
            self,
            text=" Video Insights & Quality Options ",
            font=("Segoe UI", 9, "bold"),
            bg="#ffffff",
            padx=12,
            pady=10
        )
        self.meta_frame.pack(fill="both", expand=True, padx=16, pady=5)
        self.meta_frame.columnconfigure(1, weight=1)

        # Left: Thumbnail Display
        self.thumb_label = tk.Label(
            self.meta_frame,
            text="[ Thumbnail Area ]",
            bg="#e5e7eb",
            fg="#6b7280",
            font=("Segoe UI", 9),
            width=26,
            height=8,
            relief="solid",
            bd=1
        )
        self.thumb_label.grid(row=0, column=0, rowspan=4, padx=(0, 14), pady=5, sticky="nw")

        # Right: Metadata Fields
        self.title_label = tk.Label(
            self.meta_frame,
            text="Title: N/A",
            bg="#ffffff",
            font=("Segoe UI", 10, "bold"),
            anchor="w",
            justify="left",
            wraplength=460
        )
        self.title_label.grid(row=0, column=1, sticky="nw", pady=(2, 4))

        self.tags_label = tk.Label(
            self.meta_frame,
            text="Hashtags: N/A",
            bg="#ffffff",
            fg="#2563eb",
            font=("Segoe UI", 9),
            anchor="w",
            justify="left",
            wraplength=460
        )
        self.tags_label.grid(row=1, column=1, sticky="nw", pady=2)

        self.channel_label = tk.Label(
            self.meta_frame,
            text="Channel / Duration: N/A",
            bg="#ffffff",
            fg="#4b5563",
            font=("Segoe UI", 9),
            anchor="w",
            justify="left"
        )
        self.channel_label.grid(row=2, column=1, sticky="nw", pady=2)

        self.desc_label = tk.Label(
            self.meta_frame,
            text="Description: Paste a YouTube link and click 'Fetch Metadata'.",
            bg="#ffffff",
            fg="#4b5563",
            font=("Segoe UI", 8),
            anchor="w",
            justify="left",
            wraplength=460,
            height=3
        )
        self.desc_label.grid(row=3, column=1, sticky="nw", pady=(2, 6))

        # Dropdown Selection
        tk.Label(
            self.meta_frame,
            text="Choose Targeted Quality (Highest to Lowest):",
            bg="#ffffff",
            font=("Segoe UI", 9, "bold")
        ).grid(row=4, column=0, columnspan=2, sticky="w", pady=(10, 4))

        self.quality_combo = ttk.Combobox(
            self.meta_frame,
            state="disabled",
            font=("Segoe UI", 9)
        )
        self.quality_combo.grid(row=5, column=0, columnspan=2, sticky="ew", pady=(0, 5))

        # --- Live Progress Monitor Dashboard ---
        self.prog_frame = tk.LabelFrame(
            self,
            text=" Download Monitor Engine ",
            font=("Segoe UI", 9, "bold"),
            bg="#ffffff",
            padx=12,
            pady=10
        )
        self.prog_frame.pack(fill="x", padx=16, pady=10)

        self.progress_bar = ttk.Progressbar(
            self.prog_frame,
            orient="horizontal",
            mode="determinate"
        )
        self.progress_bar.pack(fill="x", pady=(2, 8))

        lbl_container = tk.Frame(self.prog_frame, bg="#ffffff")
        lbl_container.pack(fill="x")

        self.lbl_ratio = tk.Label(
            lbl_container,
            text="Data Processed: 0.00 / 0.00 MB",
            bg="#ffffff",
            font=("Segoe UI", 9)
        )
        self.lbl_ratio.pack(side="left")

        self.lbl_percentage = tk.Label(
            lbl_container,
            text="Progress: 0%",
            bg="#ffffff",
            font=("Segoe UI", 9, "bold"),
            fg="#2563eb"
        )
        self.lbl_percentage.pack(side="left", expand=True)

        self.lbl_speed_eta = tk.Label(
            lbl_container,
            text="Speed: N/A  |  ETA: N/A",
            bg="#ffffff",
            font=("Segoe UI", 9)
        )
        self.lbl_speed_eta.pack(side="right")

        self.status_note = tk.Label(
            self.prog_frame,
            text="Status: Ready",
            bg="#ffffff",
            fg="#6b7280",
            font=("Segoe UI", 8, "italic"),
            anchor="w"
        )
        self.status_note.pack(fill="x", pady=(6, 0))

        # Action Button
        self.download_btn = ttk.Button(
            self,
            text="Download Target Quality (MP4)",
            state="disabled",
            command=self.start_download_pipeline
        )
        self.download_btn.pack(pady=(0, 15), ipady=5, ipadx=10)

    # --- Fetch Metadata ---
    def start_fetch_metadata(self):
        url = self.url_entry.get().strip()
        if not url:
            messagebox.showerror("Error", "Please input a valid YouTube URL first.")
            return

        self.video_url = url
        self.fetch_btn.config(state="disabled")
        self.download_btn.config(state="disabled")
        self.status_note.config(text="Status: Contacting YouTube to extract metadata...", fg="#2563eb")

        threading.Thread(target=self._fetch_metadata_worker, daemon=True).start()

    def _fetch_metadata_worker(self):
        ydl_opts = get_base_ydl_opts()
        ydl_opts['skip_download'] = True

        try:
            with YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(self.video_url, download=False)

            title = info.get('title', 'Unknown Title')
            description = info.get('description', '') or 'No description layout available.'
            tags = info.get('tags', []) or []
            hashtag_str = " ".join([f"#{t}" for t in tags[:6]]) if tags else "None parsed"
            thumbnail_url = info.get('thumbnail')
            uploader = info.get('uploader', 'Unknown Channel')
            duration_secs = info.get('duration', 0) or 0
            duration_str = f"{duration_secs // 60}m {duration_secs % 60}s" if duration_secs else "Live / Unknown"

            # Parse video formats & collect unique resolutions sorted descending
            raw_formats = info.get('formats', [])
            seen_heights = set()
            resolution_list = []

            for f in raw_formats:
                height = f.get('height')
                vcodec = f.get('vcodec')
                if height and vcodec != 'none':
                    if height not in seen_heights:
                        seen_heights.add(height)
                        resolution_list.append(height)

            # Sort descending: e.g. 4320 (8K), 2160 (4K), 1440 (2K), 1080, 720, 480, 360, 240, 144
            resolution_list.sort(reverse=True)

            processed_formats = []

            # 1. Highest available maximum quality
            processed_formats.append({
                "id": "bestvideo+bestaudio/best",
                "label": "★ Absolute Maximum Quality Available (Auto Hybrid Merge Setup)"
            })

            # 2. Formats for each detected resolution
            resolution_names = {
                4320: "8K Ultra HD (4320p)",
                2160: "4K Ultra HD (2160p)",
                1440: "2K Quad HD (1440p)",
                1080: "1080p (Full HD)",
                720: "720p (HD)",
                480: "480p (SD)",
                360: "360p",
                240: "240p",
                144: "144p"
            }

            for h in resolution_list:
                res_title = resolution_names.get(h, f"{h}p")
                processed_formats.append({
                    "id": f"bestvideo[height<={h}]+bestaudio/best[height<={h}]/best",
                    "label": f"Resolution: {res_title}"
                })

            # 3. Audio-only option
            processed_formats.append({
                "id": "bestaudio/best",
                "label": "Audio Only (Best Quality MP3/M4A)"
            })

            self.formats_data = processed_formats
            self.after(
                0,
                self.update_ui_metadata,
                title,
                hashtag_str,
                description,
                thumbnail_url,
                uploader,
                duration_str
            )

        except Exception as e:
            self.after(0, self.update_ui_error, str(e))

    def update_ui_metadata(self, title, hashtags, description, thumb_url, uploader, duration):
        display_title = title if len(title) <= 90 else title[:87] + "..."
        self.title_label.config(text=f"Title: {display_title}")
        self.tags_label.config(text=f"Tags: {hashtags}")
        self.channel_label.config(text=f"Channel: {uploader}  |  Duration: {duration}")

        clean_desc = " ".join(description.split())
        display_desc = clean_desc if len(clean_desc) <= 180 else clean_desc[:177] + "..."
        self.desc_label.config(text=f"Description snippet: {display_desc}")

        if thumb_url:
            threading.Thread(target=self._load_thumbnail, args=(thumb_url,), daemon=True).start()

        combo_values = [item['label'] for item in self.formats_data]
        self.quality_combo.config(values=combo_values, state="readonly")
        if combo_values:
            self.quality_combo.current(0)

        self.fetch_btn.config(state="normal")
        self.download_btn.config(state="normal")
        self.status_note.config(text="Status: Video metadata retrieved successfully. Ready to download.", fg="green")

    def _load_thumbnail(self, thumb_url):
        try:
            res = requests.get(thumb_url, timeout=6)
            if res.status_code == 200:
                img_data = Image.open(BytesIO(res.content))
                img_data = img_data.resize((190, 110), Image.Resampling.LANCZOS)
                thumb_img = ImageTk.PhotoImage(img_data)

                def apply_thumb():
                    self.thumb_label.config(image=thumb_img, text="", relief="flat")
                    self.thumb_label.image = thumb_img

                self.after(0, apply_thumb)
        except Exception:
            self.after(0, lambda: self.thumb_label.config(text="[Thumb Render Error]"))

    def update_ui_error(self, error_msg):
        self.fetch_btn.config(state="normal")
        self.status_note.config(text="Status: Failed to extract video metadata.", fg="red")
        messagebox.showerror(
            "Extraction Error",
            f"Could not pull video layout data.\n\nReason:\n{error_msg[:300]}"
        )

    # --- Download Pipeline Execution ---
    def start_download_pipeline(self):
        selected_index = self.quality_combo.current()
        if selected_index == -1 or not self.formats_data:
            messagebox.showwarning("Quality Selection", "Please choose a targeted quality first.")
            return

        self.selected_format_id = self.formats_data[selected_index]['id']
        selected_label = self.formats_data[selected_index]['label']

        self.download_btn.config(state="disabled")
        self.fetch_btn.config(state="disabled")
        self.quality_combo.config(state="disabled")

        self.progress_bar['value'] = 0
        self.lbl_percentage.config(text="Progress: 0%")
        self.lbl_ratio.config(text="Data Processed: 0.00 / 0.00 MB")
        self.lbl_speed_eta.config(text="Speed: Connecting...  |  ETA: Calculating...")
        self.status_note.config(text=f"Status: Downloading [{selected_label}]...", fg="#2563eb")

        threading.Thread(target=self._download_worker, daemon=True).start()

    def _download_worker(self):
        def progress_hook(d):
            if d.get('status') == 'downloading':
                total_bytes = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
                downloaded_bytes = d.get('downloaded_bytes', 0)

                total_mb = total_bytes / (1024 * 1024) if total_bytes else 0.0
                downloaded_mb = downloaded_bytes / (1024 * 1024) if downloaded_bytes else 0.0

                if total_bytes > 0:
                    percentage = int((downloaded_bytes / total_bytes) * 100)
                else:
                    percentage = 0

                speed = d.get('_speed_str', 'N/A')
                eta = d.get('_eta_str', 'N/A')

                self.after(
                    0,
                    self.update_progress_dashboard,
                    downloaded_mb,
                    total_mb,
                    percentage,
                    speed,
                    eta
                )

            elif d.get('status') == 'finished':
                self.after(0, self.update_progress_stitching)

        ydl_opts = get_base_ydl_opts()
        ydl_opts.update({
            'format': self.selected_format_id,
            'outtmpl': os.path.join(DOWNLOAD_DIR, '%(title)s.%(ext)s'),
            'merge_output_format': 'mp4',
            'writedescription': True,
            'writeinfojson': True,
            'embedmetadata': True,
            'progress_hooks': [progress_hook],
        })

        try:
            with YoutubeDL(ydl_opts) as ydl:
                ydl.download([self.video_url])

            self.after(0, self.update_progress_finished)

        except Exception as e:
            err_msg = str(e)
            self.after(0, self.update_download_error, err_msg)

    def update_progress_dashboard(self, downloaded, total, percentage, speed, eta):
        self.progress_bar['value'] = percentage
        self.lbl_percentage.config(text=f"Progress: {percentage}%")
        if total > 0:
            self.lbl_ratio.config(text=f"Data Processed: {downloaded:.2f} / {total:.2f} MB")
        else:
            self.lbl_ratio.config(text=f"Data Processed: {downloaded:.2f} MB")
        self.lbl_speed_eta.config(text=f"Speed: {speed}  |  ETA: {eta}")

    def update_progress_stitching(self):
        self.progress_bar['value'] = 100
        self.lbl_percentage.config(text="Progress: 100% (Muxing MP4...)")
        self.status_note.config(
            text="Status: Download completed! Stitching audio/video streams into final MP4...",
            fg="#d97706"
        )

    def update_progress_finished(self):
        self.progress_bar['value'] = 100
        self.lbl_percentage.config(text="Progress: 100% (Complete!)")
        self.status_note.config(text="Status: Download and packaging completed successfully!", fg="green")
        self.download_btn.config(state="normal")
        self.fetch_btn.config(state="normal")
        self.quality_combo.config(state="readonly")

        messagebox.showinfo(
            "Success",
            f"Video tracks fetched, cleanly packaged as MP4, and saved to:\n\n{DOWNLOAD_DIR}"
        )

    def update_download_error(self, err_msg):
        self.status_note.config(text="Status: Download interrupted or failed.", fg="red")
        self.download_btn.config(state="normal")
        self.fetch_btn.config(state="normal")
        self.quality_combo.config(state="readonly")

        messagebox.showerror(
            "Download Error",
            f"Stitched process broken:\n\n{err_msg[:300]}"
        )


if __name__ == "__main__":
    app = YouTubeDownloaderApp()
    app.mainloop()
