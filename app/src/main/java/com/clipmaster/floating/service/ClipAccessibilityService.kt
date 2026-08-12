package com.clipmaster.floating.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clipmaster.floating.ClipMasterApp
import com.clipmaster.floating.data.repository.ClipRepository
import kotlinx.coroutines.*

/**
 * Accessibility service that:
 *  1. Monitors clipboard changes via TYPE_VIEW_TEXT_CHANGED events.
 *  2. Provides an on-demand full-screen text capture via [captureScreenText].
 *  3. Can paste text into the currently focused editable field via [pasteIntoFocused].
 *
 * The service keeps a static [instance] reference so the FloatingBubbleService
 * can trigger captures without binding.
 */
class ClipAccessibilityService : AccessibilityService() {

    private lateinit var repository: ClipRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val app = application as ClipMasterApp
        repository = ClipRepository(app.database.clipDao())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            // Capture text that the user copies (selection-based)
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                val text = event.text?.joinToString("") ?: return
                if (text.isNotBlank()) {
                    val pkg = event.packageName?.toString()
                    scope.launch { repository.addClip(text, pkg) }
                }
            }
            // Capture clipboard changes
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Light-touch: we rely on captureScreenText() for full scraping
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────
    // Public API for FloatingBubbleService
    // ──────────────────────────────────────────────

    /**
     * Walk the entire accessibility node tree of the active window,
     * collect all visible text, and store as a single clip entry.
     */
    fun captureScreenText() {
        val root = rootInActiveWindow ?: return
        val builder = StringBuilder()
        collectText(root, builder)
        root.recycle()

        val captured = builder.toString().trim()
        if (captured.isNotBlank()) {
            val pkg = root.packageName?.toString()
            scope.launch { repository.addClip(captured, pkg) }
        }
    }

    /**
     * Find the currently focused editable field and set its text.
     */
    fun pasteIntoFocused(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = findFocusedEditable(root)
        if (focused != null) {
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
        root.recycle()
    }

    // ──────────────────────────────────────────────
    // Node tree helpers
    // ──────────────────────────────────────────────

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        node.text?.let { txt ->
            if (txt.isNotBlank()) {
                out.appendLine(txt)
            }
        }
        node.contentDescription?.let { desc ->
            if (desc.isNotBlank() && node.text.isNullOrBlank()) {
                out.appendLine(desc)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditable(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    companion object {
        @Volatile
        var instance: ClipAccessibilityService? = null
            private set
    }
}
