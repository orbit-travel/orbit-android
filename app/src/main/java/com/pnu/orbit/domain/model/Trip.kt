package com.pnu.orbit.domain.model

data class Trip(
    val id: Long,
    val title: String,
    val startPlace: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val coverPhotoUri: String?,
    val memo: String?,
    val photoCount: Int = 0,
)
