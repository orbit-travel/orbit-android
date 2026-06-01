package com.pnu.orbit.map

data class MapMarkerModel(
    val id: Long,
    val title: String,
    val lat: Double,
    val lng: Double,
    val photoUri: String?,
)
