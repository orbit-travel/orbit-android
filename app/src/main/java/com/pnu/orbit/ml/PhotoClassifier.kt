package com.pnu.orbit.ml

import android.net.Uri
import com.pnu.orbit.domain.model.PhotoTag

interface PhotoClassifier {
    suspend fun classify(uri: Uri): PhotoTag
}
