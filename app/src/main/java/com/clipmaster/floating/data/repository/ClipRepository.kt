package com.clipmaster.floating.data.repository

import com.clipmaster.floating.data.db.ClipDao
import com.clipmaster.floating.data.db.ClipEntry
import kotlinx.coroutines.flow.Flow

class ClipRepository(private val dao: ClipDao) {

    /** Observe the [limit] most recent entries as a reactive Flow. */
    fun recentClips(limit: Int): Flow<List<ClipEntry>> = dao.observeRecent(limit)

    /** Observe the total clip count, independent of any display limit. */
    fun clipCount(): Flow<Int> = dao.observeCount()

    /**
     * Insert a new clip, skip if it duplicates the most recent entry,
     * then prune to enforce the [historyLimit] FIFO cap.
     */
    suspend fun addClip(content: String, sourceApp: String? = null, historyLimit: Int) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        // Deduplicate against the last entry
        val last = dao.lastEntry()
        if (last != null && last.content == trimmed) return

        dao.insert(ClipEntry(content = trimmed, sourceApp = sourceApp))
        dao.pruneOldEntries(historyLimit)
    }

    suspend fun delete(entry: ClipEntry) = dao.delete(entry)

    suspend fun clearAll() = dao.deleteAll()

    /** Update an existing clip's text in place, preserving its position/id. */
    suspend fun updateClip(entry: ClipEntry, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank() || trimmed == entry.content) return
        dao.update(entry.copy(content = trimmed))
    }
}
