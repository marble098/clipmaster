package com.clipmaster.floating.root

import android.util.Base64
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
        // Clip content is arbitrary text copied from any app, so it must never
        // be spliced directly into shell source (quotes/backticks/`$()` in it
        // would otherwise let it run as commands under root). Base64-encode it
        // and decode into a shell variable instead — the base64 alphabet has
        // no shell metacharacters, so this is safe regardless of content.
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val commands = buildString {
            appendLine("CLIP_TEXT=\$(printf '%s' '$encoded' | base64 -d)")
            appendLine(
                "content call --uri content://clipboard/primary --method write " +
                    "--extra text:string:\"\$CLIP_TEXT\" 2>/dev/null || " +
                    "service call clipboard 2 i32 1 i32 0 s16 \"com.clipmaster.floating\" " +
                    "s16 \"\$CLIP_TEXT\" i32 0 i32 0 2>/dev/null"
            )
        }

        val result = exec(commands)
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
