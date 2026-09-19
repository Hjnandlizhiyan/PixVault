package com.pixvault.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixvault.data.db.entity.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Query("SELECT * FROM images WHERE deletedTime IS NULL ORDER BY modifiedTime DESC")
    fun observeAll(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE deletedTime IS NOT NULL ORDER BY deletedTime DESC")
    fun observeTrashed(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE id = :id AND deletedTime IS NULL")
    suspend fun getById(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getByIdIncludingTrashed(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE id IN (:ids) AND deletedTime IS NULL")
    suspend fun getByIds(ids: List<Long>): List<ImageEntity>

    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun getByIdsIncludingTrashed(ids: List<Long>): List<ImageEntity>

    @Query("SELECT * FROM images WHERE deletedTime IS NOT NULL")
    suspend fun getAllTrashed(): List<ImageEntity>

    @Query(
        "SELECT i.* FROM images i INNER JOIN image_tags it ON i.id = it.imageId " +
            "WHERE it.tagId = :tagId AND i.deletedTime IS NULL ORDER BY i.modifiedTime DESC"
    )
    fun observeImagesByTag(tagId: Long): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE isFavorite = 1 AND deletedTime IS NULL ORDER BY modifiedTime DESC")
    fun observeFavorites(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE embedding IS NOT NULL AND deletedTime IS NULL")
    suspend fun getAllWithEmbedding(): List<ImageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(images: List<ImageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ImageEntity): Long

    @Query("UPDATE images SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: ByteArray)

    @Query("UPDATE images SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE images SET fileName = :fileName WHERE id = :id")
    suspend fun updateFileName(id: Long, fileName: String)

    @Query(
        "UPDATE images SET width = :width, height = :height, fileSize = :fileSize, " +
            "mimeType = :mimeType, hasAlpha = :hasAlpha, modifiedTime = :modifiedTime WHERE id = :id"
    )
    suspend fun updateDimensions(
        id: Long,
        width: Int,
        height: Int,
        fileSize: Long,
        mimeType: String,
        hasAlpha: Boolean,
        modifiedTime: Long
    )

    @Query("UPDATE images SET deletedTime = :time WHERE id = :id")
    suspend fun softDelete(id: Long, time: Long)

    @Query("UPDATE images SET deletedTime = :time WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<Long>, time: Long)

    @Query("UPDATE images SET deletedTime = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("UPDATE images SET deletedTime = NULL WHERE id IN (:ids)")
    suspend fun restoreAll(ids: List<Long>)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM images WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM images WHERE deletedTime IS NOT NULL")
    suspend fun deleteAllTrashed()

    @Query("UPDATE images SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun updateFavoriteAll(ids: List<Long>, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM images WHERE deletedTime IS NULL")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM images WHERE deletedTime IS NOT NULL")
    suspend fun countTrashed(): Int
}
