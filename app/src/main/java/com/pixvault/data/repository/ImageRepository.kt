package com.pixvault.data.repository

import com.pixvault.data.db.AppDatabase
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.processor.ImageProcessor
import com.pixvault.data.storage.StoredImageFileManager
import com.pixvault.data.util.VectorUtils
import com.pixvault.data.util.PhotoMetadataReader
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ImageRepository(
    private val database: AppDatabase,
    private val storedImageFiles: StoredImageFileManager
) {
    private val imageDao = database.imageDao()
    private val imageTagDao = database.imageTagDao()

    fun observeImages(): Flow<List<ImageEntity>> = imageDao.observeAll()

    fun observePrivateImages(): Flow<List<ImageEntity>> = imageDao.observePrivate()

    fun observeLocatedImages(): Flow<List<ImageEntity>> = imageDao.observeLocated()

    fun observeImagesByTag(tagId: Long): Flow<List<ImageEntity>> = imageDao.observeImagesByTag(tagId)

    fun observeFavorites(): Flow<List<ImageEntity>> = imageDao.observeFavorites()

    suspend fun getImage(id: Long): ImageEntity? = imageDao.getById(id)

    suspend fun count(): Int = imageDao.count()

    suspend fun insertAll(images: List<ImageEntity>): List<Long> {
        val ids = imageDao.insertAll(images)
        images.zip(ids).forEach { (image, id) ->
            if (id == -1L) storedImageFiles.delete(image)
        }
        return ids
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
        val image = imageDao.getByIdIncludingTrashed(id) ?: return
        database.withTransaction {
            imageTagDao.deleteByImage(id)
            imageDao.delete(id)
        }
        storedImageFiles.delete(image)
    }

    suspend fun permanentlyDeleteAll(ids: List<Long>) {
        if (ids.isEmpty()) return
        val images = imageDao.getByIdsIncludingTrashed(ids)
        database.withTransaction {
            imageTagDao.deleteByImages(ids)
            imageDao.deleteAll(ids)
        }
        images.forEach(storedImageFiles::delete)
    }

    suspend fun emptyTrash() {
        val images = imageDao.getAllTrashed()
        database.withTransaction {
            imageTagDao.deleteForTrashed()
            imageDao.deleteAllTrashed()
        }
        images.forEach(storedImageFiles::delete)
    }

    suspend fun updateFavoriteAll(ids: List<Long>, favorite: Boolean) {
        imageDao.updateFavoriteAll(ids, favorite)
    }

    suspend fun updatePrivateAll(ids: List<Long>, isPrivate: Boolean) {
        imageDao.updatePrivateAll(ids, isPrivate)
    }

    suspend fun backfillPhotoMetadata() {
        imageDao.getPendingMetadata().forEach { image ->
            val path = image.path ?: image.uri
            val metadata = PhotoMetadataReader.read(path)
            imageDao.updateMetadata(
                id = image.id,
                dateTaken = metadata.dateTaken,
                latitude = metadata.latitude,
                longitude = metadata.longitude
            )
        }
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
            hasAlpha = result.mimeType == "image/png" || result.mimeType == "image/webp",
            metadataIndexed = true
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

    suspend fun findSimilarGroups(
        threshold: Float = 0.90f,
        maxImages: Int = 500
    ): List<List<Pair<ImageEntity, Float>>> {
        val candidates = imageDao.getAllWithEmbedding().take(maxImages)
        val consumed = mutableSetOf<Long>()
        val groups = mutableListOf<List<Pair<ImageEntity, Float>>>()
        for (source in candidates) {
            if (source.id in consumed) continue
            val sourceBytes = source.embedding ?: continue
            val sourceVector = VectorUtils.toFloatArray(sourceBytes)
            val matches = candidates.asSequence()
                .filter { it.id != source.id && it.id !in consumed }
                .mapNotNull { candidate ->
                    val bytes = candidate.embedding ?: return@mapNotNull null
                    val score = VectorUtils.cosine(sourceVector, VectorUtils.toFloatArray(bytes))
                    if (score >= threshold) candidate to score else null
                }
                .sortedByDescending { it.second }
                .toList()
            if (matches.isNotEmpty()) {
                val group = listOf(source to 1f) + matches
                consumed += group.map { it.first.id }
                groups += group
            }
        }
        return groups.sortedByDescending { it.size }
    }
}
