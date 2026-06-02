package com.pnu.orbit.map

import com.google.android.gms.maps.model.LatLng
import kotlin.math.absoluteValue

object PlaceCoordinateResolver {
    fun resolve(placeName: String): LatLng {
        val normalized = placeName.lowercase()
        KNOWN_PLACES.firstOrNull { (key, _) -> normalized.contains(key) }?.let { return it.second }

        val hash = normalized.hashCode().absoluteValue
        val latOffset = ((hash % 1200) / 100.0) - 6.0
        val lngOffset = (((hash / 1200) % 1400) / 100.0) - 7.0
        return LatLng(DEFAULT_CENTER.latitude + latOffset, DEFAULT_CENTER.longitude + lngOffset)
    }

    private val DEFAULT_CENTER = LatLng(35.1796, 129.0756)
    private val KNOWN_PLACES = listOf(
        "pnu" to LatLng(35.2339, 129.0835),
        "pusan national" to LatLng(35.2339, 129.0835),
        "busan" to LatLng(35.1796, 129.0756),
        "haeundae" to LatLng(35.1587, 129.1604),
        "seoul station" to LatLng(37.5547, 126.9706),
        "seoul" to LatLng(37.5665, 126.9780),
        "gyeongbokgung" to LatLng(37.5796, 126.9770),
        "incheon airport" to LatLng(37.4602, 126.4407),
        "icn" to LatLng(37.4602, 126.4407),
        "london heathrow" to LatLng(51.4700, -0.4543),
        "heathrow" to LatLng(51.4700, -0.4543),
        "london" to LatLng(51.5072, -0.1276),
        "oxford" to LatLng(51.7520, -1.2577),
    )
}
