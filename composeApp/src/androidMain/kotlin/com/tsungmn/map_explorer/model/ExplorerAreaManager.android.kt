package com.tsungmn.map_explorer.model

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * ExplorerAreaManager (Android actual)
 *
 * Responsibilities:
 *  - Maintain an accumulated explored Geometry (JTS, WebMercator meters).
 *  - Incrementally union buffered points/line-corridors when addPoint(...) is called.
 *  - Prevent unrealistic "teleport" connections (e.g., subway tunnel gaps).
 *  - Persist/restore geometry as WKB.
 *  - Export GeoJSON Polygon: [ worldOuterRing, ...innerRings ]
 */
actual object ExplorerAreaManager {

    // ---------- Configuration ----------
    private const val DEFAULT_BUFFER_METERS = 15.0
    private const val MAX_CONNECT_DISTANCE_METERS = 10_000.0
    private const val MAX_NO_GPS_INTERVAL_SECONDS = 30.0
    private const val MIN_TELEPORT_DISTANCE_METERS = 100.0

    private const val PERSISTENCE_FILENAME = "explored.wkb"

    // WebMercator
    private const val R_MAJOR = 6378137.0
    private const val ORIGIN_SHIFT = 2.0 * Math.PI * R_MAJOR / 2.0

    // ---------- JTS / state ----------
    private val geomFactory = GeometryFactory(PrecisionModel(), 0)
    private val exploredRef = AtomicReference<Geometry?>(null)
    private val unionMutex = Mutex()

    private val lastMercator = AtomicReference<Pair<Double, Double>?>(null)
    private val lastTimestampMs = AtomicReference<Long?>(null)

    // Debug / visualization
    private val tunnelSegments = ArrayList<List<Pair<Double, Double>>>()

    // ---------- Persistence ----------
    fun saveToFile(cacheDir: File) {
        val geom = exploredRef.get() ?: return
        try {
            val bytes = WKBWriter().write(geom)
            File(cacheDir, PERSISTENCE_FILENAME).writeBytes(bytes)
        } catch (_: Exception) {
        }
    }

    fun loadFromFile(cacheDir: File) {
        val file = File(cacheDir, PERSISTENCE_FILENAME)
        if (!file.exists()) return
        try {
            exploredRef.set(WKBReader().read(file.readBytes()))
        } catch (_: Exception) {
        }
    }

    // ---------- Core ----------
    suspend fun addPoint(
        locationPoint: LocationPoint,
        bufferMeters: Double = DEFAULT_BUFFER_METERS,
        timestampMs: Long
    ) {
        val (mx, my) = lonLatToMercator(
            locationPoint.longitude,
            locationPoint.latitude
        )

        val prev = lastMercator.get()
        val prevTs = lastTimestampMs.get()

        val shouldConnect = decideShouldConnect(prev, prevTs, mx, my, timestampMs)

        val newGeom: Geometry = if (shouldConnect && prev != null) {
            val coords = arrayOf(
                Coordinate(prev.first, prev.second),
                Coordinate(mx, my)
            )
            geomFactory.createLineString(coords).buffer(bufferMeters)
        } else {
            if (prev != null && prevTs != null) {
                if (isLikelyTunnelGap(prev, prevTs, mx, my, timestampMs)) {
                    try {
                        tunnelSegments.add(listOf(prev, Pair(mx, my)))
                    } catch (_: Exception) {
                    }
                }
            }
            geomFactory.createPoint(Coordinate(mx, my)).buffer(bufferMeters)
        }

        unionMutex.withLock {
            val current = exploredRef.get()
            exploredRef.set(if (current == null) newGeom else current.union(newGeom))
            lastMercator.set(Pair(mx, my))
            lastTimestampMs.set(timestampMs)
        }
    }

    // ---------- Connection / teleport logic ----------
    private fun decideShouldConnect(
        prev: Pair<Double, Double>?,
        prevTs: Long?,
        mx: Double,
        my: Double,
        timestampMs: Long
    ): Boolean {
        if (prev == null || prevTs == null) return false

        val dist = computeDistMeters(prev, mx, my)
        val withinRange = dist <= MAX_CONNECT_DISTANCE_METERS
        val tunnelGap = isLikelyTunnelGap(prev, prevTs, mx, my, timestampMs)

        return withinRange && !tunnelGap
    }

    private fun isLikelyTunnelGap(
        prev: Pair<Double, Double>,
        prevTs: Long,
        mx: Double,
        my: Double,
        timestampMs: Long
    ): Boolean {
        val dist = computeDistMeters(prev, mx, my)
        val dtSec = computeDtSec(prevTs, timestampMs)
        return dtSec >= MAX_NO_GPS_INTERVAL_SECONDS &&
                dist >= MIN_TELEPORT_DISTANCE_METERS
    }

    private fun computeDistMeters(
        prev: Pair<Double, Double>,
        mx: Double,
        my: Double
    ): Double {
        val dx = mx - prev.first
        val dy = my - prev.second
        return sqrt(dx * dx + dy * dy)
    }

    private fun computeDtSec(prevTs: Long, timestampMs: Long): Double =
        (timestampMs - prevTs) / 1000.0

    // ---------- GeoJSON export ----------
    fun toGeoJsonPolygon(): Polygon {
        val geom = exploredRef.get() ?: return Polygon.fromLngLats(listOf(worldRing()))
        val innerRings = ArrayList<List<Point>>()

        for (i in 0 until geom.numGeometries) {
            val g = geom.getGeometryN(i)
            if (g is org.locationtech.jts.geom.Polygon) {
                val ring = g.exteriorRing.coordinates.map {
                    val (lon, lat) = mercatorToLonLat(it.x, it.y)
                    Point.fromLngLat(lon, lat)
                }
                val fixed = if (signedAreaLonLat(ring) > 0) ring.reversed() else ring
                innerRings.add(fixed)
            }
        }

        val outer = worldRing()
        val fixedOuter =
            if (signedAreaLonLat(outer) < 0) outer.reversed() else outer

        return Polygon.fromLngLats(listOf(fixedOuter) + innerRings)
    }

    private fun signedAreaLonLat(points: List<Point>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            sum += (a.longitude() * b.latitude()) -
                    (b.longitude() * a.latitude())
        }
        return sum / 2.0
    }

    // ---------- Reset / rebuild ----------
    suspend fun reset() {
        unionMutex.withLock {
            exploredRef.set(null)
            lastMercator.set(null)
            lastTimestampMs.set(null)
            tunnelSegments.clear()
        }
    }

    suspend fun rebuildFromPoints(
        points: List<LocationPoint>,
        bufferMetersProvider: (LocationPoint) -> Double = { DEFAULT_BUFFER_METERS },
        batchSize: Int = 200
    ) {
        reset()
        var idx = 0
        while (idx < points.size) {
            val end = minOf(points.size, idx + batchSize)
            for (pt in points.subList(idx, end)) {
                addPoint(pt, bufferMetersProvider(pt), System.currentTimeMillis())
            }
            idx = end
        }
    }

    // ---------- Projection helpers ----------
    private fun lonLatToMercator(lon: Double, lat: Double): Pair<Double, Double> {
        val x = lon * ORIGIN_SHIFT / 180.0
        val y = ln(tan((90.0 + lat) * PI / 360.0)) / (PI / 180.0)
        return Pair(x, y * ORIGIN_SHIFT / 180.0)
    }

    private fun mercatorToLonLat(x: Double, y: Double): Pair<Double, Double> {
        val lon = (x / ORIGIN_SHIFT) * 180.0
        var lat = (y / ORIGIN_SHIFT) * 180.0
        lat = 180.0 / PI * (2.0 * atan(exp(lat * PI / 180.0)) - PI / 2.0)
        return Pair(lon, lat)
    }

    private fun worldRing(): List<Point> = listOf(
        Point.fromLngLat(-180.0, -90.0),
        Point.fromLngLat(180.0, -90.0),
        Point.fromLngLat(180.0, 90.0),
        Point.fromLngLat(-180.0, 90.0),
        Point.fromLngLat(-180.0, -90.0)
    )

    // ---------- Statistics ----------
    private const val EARTH_SURFACE_AREA_M2 = 5.10072e14
    private const val EARTH_LAND_AREA_M2 = 1.4894e14

    actual fun exploredAreaMeters(): Double =
        exploredRef.get()?.area ?: 0.0

    actual fun exploredPercentOfEarth(): Double =
        (exploredAreaMeters() / EARTH_SURFACE_AREA_M2) * 100.0

    actual fun exploredPercentOfLand(): Double =
        (exploredAreaMeters() / EARTH_LAND_AREA_M2) * 100.0
}