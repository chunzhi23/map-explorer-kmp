package com.tsungmn.map_explorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.tsungmn.map_explorer.service.LocationService

class MainActivity : ComponentActivity() {

    private enum class PermissionStep {
        NOTIFICATION,
        FOREGROUND_LOCATION,
        BACKGROUND_LOCATION
    }

    private var currentStep: PermissionStep? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }

            if (!allGranted) {
                return@registerForActivityResult
            }

            when (currentStep) {
                PermissionStep.NOTIFICATION -> requestForegroundLocation()
                PermissionStep.FOREGROUND_LOCATION -> requestBackgroundLocation()
                PermissionStep.BACKGROUND_LOCATION -> startLocationService()
                null -> Unit
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MapExplorer() }

        requestNotification()
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                currentStep = PermissionStep.NOTIFICATION
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            } else {
                requestForegroundLocation()
            }
        } else {
            requestForegroundLocation()
        }
    }

    private fun requestForegroundLocation() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (toRequest.isNotEmpty()) {
            currentStep = PermissionStep.FOREGROUND_LOCATION
            permissionLauncher.launch(toRequest)
        } else {
            requestBackgroundLocation()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                currentStep = PermissionStep.BACKGROUND_LOCATION
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                )
            } else {
                startLocationService()
            }
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    MapExplorer()
}