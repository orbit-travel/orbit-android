package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.NewTravelPhoto
import com.pnu.orbit.domain.model.TransportSegment
import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>
    fun observeSegments(tripId: Long): Flow<List<TransportSegment>>
    fun observePhotos(tripId: Long): Flow<List<TravelPhoto>>
    suspend fun getTrip(tripId: Long): Trip?
    suspend fun getSegments(tripId: Long): List<TransportSegment>
    suspend fun getPhotos(tripId: Long): List<TravelPhoto>
    suspend fun addTrip(
        trip: Trip,
        segments: List<TransportSegment>,
        photos: List<NewTravelPhoto>,
    ): Long
    suspend fun updateTrip(
        trip: Trip,
        segments: List<TransportSegment>,
        photos: List<NewTravelPhoto>,
    )
    suspend fun deleteTrip(tripId: Long)
    suspend fun updatePhotoComment(photoId: Long, comment: String?)
}
