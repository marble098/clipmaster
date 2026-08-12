package com.clipmaster.floating.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    /** Observe the most recent [limit] entries, newest first. */
    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ClipEntry>>

    /** Observe the total clip count, regardless of the display limit. */
    @Query("SELECT COUNT(*) FROM clip_entries")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipEntry): Long

    @Update
    suspend fun update(entry: ClipEntry)

    @Delete
    suspend fun delete(entry: ClipEntry)

    @Query("DELETE FROM clip_entries")
    suspend fun deleteAll()

    /**
     * Enforce a FIFO cap of [limit] items.
     * Deletes all entries whose id is NOT in the top-[limit] newest.
     */
    @Query(
        """
        DELETE FROM clip_entries
        WHERE id NOT IN (
            SELECT id FROM clip_entries ORDER BY timestamp DESC LIMIT :limit
        )
        """
    )
    suspend fun pruneOldEntries(limit: Int)

    /** Prevent storing exact duplicate text back-to-back. */
    @Query("SELECT * FROM clip_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastEntry(): ClipEntry?
}
