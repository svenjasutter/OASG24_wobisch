package com.project.oasg

import android.location.Location
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

data class UserLocation(
    var userId: String? = null,
    var userMail: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long? = null
)


class DatabaseService(private val authService: AuthenticationService) {
    private var databaseReference = FirebaseDatabase.getInstance().getReference("locations")

    fun updateLocation(location: Location){
        val locationMap = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to ServerValue.TIMESTAMP
        )
        val userid = authService.getCurrentUserId()
//        Log.d("Userid: ", userid)
        if (userid != ""){
            databaseReference.child(userid).setValue(locationMap)
                .addOnSuccessListener {
//                    Log.d("Database", "Location saved successfully!")
                }
                .addOnFailureListener {
                    Log.d("Database", "Failed to write location", it)
                }
        }
    }

    fun getAllLocations(callback: (List<UserLocation>) -> Unit) {
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<UserLocation>()
                for (childSnapshot in snapshot.children) {
                    val location = childSnapshot.getValue(UserLocation::class.java)
                    location?.userId = childSnapshot.key
                    childSnapshot.key?.let { userId ->
                        authService.getUserEmailFromUserId(userId) { email ->
                            location?.userMail = email
                            if (location != null) {
                                locations.add(location)
                            }
                            if (locations.size == snapshot.childrenCount.toInt()) {
                                callback(locations)
                            }
                        }
                    }
                }
                if (snapshot.childrenCount == 0L) {
                    callback(emptyList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("Database", "Error fetching location data", error.toException())
                callback(emptyList())
            }
        })
    }

    fun trackUserLocation(userId: String, callback: (UserLocation) -> Unit): ValueEventListener {
        val userRef = databaseReference.child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(UserLocation::class.java)
                Log.d("Location Change of selected user", location?.latitude.toString() + location?.longitude.toString())
                location?.let { callback(it) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.d("Database", "Error tracking user location", error.toException())
            }
        }
        userRef.addValueEventListener(listener)
        return listener
    }

    fun getReferenceForUser(userId: String) = databaseReference.child(userId)


}