package com.pixvault.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "image_tags",
    primaryKeys = ["imageId", "tagId"]
)
data class ImageTagEntity(
    val imageId: Long,
    val tagId: Long,
    val similarity: Float,
    val source: String,
    val createdTime: Long
) {
    companion object {
        const val SOURCE_AUTO = "AUTO"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_POSITIVE = "POSITIVE"
    }
}