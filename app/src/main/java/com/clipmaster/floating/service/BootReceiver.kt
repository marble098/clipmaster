package com.clipmaster.floating.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.clipmaster.floating.util.PermissionHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // Only auto-start if overlay permission is still granted
        if (Settings.canDrawOverlays(context) &&
            PermissionHelper.hasAccessibilityEnabled(context)
        ) {
            val serviceIntent = Intent(context, FloatingBubbleService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
