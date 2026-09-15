package com.pixvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixvault.data.db.entity.ImageTagName
import com.pixvault.data.db.entity.TagEntity
import com.pixvault.data.db.entity.TagWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        "SELECT it.imageId AS imageId, t.name AS tagName " +
            "FROM image_tags it INNER JOIN tags t ON it.tagId = t.id"
    )
    fun observeImageTagNames(): Flow<List<ImageTagName>>

    @Query(
        "SELECT t.id, t.name, t.description, t.createdTime, t.threshold, " +
            "COUNT(i.id) AS count FROM tags t " +
            "LEFT JOIN image_tags it ON t.id = it.tagId " +
            "LEFT JOIN images i ON it.imageId = i.id AND i.deletedTime IS NULL " +
            "GROUP BY t.id ORDER BY t.name"
    )
    fun observeTagsWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE prototypeVector IS NOT NULL")
    suspend fun getWithPrototype(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name, description = :description, threshold = :threshold WHERE id = :id")
    suspend fun update(id: Long, name: String, description: String?, threshold: Float)

    @Query("UPDATE tags SET threshold = :threshold WHERE id = :id")
    suspend fun updateThreshold(id: Long, threshold: Float)

    @Query("UPDATE tags SET prototypeVector = :vector WHERE id = :id")
    suspend fun updatePrototype(id: Long, vector: ByteArray)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}
