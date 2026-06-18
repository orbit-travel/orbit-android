package com.pnu.orbit.ui.addtrip

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoMetadataReader(
    private val context: Context,
) {
    fun read(uri: Uri, fallbackOrder: Int): PhotoDraft {
        // Photos chosen via the system picker have their GPS EXIF redacted by default; ask for the
        // un-redacted original so location-tagged photos place themselves automatically. This only
        // yields data when the app holds ACCESS_MEDIA_LOCATION and the source keeps it, and degrades
        // to "no location" otherwise.
        val sourceUri = originalUriOrSame(uri)
        val exif = readExif(sourceUri)
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

    private fun originalUriOrSame(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        return runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
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

    // Downloaded/web images usually have their EXIF capture date stripped, so DATE_TAKEN
    // (which MediaStore populates from EXIF) is null too. Fall back to DATE_ADDED /
    // DATE_MODIFIED — the file's import/modify time — which is what the system gallery
    // shows for such photos. Query with a null projection so this also works for the
    // limited column sets some content providers (e.g. the photo picker) expose.
    private fun readTakenAtFromMediaStore(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            // DATE_TAKEN is in milliseconds; DATE_ADDED / DATE_MODIFIED are in seconds.
            columnMillis(cursor, MediaStore.Images.Media.DATE_TAKEN, 1L)
                ?: columnMillis(cursor, MediaStore.Images.Media.DATE_ADDED, 1000L)
                ?: columnMillis(cursor, MediaStore.Images.Media.DATE_MODIFIED, 1000L)
        }
    }.getOrNull()

    private fun columnMillis(cursor: Cursor, column: String, multiplier: Long): Long? {
        val index = cursor.getColumnIndex(column)
        if (index == -1 || cursor.isNull(index)) return null
        val value = cursor.getLong(index)
        return if (value > 0) value * multiplier else null
    }

    companion object {
        private const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"
    }
}
