package com.geeplayer.data.db.dao

import androidx.room.*
import com.geeplayer.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<PlayHistoryEntity>>

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clearAll()
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueEntity>>

    @Insert
    suspend fun insert(item: QueueEntity)

    @Delete
    suspend fun delete(item: QueueEntity)

    @Query("DELETE FROM queue")
    suspend fun clearAll()

    @Query("UPDATE queue SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE songKey = :key")
    suspend fun get(key: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
