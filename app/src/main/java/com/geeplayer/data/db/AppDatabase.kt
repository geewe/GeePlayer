package com.geeplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.geeplayer.data.db.dao.*
import com.geeplayer.data.db.entity.*

@Database(
    entities = [
        PlayHistoryEntity::class,
        QueueEntity::class,
        LyricsCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}
