package com.project.oasg

import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest


class LocationService(
    private val context: Context,
    private val handleLocationUpdate: (Location) -> Unit
) {
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback


    init {
        createLocationRequest()
        initializeLocationCallback()
    }

    private fun createLocationRequest() {
        // TODO: Use location request builder
        locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun initializeLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    handleLocationUpdate(location)
                }
            }
        }
    }

    fun startOwnLocationUpdates(locationPermissionRequest: ActivityResultLauncher<String>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    fun stopOwnLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
