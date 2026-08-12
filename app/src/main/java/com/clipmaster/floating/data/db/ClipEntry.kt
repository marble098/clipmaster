package com.clipmaster.floating.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clip_entries")
data class ClipEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val sourceApp: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
