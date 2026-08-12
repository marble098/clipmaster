package com.clipmaster.floating.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    /** Observe the most recent 50 entries, newest first. */
    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC LIMIT 50")
    fun observeRecent(): Flow<List<ClipEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipEntry): Long

    @Update
    suspend fun update(entry: ClipEntry)

    @Delete
    suspend fun delete(entry: ClipEntry)

    /** Count total rows. */
    @Query("SELECT COUNT(*) FROM clip_entries")
    suspend fun count(): Int

    /**
     * Enforce the 50-item FIFO cap.
     * Deletes all entries whose id is NOT in the top-50 newest.
     */
    @Query(
        """
        DELETE FROM clip_entries
        WHERE id NOT IN (
            SELECT id FROM clip_entries ORDER BY timestamp DESC LIMIT 50
        )
        """
    )
    suspend fun pruneOldEntries()

    /** Prevent storing exact duplicate text back-to-back. */
    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastEntry(): ClipEntry?
}
