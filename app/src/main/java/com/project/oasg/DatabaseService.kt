package com.project.oasg

import android.location.Location
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class DatabaseService(private val authService: AuthenticationService) {
    private var databaseReference = FirebaseDatabase.getInstance().getReference("locations")

    fun updateLocation(location: Location){
        val locationMap = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to ServerValue.TIMESTAMP
        )
        // I only use mail for convenience, should be userid
        val userMail = authService.getCurrentUserEmail()
        Log.d("userMail: ", userMail)
        if (userMail != ""){
            databaseReference.child(userMail).setValue(locationMap)
                .addOnSuccessListener {
                    Log.d("Database", "Location saved successfully!")
                }
                .addOnFailureListener {
                    Log.d("Database", "Failed to write location", it)
                }
        }
    }
}