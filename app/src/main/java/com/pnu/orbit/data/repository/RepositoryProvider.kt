package com.pnu.orbit.data.repository

import android.content.Context
import com.pnu.orbit.data.local.db.OrbitDatabase
import com.pnu.orbit.data.remote.client.RetrofitClient

object RepositoryProvider {
    fun tripRepository(context: Context): TripRepository {
        val database = OrbitDatabase.getInstance(context)
        return LocalTripRepository(
            tripDao = database.tripDao(),
            photoDao = database.photoDao(),
        )
    }

    fun plannerRepository(context: Context): PlannerRepository {
        val database = OrbitDatabase.getInstance(context)
        return LocalPlannerRepository(
            planDao = database.planDao(),
            aiPlannerApi = RetrofitClient.aiPlannerApi,
        )
    }

    fun earthRepository(): EarthRepository = DummyEarthRepository()
}
