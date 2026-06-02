package com.pnu.orbit.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pnu.orbit.data.local.dao.PhotoDao
import com.pnu.orbit.data.local.dao.PlanDao
import com.pnu.orbit.data.local.dao.TransportSegmentDao
import com.pnu.orbit.data.local.dao.TripDao
import com.pnu.orbit.data.local.entity.PhotoEntity
import com.pnu.orbit.data.local.entity.PlanEntity
import com.pnu.orbit.data.local.entity.TransportSegmentEntity
import com.pnu.orbit.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        TransportSegmentEntity::class,
        PhotoEntity::class,
        PlanEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun transportSegmentDao(): TransportSegmentDao
    abstract fun photoDao(): PhotoDao
    abstract fun planDao(): PlanDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transport_segments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tripId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `departureName` TEXT NOT NULL,
                        `arrivalName` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transport_segments_tripId` " +
                        "ON `transport_segments` (`tripId`)",
                )
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `segmentId` INTEGER")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `locationName` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_photos_segmentId` ON `photos` (`segmentId`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transport_segments` ADD COLUMN `departureLat` REAL")
                db.execSQL("ALTER TABLE `transport_segments` ADD COLUMN `departureLng` REAL")
                db.execSQL("ALTER TABLE `transport_segments` ADD COLUMN `arrivalLat` REAL")
                db.execSQL("ALTER TABLE `transport_segments` ADD COLUMN `arrivalLng` REAL")
            }
        }

        @Volatile
        private var instance: OrbitDatabase? = null

        fun getInstance(context: Context): OrbitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
