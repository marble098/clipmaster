package com.clipmaster.floating.data.repository

import com.clipmaster.floating.data.db.ClipDao
import com.clipmaster.floating.data.db.ClipEntry
import kotlinx.coroutines.flow.Flow

class ClipRepository(private val dao: ClipDao) {

    /** Observe the 50 most recent entries as a reactive Flow. */
    fun recentClips(): Flow<List<ClipEntry>> = dao.observeRecent()

    /**
     * Insert a new clip, skip if it duplicates the most recent entry,
     * then prune to enforce the 50-item FIFO cap.
     */
    suspend fun addClip(content: String, sourceApp: String? = null) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        // Deduplicate against the last entry
        val last = dao.lastEntry()
        if (last != null && last.content == trimmed) return

        dao.insert(ClipEntry(content = trimmed, sourceApp = sourceApp))
        dao.pruneOldEntries()
    }

    suspend fun delete(entry: ClipEntry) = dao.delete(entry)
}
