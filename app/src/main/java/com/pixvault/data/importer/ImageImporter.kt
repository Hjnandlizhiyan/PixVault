package com.pixvault.data.importer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.pixvault.data.db.entity.ImageEntity
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

            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dest.absolutePath, opts)

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
                modifiedTime = now,
                hasAlpha = mimeType == "image/png" || mimeType == "image/webp"
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
