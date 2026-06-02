package com.pnu.orbit.data.local.asset

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AirportDataSource(
    private val context: Context,
) {
    suspend fun loadAirports(): List<Airport> = withContext(Dispatchers.IO) {
        context.assets.open(AIRPORTS_FILE).bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull(::parseAirport)
                .toList()
        }
    }

    private fun parseAirport(line: String): Airport? {
        val columns = parseCsvLine(line)
        if (columns.size < EXPECTED_COLUMNS) return null

        val lat = columns[6].toDoubleOrNull() ?: return null
        val lng = columns[7].toDoubleOrNull() ?: return null
        return Airport(
            iataCode = columns[0],
            ident = columns[1],
            name = columns[2],
            type = columns[3],
            municipality = columns[4].ifBlank { null },
            countryCode = columns[5],
            lat = lat,
            lng = lng,
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values.add(current.toString())
        return values
    }

    companion object {
        private const val AIRPORTS_FILE = "airports.csv"
        private const val EXPECTED_COLUMNS = 8
    }
}
