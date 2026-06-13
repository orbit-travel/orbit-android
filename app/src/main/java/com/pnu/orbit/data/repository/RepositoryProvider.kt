package com.pnu.orbit.data.repository

import android.content.Context
import com.pnu.orbit.data.local.db.OrbitDatabase
import com.pnu.orbit.data.remote.client.RetrofitClient

object RepositoryProvider {
    private var tripRepositoryInstance: TripRepository? = null
    private var plannerRepositoryInstance: PlannerRepository? = null

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

    fun earthRepository(): EarthRepository = DummyEarthRepository()
}
