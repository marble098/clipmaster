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
     * Insert a new clip, then prune to enforce the [historyLimit] FIFO cap.
     * If this exact text already exists anywhere in history, it's bumped
     * back to the top (fresh timestamp) instead of being inserted again —
     * otherwise re-copying the same thing repeatedly would show it more
     * than once in the list.
     */
    suspend fun addClip(content: String, sourceApp: String? = null, historyLimit: Int) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        val last = dao.lastEntry()
        if (last != null && last.content == trimmed) return

        val existing = dao.findByContent(trimmed)
        if (existing != null) {
            dao.touch(existing.id, System.currentTimeMillis(), sourceApp ?: existing.sourceApp)
            return
        }

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
