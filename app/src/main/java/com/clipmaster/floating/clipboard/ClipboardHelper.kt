package com.clipmaster.floating.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.clipmaster.floating.root.RootExecutor

/**
 * Bridges ClipMaster's clip history with the phone's actual system clipboard:
 * reading whatever was just copied anywhere on the device, and writing clips
 * back onto it so any app's paste action picks them up.
 */
object ClipboardHelper {

    /** Read the current primary clip as plain text, if any. */
    fun readPrimaryClip(context: Context): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val description = clip.description
        val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        if (!isText) return null

        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    /** Write text to the system clipboard using the standard Android API. */
    fun copyToClipboard(context: Context, text: String, label: String = "ClipMaster"): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return try {
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy [text] back onto the system clipboard, preferring the normal
     * Android API. Android 10+ blocks background apps from writing the
     * clipboard, so when that happens (e.g. ClipMaster's overlay isn't
     * treated as the focused window) this falls back to the root shell
     * write in [RootExecutor], which bypasses that restriction.
     */
    suspend fun copyWithRootFallback(context: Context, text: String, label: String = "ClipMaster"): Boolean {
        if (copyToClipboard(context, text, label)) return true
        return RootExecutor.setClipboard(text)
    }
}
