package com.pnu.orbit.data.repository

import com.pnu.orbit.domain.model.EarthPreview
import com.pnu.orbit.util.DemoFallbacks

class DummyEarthRepository : EarthRepository {
    override fun getEarthPreviews(): List<EarthPreview> = DemoFallbacks.earthPreviews()
}
