package com.tsungmn.map_explorer.service

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.tsungmn.map_explorer.model.MovementState

/**
 * LocationRequestFactory
 *
 * Produce LocationRequest instances tuned to different movement states.
 * This centralizes request parameters (intervals, min distance, accuracy).
 */
object LocationRequestFactory {

    /**
     * Default request used at startup.
     * We pick ROAD as a balanced default.
     */
    fun defaultRequest(): LocationRequest =
        requestForState(MovementState.ROAD)

    /**
     * Create a LocationRequest for a given movement state.
     */
    fun requestForState(state: MovementState): LocationRequest {
        return when (state) {

            // Stopped: conserve battery
            MovementState.STOPPED ->
                LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    30_000L
                )
                    .setMinUpdateIntervalMillis(15_000L)
                    .setMinUpdateDistanceMeters(0f)
                    .build()

            // Walking
            MovementState.WALKING ->
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    5_000L
                )
                    .setMinUpdateIntervalMillis(2_000L)
                    .setMinUpdateDistanceMeters(3f)
                    .build()

            // Biking
            MovementState.BIKE ->
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    2_000L
                )
                    .setMinUpdateIntervalMillis(1_000L)
                    .setMinUpdateDistanceMeters(3f)
                    .build()

            // Road driving (city / national roads)
            MovementState.ROAD ->
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1_000L
                )
                    .setMinUpdateIntervalMillis(500L)
                    .setMinUpdateDistanceMeters(2f)
                    .build()

            // Highway driving
            MovementState.HIGHWAY ->
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1_000L
                )
                    .setMinUpdateIntervalMillis(500L)
                    .setMinUpdateDistanceMeters(5f)
                    .build()

            // Train / very fast movement
            MovementState.TRAIN ->
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    1_000L
                )
                    .setMinUpdateIntervalMillis(1_000L)
                    .setMinUpdateDistanceMeters(10f)
                    .setWaitForAccurateLocation(false)
                    .build()
        }
    }
}