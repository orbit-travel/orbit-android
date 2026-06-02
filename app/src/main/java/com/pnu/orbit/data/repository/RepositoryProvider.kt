package com.pnu.orbit.data.repository

import android.content.Context
import com.pnu.orbit.data.local.db.OrbitDatabase
import com.pnu.orbit.data.remote.client.RetrofitClient

object RepositoryProvider {
    fun tripRepository(context: Context): TripRepository {
        val database = OrbitDatabase.getInstance(context)
        return LocalTripRepository(
            tripDao = database.tripDao(),
            transportSegmentDao = database.transportSegmentDao(),
            photoDao = database.photoDao(),
        )
    }

    fun plannerRepository(context: Context): PlannerRepository {
        val database = OrbitDatabase.getInstance(context)
        val geminiApi = RetrofitClient.geminiApi
        val apiKey = com.pnu.orbit.BuildConfig.GEMINI_API_KEY
        val aiPlanner = com.pnu.orbit.data.remote.api.GeminiPlannerApi(geminiApi, apiKey)
        return LocalPlannerRepository(
            planDao = database.planDao(),
            aiPlannerApi = aiPlanner,
        )
    }

    fun earthRepository(): EarthRepository = DummyEarthRepository()
}
