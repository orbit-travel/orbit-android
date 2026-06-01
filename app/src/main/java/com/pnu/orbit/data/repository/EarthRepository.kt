package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.EarthPreview

interface EarthRepository {
    fun getEarthPreviews(): List<EarthPreview>
}
