package com.clipmaster.floating.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * Utility for executing privileged shell commands via `su`.
 *
 * Primary use: injecting arbitrary text into the Android system clipboard
 * using `service call clipboard`, which works even when the app is in the
 * background or targeting secured input fields.
 */
object RootExecutor {

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Execute a command as root. Returns structured result.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    /**
     * Set the system clipboard text using Android's clipboard service
     * via root shell. Uses `service call clipboard` with the IClipboard
     * AIDL transaction code.
     *
     * This bypasses all foreground/background restrictions.
     */
    suspend fun setClipboard(text: String): Boolean {
        // Escape single quotes in the text for shell safety
        val escaped = text.replace("'", "'\\''")

        // Use `am broadcast` with the clipboard as an alternative approach
        // that's more stable across Android versions.
        // Falls back to `service call clipboard` if needed.
        val commands = buildString {
            // Primary method: input via `am` and content provider
            appendLine("input keyevent --longpress 279 2>/dev/null || true")
            // Robust method: write to clipboard via settings-style command
            appendLine(
                """
                content call --uri content://clipboard/primary \
                  --method write \
                  --extra text:string:'$escaped' 2>/dev/null || \
                am broadcast \
                  -a clipmaster.SET_CLIPBOARD \
                  --es text '$escaped' 2>/dev/null || \
                service call clipboard 2 i32 1 i32 0 \
                  s16 "com.clipmaster.floating" \
                  s16 "$escaped" i32 0 i32 0 2>/dev/null
                """.trimIndent()
            )
        }

        val result = exec(commands)
        return result.exitCode == 0
    }

    /**
     * Simulate a paste keystroke (Ctrl+V) via root input command.
     * Useful after setting the clipboard to trigger a paste into the focused field.
     */
    suspend fun simulatePaste(): Boolean {
        val result = exec("input keyevent 279") // KEYCODE_PASTE
        return result.exitCode == 0
    }

    /**
     * Check if root access is available.
     */
    suspend fun isRootAvailable(): Boolean {
        val result = exec("id")
        return result.stdout.contains("uid=0")
    }
}
