package com.pnu.orbit.domain.model

data class EarthPreview(
    val id: String,
    val type: EarthType,
    val title: String,
    val subtitle: String,
    val coverImageRes: Int?,
    val isRealData: Boolean,
)

enum class EarthType {
    MY,
    FRIENDS,
    WORLD,
}
