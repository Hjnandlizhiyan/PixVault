package com.pixvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixvault.data.db.entity.ImageTagEntity
import com.pixvault.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rel: ImageTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rels: List<ImageTagEntity>)

    @Query("DELETE FROM image_tags WHERE imageId = :imageId AND tagId = :tagId")
    suspend fun delete(imageId: Long, tagId: Long)

    @Query("DELETE FROM image_tags WHERE imageId = :imageId")
    suspend fun deleteByImage(imageId: Long)

    @Query("DELETE FROM image_tags WHERE imageId IN (:imageIds)")
    suspend fun deleteByImages(imageIds: List<Long>)

    @Query("DELETE FROM image_tags WHERE imageId IN (SELECT id FROM images WHERE deletedTime IS NOT NULL)")
    suspend fun deleteForTrashed()

    @Query("SELECT * FROM image_tags WHERE imageId = :imageId")
    suspend fun getByImage(imageId: Long): List<ImageTagEntity>

    @Query("SELECT imageId FROM image_tags WHERE tagId = :tagId")
    suspend fun getImageIdsByTag(tagId: Long): List<Long>

    @Query("SELECT imageId FROM image_tags WHERE tagId = :tagId AND source = :source")
    suspend fun getImageIdsByTagAndSource(tagId: Long, source: String): List<Long>

    @Query("DELETE FROM image_tags WHERE tagId = :tagId AND source = :source")
    suspend fun deleteByTagAndSource(tagId: Long, source: String)

    @Query("DELETE FROM image_tags WHERE tagId = :tagId")
    suspend fun deleteByTag(tagId: Long)

    @Query("SELECT tagId FROM image_tags WHERE imageId = :imageId")
    suspend fun getTagIdsByImage(imageId: Long): List<Long>

    @Query("SELECT t.* FROM tags t INNER JOIN image_tags it ON t.id = it.tagId WHERE it.imageId = :imageId ORDER BY t.name")
    fun observeTagsForImage(imageId: Long): Flow<List<TagEntity>>
}
