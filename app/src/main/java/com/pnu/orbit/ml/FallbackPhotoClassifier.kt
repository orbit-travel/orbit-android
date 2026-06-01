package com.pnu.orbit.ml

import android.content.Context
import android.net.Uri
import com.pnu.orbit.domain.model.PhotoTag

class FallbackPhotoClassifier(
    @Suppress("unused") private val context: Context,
) : PhotoClassifier {
    override suspend fun classify(uri: Uri): PhotoTag = PhotoTag.UNKNOWN
}
