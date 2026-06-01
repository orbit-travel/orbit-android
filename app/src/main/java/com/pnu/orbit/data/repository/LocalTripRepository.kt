package com.pnu.orbit.data.repository

import com.pnu.orbit.data.local.dao.PhotoDao
import com.pnu.orbit.data.local.dao.TripDao
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalTripRepository(
    private val tripDao: TripDao,
    private val photoDao: PhotoDao,
) : TripRepository {
    override fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeTrips().map { entities -> entities.map { it.toDomain() } }

    override fun observePhotos(tripId: Long): Flow<List<TravelPhoto>> =
        photoDao.observePhotos(tripId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTrip(tripId: Long): Trip? =
        tripDao.getTrip(tripId)?.toDomain()

    override suspend fun addTrip(trip: Trip, photos: List<TravelPhoto>): Long {
        val newTripId = tripDao.insertTrip(trip.copy(id = 0L).toEntity())
        if (photos.isNotEmpty()) {
            photoDao.insertPhotos(photos.map { it.copy(id = 0L).toEntity(newTripId) })
        }
        return newTripId
    }

    override suspend fun updatePhotoComment(photoId: Long, comment: String?) {
        photoDao.updateComment(photoId, comment)
    }
}
