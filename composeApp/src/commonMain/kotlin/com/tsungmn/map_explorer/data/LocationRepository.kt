package com.tsungmn.map_explorer.data

import com.tsungmn.map_explorer.model.LocationPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LocationRepository {

    private val _latestLocation = MutableStateFlow<LocationPoint?>(null)
    val latestLocation = _latestLocation.asStateFlow()

    private val _path = MutableStateFlow<List<LocationPoint>>(emptyList())
    val path = _path.asStateFlow()

    private const val MIN_ADD_DISTANCE_M = 5.0

    fun update(point: LocationPoint) {
        _latestLocation.value = point

        _path.update { old ->
            if (old.isNotEmpty()) {
                val last = old.last()
                if (last.distanceTo(point) < MIN_ADD_DISTANCE_M) return@update old
            }
            old + point
        }
    }

    fun clearPath() {
        _path.value = emptyList()
    }
}