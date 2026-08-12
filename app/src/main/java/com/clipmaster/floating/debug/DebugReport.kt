package com.clipmaster.floating.debug

import android.content.Context
import android.os.Build
import com.clipmaster.floating.BuildConfig
import com.clipmaster.floating.ClipMasterApp
import com.clipmaster.floating.settings.SettingsStore
import com.clipmaster.floating.util.PermissionHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a plain-text snapshot of app/device state, permission status, and
 * any recorded crashes — everything needed to diagnose a bug report without
 * a connected debugger. Shown, copied, or shared from Settings.
 */
object DebugReport {

    suspend fun generate(context: Context): String {
        val app = context.applicationContext as ClipMasterApp
        val clipCount = app.database.clipDao().observeCount().first()
        val settings = SettingsStore.current()
        val rootGranted = PermissionHelper.hasRootAccess()
        val overlayGranted = PermissionHelper.hasOverlayPermission(context)
        val accessibilityGranted = PermissionHelper.hasAccessibilityEnabled(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val crashes = CrashLogger.readLog(context)

        return buildString {
            appendLine("ClipMaster Debug Report")
            appendLine("Generated: $timestamp")
            appendLine()
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${context.packageName}")
            appendLine()
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("Permissions:")
            appendLine("  Root: ${if (rootGranted) "granted" else "NOT granted"}")
            appendLine("  Overlay: ${if (overlayGranted) "granted" else "NOT granted"}")
            appendLine("  Accessibility: ${if (accessibilityGranted) "enabled" else "NOT enabled"}")
            appendLine()
            appendLine("Settings:")
            appendLine("  Snap to edges: ${settings.snapBubbleToEdges}")
            appendLine("  Auto-hide bubble when empty: ${settings.autoHideBubbleWhenEmpty}")
            appendLine("  Auto-capture from clipboard: ${settings.autoCaptureFromClipboard}")
            appendLine("  Show source app: ${settings.showSourceApp}")
            appendLine("  History limit: ${settings.historyLimit}")
            appendLine()
            appendLine("Clip history: $clipCount saved")
            appendLine()
            appendLine("Recent crashes:")
            appendLine(if (crashes.isBlank()) "  (none recorded)" else crashes)
        }
    }
}
