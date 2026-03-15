package com.horizonweb.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VaultFileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao
}
