package com.pixvault.data.importer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.pixvault.data.db.entity.ImageEntity
import com.pixvault.data.util.Hashing
import com.pixvault.data.util.PhotoMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImageImporter(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply { mkdirs() }
    }

    suspend fun import(uris: List<Uri>): List<ImageEntity> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> importOne(uri) }
    }

    private fun importOne(uri: Uri): ImageEntity? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: return null
            if (!mimeType.startsWith("image/")) return null

            var fileName = "unknown"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { fileName = it }
                }
            }

            val dest = File(imagesDir, "${UUID.randomUUID()}${extensionFor(mimeType)}")

            val sourceUris = buildList {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_MEDIA_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    add(MediaStore.setRequireOriginal(uri))
                }
                add(uri)
            }.distinct()
            val sourceMetadata = sourceUris
                .map { sourceUri -> PhotoMetadataReader.read(resolver, sourceUri) }
                .firstOrNull { it.latitude != null && it.longitude != null }
            val contentHash = sourceUris.firstNotNullOfOrNull { sourceUri ->
                runCatching {
                    resolver.openInputStream(sourceUri)?.use { input ->
                        dest.outputStream().use { output ->
                            Hashing.copyAndSha256(input, output)
                        }
                    }
                }.getOrNull()
            } ?: return null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dest.absolutePath, opts)

            val copiedMetadata = PhotoMetadataReader.read(dest.absolutePath)
            val metadata = copiedMetadata.copy(
                dateTaken = copiedMetadata.dateTaken ?: sourceMetadata?.dateTaken,
                latitude = copiedMetadata.latitude ?: sourceMetadata?.latitude,
                longitude = copiedMetadata.longitude ?: sourceMetadata?.longitude
            )

            val now = System.currentTimeMillis()
            ImageEntity(
                uri = dest.absolutePath,
                path = dest.absolutePath,
                fileName = fileName,
                width = opts.outWidth,
                height = opts.outHeight,
                fileSize = dest.length(),
                mimeType = mimeType,
                createdTime = now,
                modifiedTime = metadata.dateTaken ?: now,
                hasAlpha = mimeType == "image/png" || mimeType == "image/webp",
                contentHash = contentHash,
                dateTaken = metadata.dateTaken,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                metadataIndexed = true
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/heic", "image/heif" -> ".heic"
        "image/bmp" -> ".bmp"
        else -> ".jpg"
    }
}
