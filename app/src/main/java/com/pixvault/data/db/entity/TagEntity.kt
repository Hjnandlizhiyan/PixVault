package com.pixvault.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdTime: Long,
    @ColumnInfo(defaultValue = "0.30") val threshold: Float = 0.30f,
    val prototypeVector: ByteArray? = null
)