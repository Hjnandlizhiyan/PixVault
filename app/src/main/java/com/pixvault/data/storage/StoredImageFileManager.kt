package com.pixvault.data.storage

import com.pixvault.data.db.entity.ImageEntity
import java.io.File

/** Owns deletion of image files copied into PixVault's private image directory. */
class StoredImageFileManager(imagesDirectory: File) {
    private val root = imagesDirectory.canonicalFile

    fun delete(image: ImageEntity): Boolean {
        val path = image.path ?: return true
        val file = File(path).canonicalFile
        if (!isInsideRoot(file)) return true
        return !file.exists() || file.delete()
    }

    internal fun isManaged(path: String): Boolean = isInsideRoot(File(path).canonicalFile)

    private fun isInsideRoot(file: File): Boolean {
        val prefix = root.path + File.separator
        return file.path.startsWith(prefix, ignoreCase = true)
    }
}
