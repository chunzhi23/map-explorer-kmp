package com.tsungmn.map_explorer.utils

import com.mapbox.geojson.Point
import com.tsungmn.map_explorer.model.LocationPoint

fun LocationPoint.toMapboxPoint(): Point = Point.fromLngLat(longitude, latitude)

fun Point.toLocationPoint(): LocationPoint = LocationPoint(longitude(), latitude())