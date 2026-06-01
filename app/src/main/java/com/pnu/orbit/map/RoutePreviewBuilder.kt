package com.pnu.orbit.map

import com.pnu.orbit.domain.model.TravelPhoto

object RoutePreviewBuilder {
    fun markersFromPhotos(photos: List<TravelPhoto>, fallbackLat: Double, fallbackLng: Double): List<MapMarkerModel> =
        photos.map { photo ->
            MapMarkerModel(
                id = photo.id,
                title = photo.comment ?: "Photo ${photo.id}",
                lat = photo.lat ?: fallbackLat,
                lng = photo.lng ?: fallbackLng,
                photoUri = photo.uri,
            )
        }
}
