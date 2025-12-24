package com.tsungmn.map_explorer.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun distanceTo(other: LocationPoint): Double {
        val r = 6_371_000

        val lat1 = latitude * PI / 180
        val lat2 = other.latitude * PI / 180
        val dLat = (other.latitude - latitude) * PI / 180
        val dLon = (other.longitude - longitude) * PI / 180

        val a = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)

        return 2 * r * asin(sqrt(a))
    }
}