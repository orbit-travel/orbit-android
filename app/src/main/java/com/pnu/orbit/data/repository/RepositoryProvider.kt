package com.pnu.orbit.data.repository

import android.content.Context
import com.pnu.orbit.data.local.db.OrbitDatabase
import com.pnu.orbit.data.remote.client.RetrofitClient
import com.pnu.orbit.ml.PhotoClassifier
import com.pnu.orbit.ml.TfLitePhotoClassifier

object RepositoryProvider {
    private var tripRepositoryInstance: TripRepository? = null
    private var plannerRepositoryInstance: PlannerRepository? = null
    private var photoClassifierInstance: PhotoClassifier? = null

    fun tripRepository(context: Context): TripRepository {
        return tripRepositoryInstance ?: synchronized(this) {
            tripRepositoryInstance ?: LocalTripRepository(
                tripDao = OrbitDatabase.getInstance(context).tripDao(),
                transportSegmentDao = OrbitDatabase.getInstance(context).transportSegmentDao(),
                photoDao = OrbitDatabase.getInstance(context).photoDao(),
            ).also { tripRepositoryInstance = it }
        }
    }

    fun plannerRepository(context: Context): PlannerRepository {
        return plannerRepositoryInstance ?: synchronized(this) {
            plannerRepositoryInstance ?: run {
                val database = OrbitDatabase.getInstance(context)
                val geminiApi = RetrofitClient.geminiApi
                val apiKey = com.pnu.orbit.BuildConfig.GEMINI_API_KEY
                val aiPlanner = com.pnu.orbit.data.remote.api.GeminiPlannerApi(geminiApi, apiKey)
                LocalPlannerRepository(
                    planDao = database.planDao(),
                    aiPlannerApi = aiPlanner,
                ).also { plannerRepositoryInstance = it }
            }
        }
    }

    /**
     * On-device photo classifier (TFLite). [TfLitePhotoClassifier] itself degrades to
     * [com.pnu.orbit.domain.model.PhotoTag.UNKNOWN] when the model asset is missing, so no
     * explicit fallback is needed here. Shared as a singleton to reuse the loaded interpreter.
     */
    fun photoClassifier(context: Context): PhotoClassifier {
        return photoClassifierInstance ?: synchronized(this) {
            photoClassifierInstance ?: TfLitePhotoClassifier(context.applicationContext)
                .also { photoClassifierInstance = it }
        }
    }

    fun earthRepository(): EarthRepository = DummyEarthRepository()
}
