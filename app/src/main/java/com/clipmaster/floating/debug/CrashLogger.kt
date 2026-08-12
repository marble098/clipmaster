package com.clipmaster.floating.debug

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a small rolling file so the Settings
 * "debug report" can surface what actually crashed, without needing a
 * connected debugger or Play Console. Never swallows the crash — it just
 * logs, then hands off to whatever handler was previously installed (the
 * platform default, which shows the "app has stopped" dialog and lets the
 * process die normally).
 */
object CrashLogger {
    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_CHARS = 20_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Logging must never be the reason a crash fails to propagate.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun record(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            appendLine("--- $timestamp on ${thread.name} ---")
            appendLine(throwable.stackTraceToString())
        }
        val file = File(context.filesDir, FILE_NAME)
        val existing = if (file.exists()) file.readText() else ""
        // Newest first, capped so the file can't grow without bound.
        file.writeText((entry + existing).take(MAX_CHARS))
    }

    fun readLog(context: Context): String {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
