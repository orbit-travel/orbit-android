package com.pnu.orbit.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pnu.orbit.data.local.dao.PhotoDao
import com.pnu.orbit.data.local.dao.PlanDao
import com.pnu.orbit.data.local.dao.TripDao
import com.pnu.orbit.data.local.entity.PhotoEntity
import com.pnu.orbit.data.local.entity.PlanEntity
import com.pnu.orbit.data.local.entity.TripEntity

@Database(
    entities = [TripEntity::class, PhotoEntity::class, PlanEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun photoDao(): PhotoDao
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile
        private var instance: OrbitDatabase? = null

        fun getInstance(context: Context): OrbitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit.db",
                ).build().also { instance = it }
            }
    }
}
