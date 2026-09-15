package com.pixvault.data.db.entity

data class TagWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val createdTime: Long,
    val threshold: Float,
    val count: Int
)