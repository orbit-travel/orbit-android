package com.pnu.orbit.data.repository

import com.pnu.orbit.data.local.dao.PhotoDao
import com.pnu.orbit.data.local.dao.TransportSegmentDao
import com.pnu.orbit.data.local.dao.TripDao
import com.pnu.orbit.data.mapper.toDomain
import com.pnu.orbit.data.mapper.toEntity
import com.pnu.orbit.domain.model.NewTravelPhoto
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalTripRepository(
    private val tripDao: TripDao,
    private val transportSegmentDao: TransportSegmentDao,
    private val photoDao: PhotoDao,
) : TripRepository {
    override fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeTripsWithPhotoCount().map { entities -> entities.map { it.toDomain() } }

    override fun observeSegments(tripId: Long): Flow<List<TransportSegment>> =
        transportSegmentDao.observeSegments(tripId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observePhotos(tripId: Long): Flow<List<TravelPhoto>> =
        photoDao.observePhotos(tripId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTrip(tripId: Long): Trip? =
        tripDao.getTrip(tripId)?.toDomain()

    override suspend fun getSegments(tripId: Long): List<TransportSegment> =
        transportSegmentDao.getSegments(tripId).map { it.toDomain() }

    override suspend fun addTrip(
        trip: Trip,
        segments: List<TransportSegment>,
        photos: List<NewTravelPhoto>,
    ): Long {
        val newTripId = tripDao.insertTrip(trip.copy(id = 0L).toEntity())
        val segmentIds = if (segments.isNotEmpty()) {
            transportSegmentDao.insertSegments(
                segments.mapIndexed { index, segment ->
                    segment.copy(id = 0L, tripId = newTripId, sortOrder = index)
                        .toEntity(tripIdOverride = newTripId)
                },
            )
        } else {
            emptyList()
        }
        if (photos.isNotEmpty()) {
            photoDao.insertPhotos(
                photos.map { photo ->
                    val segmentId = photo.segmentSortOrder
                        ?.takeIf { it in segmentIds.indices }
                        ?.let { segmentIds[it] }
                    photo.toEntity(tripId = newTripId, segmentId = segmentId)
                },
            )
        }
        return newTripId
    }

    override suspend fun updatePhotoComment(photoId: Long, comment: String?) {
        photoDao.updateComment(photoId, comment)
    }
}
