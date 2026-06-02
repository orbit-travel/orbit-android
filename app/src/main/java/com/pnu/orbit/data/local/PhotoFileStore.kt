package com.pnu.orbit.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Copies photos chosen through the system photo picker into app-private storage so the saved
 * URIs stay readable across process restarts (picker grants are otherwise temporary).
 */
class PhotoFileStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
    private val counter = AtomicInteger(0)

    fun isPersisted(uriString: String): Boolean = uriString.contains("/$DIR_NAME/")

    /**
     * Copies the content behind [uri] into private storage and returns a `file://` URI string,
     * or null if the copy failed. Must be called off the main thread.
     */
    fun persist(uri: Uri): String? = runCatching {
        val file = File(dir, "photo_${System.currentTimeMillis()}_${counter.incrementAndGet()}.${extensionFor(uri)}")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun extensionFor(uri: Uri): String =
        when (appContext.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }

    companion object {
        private const val DIR_NAME = "trip_photos"
    }
}
