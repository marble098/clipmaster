package com.clipmaster.floating

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.clipmaster.floating.data.db.ClipDatabase
import com.clipmaster.floating.settings.SettingsStore

class ClipMasterApp : Application() {

    val database: ClipDatabase by lazy { ClipDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "clipmaster_fg"
    }
}
