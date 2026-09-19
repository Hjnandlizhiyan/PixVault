package com.pixvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "images",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["contentHash"], unique = true)
    ]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val path: String?,
    val fileName: String,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String,
    val createdTime: Long,
    val modifiedTime: Long,
    val hasAlpha: Boolean,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    val embedding: ByteArray? = null,
    val deletedTime: Long? = null,
    val contentHash: String? = null
)