package com.horizonweb.di

import android.content.Context
import androidx.room.Room
import com.horizonweb.data.AppDatabase
import com.horizonweb.data.VaultFileDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "horizon_web.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideVaultFileDao(database: AppDatabase): VaultFileDao = database.vaultFileDao()
}
