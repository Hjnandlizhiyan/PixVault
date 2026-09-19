package com.pixvault.data.storage

import com.pixvault.data.db.entity.ImageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoredImageFileManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun deletesFilesInsideManagedDirectory() {
        val root = temporaryFolder.newFolder("images")
        val imageFile = File(root, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val manager = StoredImageFileManager(root)

        assertTrue(manager.delete(image(path = imageFile.path)))
        assertFalse(imageFile.exists())
    }

    @Test
    fun neverDeletesFilesOutsideManagedDirectory() {
        val root = temporaryFolder.newFolder("images")
        val external = temporaryFolder.newFile("external.jpg").apply { writeBytes(byteArrayOf(1)) }
        val manager = StoredImageFileManager(root)

        assertTrue(manager.delete(image(path = external.path)))
        assertTrue(external.exists())
        assertFalse(manager.isManaged(external.path))
    }

    private fun image(path: String) = ImageEntity(
        uri = path,
        path = path,
        fileName = "photo.jpg",
        width = 1,
        height = 1,
        fileSize = 1,
        mimeType = "image/jpeg",
        createdTime = 0,
        modifiedTime = 0,
        hasAlpha = false
    )
}
