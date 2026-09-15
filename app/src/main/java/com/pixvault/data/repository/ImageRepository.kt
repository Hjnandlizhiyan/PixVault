package com.pixvault.data.repository

import com.pixvault.data.db.AppDatabase
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.processor.ImageProcessor
import com.pixvault.data.util.VectorUtils
import kotlinx.coroutines.flow.Flow

class ImageRepository(private val database: AppDatabase) {
    private val imageDao = database.imageDao()
    private val imageTagDao = database.imageTagDao()

    fun observeImages(): Flow<List<ImageEntity>> = imageDao.observeAll()

    fun observeImagesByTag(tagId: Long): Flow<List<ImageEntity>> = imageDao.observeImagesByTag(tagId)

    fun observeFavorites(): Flow<List<ImageEntity>> = imageDao.observeFavorites()

    suspend fun getImage(id: Long): ImageEntity? = imageDao.getById(id)

    suspend fun count(): Int = imageDao.count()

    suspend fun insertAll(images: List<ImageEntity>): List<Long> {
        return imageDao.insertAll(images)
    }

    suspend fun updateEmbedding(id: Long, embedding: FloatArray) {
        imageDao.updateEmbedding(id, VectorUtils.toBytes(embedding))
    }

    suspend fun updateFavorite(id: Long, favorite: Boolean) {
        imageDao.updateFavorite(id, favorite)
    }

    suspend fun rename(id: Long, fileName: String) {
        imageDao.updateFileName(id, fileName)
    }

    suspend fun delete(id: Long) {
        imageDao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun deleteAll(ids: List<Long>) {
        imageDao.softDeleteAll(ids, System.currentTimeMillis())
    }

    fun observeTrash(): Flow<List<ImageEntity>> = imageDao.observeTrashed()

    suspend fun trashCount(): Int = imageDao.countTrashed()

    suspend fun restore(id: Long) {
        imageDao.restore(id)
    }

    suspend fun restoreAll(ids: List<Long>) {
        imageDao.restoreAll(ids)
    }

    suspend fun permanentlyDelete(id: Long) {
        imageTagDao.deleteByImage(id)
        imageDao.delete(id)
    }

    suspend fun permanentlyDeleteAll(ids: List<Long>) {
        imageTagDao.deleteByImages(ids)
        imageDao.deleteAll(ids)
    }

    suspend fun emptyTrash() {
        imageTagDao.deleteForTrashed()
        imageDao.deleteAllTrashed()
    }

    suspend fun updateFavoriteAll(ids: List<Long>, favorite: Boolean) {
        imageDao.updateFavoriteAll(ids, favorite)
    }

    suspend fun createProcessedImage(
        source: ImageEntity,
        result: ImageProcessor.Result,
        customName: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val fileName = if (customName.isNullOrBlank()) {
            processedName(source.fileName)
        } else {
            customName + resultExtension(result)
        }
        val entity = ImageEntity(
            uri = result.path,
            path = result.path,
            fileName = fileName,
            width = result.width,
            height = result.height,
            fileSize = result.fileSize,
            mimeType = result.mimeType,
            createdTime = now,
            modifiedTime = now,
            hasAlpha = result.mimeType == "image/png" || result.mimeType == "image/webp"
        )
        return imageDao.insert(entity)
    }

    suspend fun updateAfterOverwrite(id: Long, result: ImageProcessor.Result) {
        imageDao.updateDimensions(
            id = id,
            width = result.width,
            height = result.height,
            fileSize = result.fileSize,
            mimeType = result.mimeType,
            hasAlpha = result.mimeType == "image/png" || result.mimeType == "image/webp",
            modifiedTime = System.currentTimeMillis()
        )
    }

    private fun processedName(original: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        return "${base}_处理"
    }

    private fun resultExtension(result: ImageProcessor.Result): String {
        val dot = result.path.lastIndexOf('.')
        return if (dot > 0) result.path.substring(dot) else ".jpg"
    }

    suspend fun findSimilar(imageId: Long, limit: Int = 20): List<Pair<ImageEntity, Float>> {
        val target = imageDao.getById(imageId) ?: return emptyList()
        val targetEmb = target.embedding ?: return emptyList()
        val targetVec = VectorUtils.toFloatArray(targetEmb)
        val result = mutableListOf<Pair<ImageEntity, Float>>()
        for (img in imageDao.getAllWithEmbedding()) {
            if (img.id == imageId) continue
            val emb = img.embedding ?: continue
            val sim = VectorUtils.cosine(targetVec, VectorUtils.toFloatArray(emb))
            result.add(img to sim)
        }
        return result.sortedByDescending { it.second }.take(limit)
    }

    suspend fun searchByText(
        queryVector: FloatArray,
        limit: Int = 60
    ): List<Pair<ImageEntity, Float>> {
        val result = mutableListOf<Pair<ImageEntity, Float>>()
        for (img in imageDao.getAllWithEmbedding()) {
            val emb = img.embedding ?: continue
            val sim = VectorUtils.cosine(queryVector, VectorUtils.toFloatArray(emb))
            result.add(img to sim)
        }
        return result.sortedByDescending { it.second }.take(limit)
    }
}
