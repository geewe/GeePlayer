package com.geeplayer.di

import android.content.Context
import androidx.room.Room
import com.geeplayer.data.db.AppDatabase
import com.geeplayer.data.db.dao.*
import com.geeplayer.data.preferences.AppPreferences
import com.geeplayer.player.DlnaPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "dlna_receiver.db"
        ).build()
    }

    @Provides
    fun providePlayHistoryDao(db: AppDatabase): PlayHistoryDao = db.playHistoryDao()

    @Provides
    fun provideQueueDao(db: AppDatabase): QueueDao = db.queueDao()

    @Provides
    fun provideLyricsCacheDao(db: AppDatabase): LyricsCacheDao = db.lyricsCacheDao()

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideDlnaPlayer(@ApplicationContext context: Context): DlnaPlayer {
        return DlnaPlayer(context).also { it.initialize() }
    }
}
