package com.pca.assistant

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
 * surface on a phone-only install.
 *
 * Samsung's One UI blocks user access to the Android/data tree on
 * Android 13+,
 * so we publish the crash report into the system Downloads folder via
 * MediaStore (no extra permissions required on API 29+). We also keep a
 * copy under the app's external files dir as a fallback for non-Samsung
 * vendors that allow it.
 *
 * The previous platform default uncaught-exception handler is still invoked
 * after the write, so the system's "this app stopped" dialog still fires —
 * we just capture state on the way down.
 */
object CrashLogger {

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
                .onFailure { Log.e(TAG, "crash logger failed", it) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ROOT).format(Date())
        val body = buildString {
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

        // Primary surface: system Downloads — visible in My Files on every
        // Samsung One UI without any developer setting.
        runCatching { writeToDownloads(context, body) }
            .onFailure { Log.w(TAG, "Downloads write failed: ${it.message}") }

        // Fallback: app-private external files dir. Older phones, non-Samsung
        // skins, and rooted devices can still read this with a file manager.
        runCatching {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            File(baseDir, FILE_NAME).writeText(body)
        }
    }

    private fun writeToDownloads(context: Context, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            // Best-effort delete any prior copy so the entry is fresh, then insert.
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf(FILE_NAME),
                )
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore.insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            // Pre-Q: write directly to /sdcard/Download (legacy path; rare on PCA's minSdk=30).
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(body)
        }
    }

    private const val FILE_NAME = "pca_last_crash.txt"
    private const val TAG = "PcaCrash"
}
