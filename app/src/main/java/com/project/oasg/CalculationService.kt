package com.project.oasg

import android.location.Location
import android.util.Log

class CalculationService {
    fun getDistanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        Log.d("Distance", "Start Distance Calculation")
        val locationA = Location("point A")
        locationA.latitude = lat1
        locationA.longitude = lon1

        val locationB = Location("point B")
        locationB.latitude = lat2
        locationB.longitude = lon2

        val distance = locationA.distanceTo(locationB)
        Log.d("Distance", "$distance meters")
        return distance
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        Log.d("Bearing", "Start Bearing Calculation")
        val locationA = Location("point A")
        locationA.latitude = lat1
        locationA.longitude = lon1

        val locationB = Location("point B")
        locationB.latitude = lat2
        locationB.longitude = lon2

        val bearing = locationA.bearingTo(locationB)
        Log.d("Bearing", "$bearing degrees")
        return bearing
    }


}