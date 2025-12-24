package com.tsungmn.map_explorer.service

import android.location.Location
import android.util.Log
import com.tsungmn.map_explorer.model.MovementState
import kotlin.math.max

/**
 * MovementDetector
 *
 * Uses a small sliding window of speed values to reduce noise,
 * computes a moving average, and maps that average to discrete movement states.
 *
 * To avoid flipping states too frequently, a detection must be confirmed
 * for `requiredConfirmCount` consecutive times and respect a minimum switch interval.
 */
class MovementDetector {
    private val speedWindow = ArrayDeque<Float>()
    private val windowSize = 3

    private var candidateState: MovementState? = null
    private var candidateCount = 0

    private var lastState: MovementState = MovementState.STOPPED
    private var lastSwitchTime = 0L

    // require consecutive confirmations and a minimum time between switches
    private val requiredConfirmCount = 3
    private val minSwitchIntervalMs = 5_000L

    /**
     * Feed a new Location; returns a new MovementState when a confirmed change occurs,
     * otherwise returns null.
     */
    fun onLocation(location: Location): MovementState? {
        val speed = location.speed // m/s

        if (speedWindow.size >= windowSize) speedWindow.removeFirst()
        speedWindow.addLast(max(0f, speed))
        val avgSpeed = speedWindow.average().toFloat()

        val detected = detectFromSpeed(avgSpeed)

        if (detected == candidateState) {
            candidateCount++
        } else {
            candidateState = detected
            candidateCount = 1
        }

        val now = System.currentTimeMillis()
        if (candidateCount >= requiredConfirmCount && detected != lastState && (now - lastSwitchTime) >= minSwitchIntervalMs) {
            lastState = detected
            lastSwitchTime = now
            Log.d("MovementDetector", "State changed to $detected")
            return detected
        }
        return null
    }

    /**
     * Small helper to map an instantaneous speed to a MovementState.
     */
    fun stateFromSpeedInstant(speed: Float): MovementState {
        val s = if (speed.isFinite() && speed >= 0f) speed else 0f
        return detectFromSpeed(s)
    }

    /**
     * Core mapping from speed (m/s) to MovementState buckets.
     */
    private fun detectFromSpeed(speed: Float): MovementState {
        // thresholds (m/s) corresponding approximately to:
        // WALKING < 1.7 m/s (~6 km/h)
        // BIKE   < 7.0 m/s (~25 km/h)
        // ROAD   < 19.44 m/s (~70 km/h)
        // HIGHWAY< 36.11 m/s (~130 km/h)
        return when {
            speed < 0.5f -> MovementState.STOPPED
            speed < 1.7f -> MovementState.WALKING
            speed < 7.0f -> MovementState.BIKE
            speed < 19.44f -> MovementState.ROAD
            speed < 36.11f -> MovementState.HIGHWAY
            else -> MovementState.TRAIN
        }
    }
}