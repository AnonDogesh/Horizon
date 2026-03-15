package com.horizonweb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_files")
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedPath: String,
    val originalName: String,
    val fileType: String,
    val dateAdded: Long
)
