package com.clipmaster.floating.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.clipmaster.floating.ClipMasterApp
import com.clipmaster.floating.clipboard.ClipboardHelper
import com.clipmaster.floating.data.repository.ClipRepository
import com.clipmaster.floating.debug.CrashLogger
import com.clipmaster.floating.debug.DebugReport
import com.clipmaster.floating.service.FloatingBubbleService
import com.clipmaster.floating.settings.SettingsStore
import com.clipmaster.floating.ui.theme.ClipMasterTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = ClipRepository((application as ClipMasterApp).database.clipDao())

        setContent {
            ClipMasterTheme {
                val settings by SettingsStore.settings.collectAsState()

                SettingsScreen(
                    settings = settings,
                    onSettingsChange = { transform -> SettingsStore.update(transform) },
                    onResetBubblePosition = {
                        sendBroadcast(
                            Intent(FloatingBubbleService.ACTION_RESET_BUBBLE_POSITION)
                                .setPackage(packageName)
                        )
                    },
                    onClearAllClips = {
                        lifecycleScope.launch { repository.clearAll() }
                    },
                    onGenerateDebugReport = { DebugReport.generate(this@SettingsActivity) },
                    onShareDebugReport = { report ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share debug report"))
                    },
                    onCopyDebugReport = { report ->
                        ClipboardHelper.copyToClipboard(this@SettingsActivity, report, label = "ClipMaster debug report")
                    },
                    onClearCrashLog = { CrashLogger.clear(this@SettingsActivity) },
                    onBack = { finish() },
                )
            }
        }
    }
}
