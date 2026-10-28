package com.rajandownloader.app.runtime

import android.content.Context
import java.io.File

class RuntimePaths(private val context: Context) {

    val baseDir: File = File(context.filesDir, "runtime")
    val localBinDir: File get() = File(baseDir, "bin")

    // Python
    val pythonBin: String get() = File(localBinDir, "python3.11").absolutePath
    val pythonSharedLib: String get() = File(localBinDir, "libpython3.11.so").absolutePath
    val pythonSharedLib10: String get() = File(localBinDir, "libpython3.11.so.1.0").absolutePath

    // JS Runtimes
    val nativeLibDir: File get() = File(context.applicationInfo.nativeLibraryDir)
    val qjsNativePath: String get() = File(nativeLibDir, "libqjs.so").absolutePath
    val qjsPath: String get() = if (File(qjsNativePath).exists()) qjsNativePath else File(localBinDir, "qjs").absolutePath
    val nodePath: String get() = File(localBinDir, "node").absolutePath

    // FFmpeg & FFprobe
    val ffmpegRealPath: String get() = File(localBinDir, "ffmpeg.bin").absolutePath
    val ffmpegPath: String get() = File(localBinDir, "ffmpeg").absolutePath
    val ffprobePath: String get() = File(localBinDir, "ffprobe").absolutePath

    // OpenSSL
    val libSsl: String get() = File(localBinDir, "libssl.so").absolutePath
    val libSsl3: String get() = File(localBinDir, "libssl.so.3").absolutePath
    val libCrypto: String get() = File(localBinDir, "libcrypto.so").absolutePath
    val libCrypto3: String get() = File(localBinDir, "libcrypto.so.3").absolutePath
    val libFfi: String get() = File(localBinDir, "libffi.so").absolutePath

    // Python Home & Stdlib
    val pythonHomeDir: File get() = File(baseDir, "python311-home")
    val pythonHome: String get() = pythonHomeDir.absolutePath
    val pythonStdlib: String get() = File(pythonHomeDir, "lib/python3.11").absolutePath
    val pythonDynload: String get() = File(pythonHomeDir, "lib/python3.11/lib-dynload").absolutePath

    // Packages & Scripts
    val sitePackages: String get() = File(baseDir, "site-packages").absolutePath
    val scriptsDir: String get() = File(baseDir, "scripts").absolutePath
    val backendScript: String get() = File(scriptsDir, "backend_downloader.py").absolutePath

    // Temp & Cookies
    val tempDir: String get() = File(baseDir, "tmp").absolutePath
    val defaultCookiesFile: File get() = File(context.filesDir, "cookies.txt")
    val readyMarker: String get() = File(baseDir, ".ready").absolutePath

    // Android dynamic linker
    val linkerPath: String
        get() = when {
            File("/system/bin/linker64").exists() -> "/system/bin/linker64"
            File("/system/bin/linker").exists() -> "/system/bin/linker"
            else -> "/system/bin/linker64"
        }

    fun ensureDirectories() {
        listOf(
            baseDir,
            localBinDir,
            pythonHomeDir,
            File(pythonStdlib),
            File(pythonDynload),
            File(sitePackages),
            File(scriptsDir),
            File(tempDir)
        ).forEach { it.mkdirs() }
    }

    fun buildEnvironment(cookiesFilePath: String? = null): Map<String, String> {
        val env = mutableMapOf<String, String>()

        env["LD_LIBRARY_PATH"] = listOf(
            localBinDir.absolutePath,
            pythonDynload
        ).joinToString(":")

        env["PYTHONHOME"] = pythonHome
        env["PYTHONPATH"] = listOf(
            pythonStdlib,
            pythonDynload,
            sitePackages,
            scriptsDir
        ).joinToString(":")

        env["PATH"] = listOf(
            localBinDir.absolutePath,
            "/system/bin",
            "/vendor/bin"
        ).joinToString(":")

        env["TMPDIR"] = tempDir
        env["HOME"] = baseDir.absolutePath
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        env["PYTHONUNBUFFERED"] = "1"

        val certFile = File(sitePackages, "certifi/cacert.pem")
        if (certFile.exists()) {
            env["SSL_CERT_FILE"] = certFile.absolutePath
        }

        val qjsFile = File(qjsPath)
        if (qjsFile.exists()) {
            env["YTDLP_QJS_PATH"] = qjsFile.absolutePath
        }
        val nodeFile = File(nodePath)
        if (nodeFile.exists()) {
            env["YTDLP_NODE_PATH"] = nodeFile.absolutePath
        }

        val ffmpegFile = File(ffmpegPath)
        if (ffmpegFile.exists()) {
            env["YTDLP_FFMPEG_PATH"] = ffmpegFile.absolutePath
        }

        val effectiveCookies = cookiesFilePath?.takeIf { File(it).exists() }

        if (!effectiveCookies.isNullOrBlank()) {
            env["YTDLP_COOKIES_FILE"] = effectiveCookies
        }

        return env
    }

    fun buildPythonCommand(args: List<String>): List<String> {
        return listOf(linkerPath, pythonBin) + args
    }

    fun buildFfmpegCommand(args: List<String>): List<String> {
        return listOf(linkerPath, ffmpegRealPath) + args
    }
}

