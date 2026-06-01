package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.TravelPhoto
import com.pnu.orbit.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>
    fun observePhotos(tripId: Long): Flow<List<TravelPhoto>>
    suspend fun getTrip(tripId: Long): Trip?
    suspend fun addTrip(trip: Trip, photos: List<TravelPhoto>): Long
    suspend fun updatePhotoComment(photoId: Long, comment: String?)
}
