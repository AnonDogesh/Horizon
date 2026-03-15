package com.horizonweb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY dateAdded DESC")
    fun observeVaultFiles(): Flow<List<VaultFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: VaultFileEntity): Long

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}
