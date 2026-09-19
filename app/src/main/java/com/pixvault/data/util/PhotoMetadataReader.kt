package com.pixvault.data.util

import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale

data class PhotoMetadata(
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?
)

object PhotoMetadataReader {
    fun read(path: String): PhotoMetadata {
        val exif = runCatching { ExifInterface(path) }.getOrNull()
            ?: return PhotoMetadata(null, null, null)
        val dateTaken = exif
            .getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?.let { raw ->
                runCatching {
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
                }.getOrNull()
            }
        val location = exif.latLong
        return PhotoMetadata(
            dateTaken = dateTaken,
            latitude = location?.getOrNull(0),
            longitude = location?.getOrNull(1)
        )
    }
}
