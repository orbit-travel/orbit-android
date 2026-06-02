package com.pnu.orbit.ui.addtrip

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoMetadataReader(
    private val context: Context,
) {
    fun read(uri: Uri, fallbackOrder: Int): PhotoDraft {
        val exif = readExif(uri)
        val latLong = exif?.latLong
        val takenAt = readTakenAtFromExif(exif) ?: readTakenAtFromMediaStore(uri)

        return PhotoDraft(
            draftId = System.nanoTime() + fallbackOrder,
            uri = uri,
            takenAt = takenAt,
            lat = latLong?.getOrNull(0),
            lng = latLong?.getOrNull(1),
            locationName = null,
        )
    }

    private fun readExif(uri: Uri): ExifInterface? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ExifInterface(inputStream)
            }
        }.getOrNull()

    private fun readTakenAtFromExif(exif: ExifInterface?): Long? {
        if (exif == null) return null

        val rawDate = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME,
        ).firstNotNullOfOrNull { tag -> exif.getAttribute(tag) }

        return rawDate?.let { parseExifDate(it) }
    }

    private fun parseExifDate(value: String): Long? {
        val parser = SimpleDateFormat(EXIF_DATE_PATTERN, Locale.US)
        return try {
            parser.parse(value)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun readTakenAtFromMediaStore(uri: Uri): Long? {
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                if (index == -1 || cursor.isNull(index)) null else cursor.getLong(index)
            }
        }.getOrNull()
    }

    companion object {
        private const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"
    }
}
