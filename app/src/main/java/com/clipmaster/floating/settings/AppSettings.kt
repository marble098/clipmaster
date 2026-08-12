package com.clipmaster.floating.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All user-customizable app behavior, in one place. */
data class AppSettings(
    /** Drag release snaps the bubble to the nearest screen edge; off = drop it exactly where released. */
    val snapBubbleToEdges: Boolean = true,
    /** Bubble disappears while there are zero saved clips, reappearing the moment one is captured. */
    val autoHideBubbleWhenEmpty: Boolean = true,
    /** Whether the system clipboard listener auto-captures copies into history at all. */
    val autoCaptureFromClipboard: Boolean = true,
    /** Show which app a clip came from under its text. */
    val showSourceApp: Boolean = true,
    /** Max clips retained (FIFO); mirrors ClipDao's LIMIT. */
    val historyLimit: Int = 50,
) {
    companion object {
        val HISTORY_LIMIT_OPTIONS = listOf(20, 50, 100, 200)
    }
}

/**
 * App-wide settings store backed by SharedPreferences, exposed as a
 * StateFlow so both the floating service and any UI (Settings screen,
 * clip panel) observe the same live values without extra plumbing.
 */
object SettingsStore {
    private const val PREFS_NAME = "clipmaster_settings"
    private const val KEY_SNAP_EDGES = "snap_edges"
    private const val KEY_AUTO_HIDE = "auto_hide_bubble"
    private const val KEY_AUTO_CAPTURE = "auto_capture"
    private const val KEY_SHOW_SOURCE = "show_source_app"
    private const val KEY_HISTORY_LIMIT = "history_limit"

    private var prefs: SharedPreferences? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Call once, from Application.onCreate(), before anything reads [settings]. */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _settings.value = AppSettings(
            snapBubbleToEdges = p.getBoolean(KEY_SNAP_EDGES, true),
            autoHideBubbleWhenEmpty = p.getBoolean(KEY_AUTO_HIDE, true),
            autoCaptureFromClipboard = p.getBoolean(KEY_AUTO_CAPTURE, true),
            showSourceApp = p.getBoolean(KEY_SHOW_SOURCE, true),
            historyLimit = p.getInt(KEY_HISTORY_LIMIT, 50),
        )
    }

    /** Snapshot for one-off reads (e.g. inside a coroutine that just fired). */
    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val p = prefs ?: return
        val updated = transform(_settings.value)
        p.edit()
            .putBoolean(KEY_SNAP_EDGES, updated.snapBubbleToEdges)
            .putBoolean(KEY_AUTO_HIDE, updated.autoHideBubbleWhenEmpty)
            .putBoolean(KEY_AUTO_CAPTURE, updated.autoCaptureFromClipboard)
            .putBoolean(KEY_SHOW_SOURCE, updated.showSourceApp)
            .putInt(KEY_HISTORY_LIMIT, updated.historyLimit)
            .apply()
        _settings.value = updated
    }
}
