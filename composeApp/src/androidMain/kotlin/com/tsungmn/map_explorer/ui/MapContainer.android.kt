package com.tsungmn.map_explorer.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.tsungmn.map_explorer.data.LocationRepository
import com.tsungmn.map_explorer.model.ExplorerAreaManager
import com.tsungmn.map_explorer.utils.toMapboxPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
actual fun MapContainer() {
    val mapViewportState = rememberMapViewportState()
    val latestLocation by LocationRepository.latestLocation.collectAsState()
    var didInitialMove by remember { mutableStateOf(false) }
    var fogPolygon by remember { mutableStateOf<Polygon?>(null) }

    // MapboxMap composable
    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            logo = {}, scaleBar = {}, attribution = {}
        ) {
            // Re-run when fogPolygon or tunnelSegments changes
            MapEffect(fogPolygon) { mapView ->
                val mapboxMap = mapView.mapboxMap

                mapboxMap.getStyle { style ->
                    // Ensure fog source/layer
                    ensureFogSourceAndLayer(style)

                    // Update fog source feature
                    val fogSrc = style.getSourceAs<GeoJsonSource>("fog-source")
                    fogPolygon?.let { poly ->
                        fogSrc?.feature(Feature.fromGeometry(poly))
                    }
                }

                // location puck permission-aware
                val ctx = mapView.context
                val fineGranted =
                    ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted =
                    ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                mapView.location.updateSettings {
                    enabled = fineGranted || coarseGranted
                    locationPuck = createDefault2DPuck(withBearing = true)
                    puckBearing = PuckBearing.COURSE
                    puckBearingEnabled = true
                }
            }
        }

        // overlay UI (statusbar padding)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ExplorerStatsOverlay()
        }

        if (!didInitialMove) {
            PendingOverlay()
        }
    }

    // camera initial fly-to when first location arrives
    LaunchedEffect(latestLocation) {
        if (latestLocation != null && !didInitialMove) {
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(latestLocation!!.toMapboxPoint())
                    .zoom(16.0)
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(1200) }
            )
            didInitialMove = true
        }
    }

    // fog polygon refresh loop (runs on Default dispatcher)
    LaunchedEffect(Unit) {
        while (true) {
            fogPolygon = withContext(Dispatchers.Default) {
                try {
                    ExplorerAreaManager.toGeoJsonPolygon()
                } catch (_: Throwable) {
                    null
                }
            }
            delay(1000L)
        }
    }
}

/** style에 fog-source / fog-layer가 없으면 생성해준다. */
private fun ensureFogSourceAndLayer(style: Style) {
    val existing = style.getSourceAs<GeoJsonSource>("fog-source")
    if (existing != null) return

    style.addSource(
        geoJsonSource("fog-source") {
            feature(Feature.fromGeometry(ExplorerAreaManager.toGeoJsonPolygon()))
        }
    )

    style.addLayer(
        fillLayer("fog-layer", "fog-source") {
            fillColor("#000000")
            fillOpacity(0.6)
            fillAntialias(true)
        }
    )
}