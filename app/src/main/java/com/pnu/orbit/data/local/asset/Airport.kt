package com.pnu.orbit.data.local.asset

data class Airport(
    val iataCode: String,
    val ident: String,
    val name: String,
    val type: String,
    val municipality: String?,
    val countryCode: String,
    val lat: Double,
    val lng: Double,
) {
    val displayName: String
        get() = buildString {
            append(iataCode)
            append(" - ")
            append(name)
            municipality?.takeIf { it.isNotBlank() }?.let {
                append(" (")
                append(it)
                append(")")
            }
        }
}
