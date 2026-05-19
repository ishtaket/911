package com.pca.assistant

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes any unhandled exception to a plain-text file the user can open from
 * Samsung's My Files app — without `adb logcat` we have no other diagnostic
 * surface on a phone-only install. The path is
 * `Internal storage/Android/data/com.pca.assistant/files/last_crash.txt`,
 * which scoped storage on Android 11+ exposes without extra permissions.
 *
 * The previous platform default uncaught-exception handler is still invoked
 * after the write, so the system's "this app stopped" dialog still fires —
 * we just capture state on the way down.
 */
object CrashLogger {

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val crashFile = File(baseDir, FILE_NAME)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(crashFile, thread, throwable) }
                .onFailure { Log.e(TAG, "crash logger failed", it) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(file: File, thread: Thread, throwable: Throwable) {
        file.parentFile?.mkdirs()
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ROOT).format(Date())
        file.writeText(
            buildString {
                appendLine("PCA crash report")
                appendLine("================")
                appendLine("when:    $stamp")
                appendLine("thread:  ${thread.name}")
                appendLine("model:   ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("sdk:     ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
                appendLine("build:   ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine()
                append(sw.toString())
            }
        )
    }

    private const val FILE_NAME = "last_crash.txt"
    private const val TAG = "PcaCrash"
}
