package com.pixvault.data.util

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
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
        val exifMetadata = exif?.let(::readExif) ?: PhotoMetadata(null, null, null)
        if (exifMetadata.latitude != null && exifMetadata.longitude != null) {
            return exifMetadata
        }
        val containerLocation = readContainerLocation(path)
        return PhotoMetadata(
            dateTaken = exifMetadata.dateTaken,
            latitude = exifMetadata.latitude ?: containerLocation?.first,
            longitude = exifMetadata.longitude ?: containerLocation?.second
        )
    }

    fun read(resolver: ContentResolver, uri: Uri): PhotoMetadata {
        val exifMetadata = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                readExif(ExifInterface(descriptor.fileDescriptor))
            }
        }.getOrNull() ?: PhotoMetadata(null, null, null)
        val providerMetadata = readProviderMetadata(resolver, uri)
        return PhotoMetadata(
            dateTaken = exifMetadata.dateTaken ?: providerMetadata.dateTaken,
            latitude = exifMetadata.latitude ?: providerMetadata.latitude,
            longitude = exifMetadata.longitude ?: providerMetadata.longitude
        )
    }

    private fun readExif(exif: ExifInterface): PhotoMetadata {
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

    private fun readProviderMetadata(resolver: ContentResolver, uri: Uri): PhotoMetadata {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN, "latitude", "longitude"),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use PhotoMetadata(null, null, null)
                val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val latitudeIndex = cursor.getColumnIndex("latitude")
                val longitudeIndex = cursor.getColumnIndex("longitude")
                PhotoMetadata(
                    dateTaken = dateIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getLong),
                    latitude = latitudeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getDouble),
                    longitude = longitudeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getDouble)
                )
            } ?: PhotoMetadata(null, null, null)
        }.getOrDefault(PhotoMetadata(null, null, null))
    }

    private fun readContainerLocation(path: String): Pair<Double, Double>? {
        val raw = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            } finally {
                retriever.release()
            }
        }.getOrNull() ?: return null
        val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(raw)
            ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return latitude to longitude
    }
}
