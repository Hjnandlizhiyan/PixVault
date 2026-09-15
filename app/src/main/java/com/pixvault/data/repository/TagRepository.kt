package com.pixvault.data.repository

import com.pixvault.data.db.AppDatabase
import com.pixvault.data.db.entity.ImageTagEntity
import com.pixvault.data.db.entity.ImageTagName
import com.pixvault.data.db.entity.TagEntity
import com.pixvault.data.db.entity.TagWithCount
import com.pixvault.data.util.VectorUtils
import kotlinx.coroutines.flow.Flow

class TagRepository(private val database: AppDatabase) {
    private val tagDao = database.tagDao()
    private val imageTagDao = database.imageTagDao()
    private val imageDao = database.imageDao()

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeImageTagNames(): Flow<List<ImageTagName>> = tagDao.observeImageTagNames()

    fun observeTagsWithCount(): Flow<List<TagWithCount>> = tagDao.observeTagsWithCount()

    suspend fun createTag(name: String): Long {
        val existing = tagDao.getByName(name)
        if (existing != null) return existing.id
        return tagDao.insert(TagEntity(name = name, createdTime = System.currentTimeMillis()))
    }

    suspend fun deleteTag(id: Long) {
        imageTagDao.deleteByTag(id)
        tagDao.delete(id)
    }

    fun observeTagsForImage(imageId: Long): Flow<List<TagEntity>> =
        imageTagDao.observeTagsForImage(imageId)

    suspend fun addTag(imageId: Long, tagId: Long) {
        imageTagDao.insert(
            ImageTagEntity(
                imageId = imageId,
                tagId = tagId,
                similarity = 1f,
                source = ImageTagEntity.SOURCE_MANUAL,
                createdTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun addTagToImages(imageIds: List<Long>, tagId: Long) {
        if (imageIds.isEmpty()) return
        val now = System.currentTimeMillis()
        imageTagDao.insertAll(
            imageIds.map { imageId ->
                ImageTagEntity(
                    imageId = imageId,
                    tagId = tagId,
                    similarity = 1f,
                    source = ImageTagEntity.SOURCE_MANUAL,
                    createdTime = now
                )
            }
        )
    }

    suspend fun removeTag(imageId: Long, tagId: Long) {
        imageTagDao.delete(imageId, tagId)
    }

    suspend fun getPositiveImageIds(tagId: Long): List<Long> =
        imageTagDao.getImageIdsByTagAndSource(tagId, ImageTagEntity.SOURCE_POSITIVE)

    suspend fun updateThreshold(tagId: Long, threshold: Float) {
        tagDao.updateThreshold(tagId, threshold)
    }

    suspend fun countWithEmbedding(ids: List<Long>): Int =
        imageDao.getByIds(ids).count { it.embedding != null }

    suspend fun setPositiveExamples(tagId: Long, imageIds: List<Long>) {
        imageTagDao.deleteByTagAndSource(tagId, ImageTagEntity.SOURCE_POSITIVE)
        if (imageIds.isEmpty()) return
        val now = System.currentTimeMillis()
        imageTagDao.insertAll(
            imageIds.map { imageId ->
                ImageTagEntity(
                    imageId = imageId,
                    tagId = tagId,
                    similarity = 1f,
                    source = ImageTagEntity.SOURCE_POSITIVE,
                    createdTime = now
                )
            }
        )
    }

    suspend fun computePrototype(tagId: Long) {
        val ids = getPositiveImageIds(tagId)
        if (ids.isEmpty()) return
        val vectors = imageDao.getByIds(ids).mapNotNull { img ->
            img.embedding?.let { VectorUtils.toFloatArray(it) }
        }
        if (vectors.isEmpty()) return
        val prototype = VectorUtils.mean(vectors)
        tagDao.updatePrototype(tagId, VectorUtils.toBytes(prototype))
    }

    suspend fun autoMatch(tagId: Long): Int {
        val tag = tagDao.getById(tagId) ?: return 0
        val protoBytes = tag.prototypeVector ?: return 0
        val prototype = VectorUtils.toFloatArray(protoBytes)
        val threshold = tag.threshold
        val positiveIds = getPositiveImageIds(tagId).toSet()
        val now = System.currentTimeMillis()
        val matched = mutableListOf<ImageTagEntity>()
        for (img in imageDao.getAllWithEmbedding()) {
            if (img.id in positiveIds) continue
            val emb = img.embedding ?: continue
            val sim = VectorUtils.cosine(prototype, VectorUtils.toFloatArray(emb))
            if (sim >= threshold) {
                matched.add(
                    ImageTagEntity(
                        imageId = img.id,
                        tagId = tagId,
                        similarity = sim,
                        source = ImageTagEntity.SOURCE_AUTO,
                        createdTime = now
                    )
                )
            }
        }
        imageTagDao.deleteByTagAndSource(tagId, ImageTagEntity.SOURCE_AUTO)
        imageTagDao.insertAll(matched)
        return matched.size
    }

    suspend fun getSimilarTags(imageId: Long): List<Pair<TagEntity, Float>> {
        val image = imageDao.getById(imageId) ?: return emptyList()
        val emb = image.embedding ?: return emptyList()
        val vec = VectorUtils.toFloatArray(emb)
        val result = mutableListOf<Pair<TagEntity, Float>>()
        for (tag in tagDao.getWithPrototype()) {
            val proto = tag.prototypeVector ?: continue
            val sim = VectorUtils.cosine(vec, VectorUtils.toFloatArray(proto))
            if (sim >= tag.threshold) result.add(tag to sim)
        }
        return result.sortedByDescending { it.second }
    }
}
