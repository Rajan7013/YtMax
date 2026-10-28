package com.rajandownloader.app.runtime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class PythonProcessRunner(private val paths: RuntimePaths) {

    companion object {
        private const val TAG = "PythonProcessRunner"
    }

    private var activeProcess: Process? = null
    @Volatile private var isCancelled = false

    suspend fun runPython(
        scriptAndArgs: List<String>,
        cookiesFilePath: String? = null,
        onOutputLine: (String) -> Unit = {}
    ): ProcessResult = withContext(Dispatchers.IO) {
        isCancelled = false
        val command = paths.buildPythonCommand(scriptAndArgs)
        val env = paths.buildEnvironment(cookiesFilePath)

        Log.d(TAG, "Running: ${command.joinToString(" ")}")
        val pb = ProcessBuilder(command)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.scriptsDir))
        pb.redirectErrorStream(false)

        val process = pb.start()
        activeProcess = process

        val stdoutSb = StringBuilder()
        val stderrSb = StringBuilder()

        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderrSb.appendLine(line)
                        Log.d(TAG, "PY.ERR: $line")
                    }
                }
            } catch (_: Exception) {}
        }
        stderrThread.start()

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stdoutSb.appendLine(line)
                    Log.d(TAG, "PY.OUT: $line")
                    onOutputLine(line!!)
                }
            }
        } catch (_: Exception) {}

        val exitCode = process.waitFor()
        stderrThread.join(1000)
        activeProcess = null

        ProcessResult(
            exitCode = if (isCancelled) -1 else exitCode,
            stdout = stdoutSb.toString().trim(),
            stderr = stderrSb.toString().trim()
        )
    }

    fun cancel() {
        isCancelled = true
        try {
            activeProcess?.destroyForcibly()
        } catch (_: Exception) {}
        activeProcess = null
    }
}

