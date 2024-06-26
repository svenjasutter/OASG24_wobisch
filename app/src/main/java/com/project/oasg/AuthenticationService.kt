package com.project.oasg

import android.content.Context
import android.content.Intent
import android.util.Log
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AuthenticationService(private val context: Context) {
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var usersReference = FirebaseDatabase.getInstance().getReference("users")

    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    fun getCurrentUserEmail(): String {
        return firebaseAuth.currentUser?.email ?: "Not signed in"
    }

    fun getUserEmailFromUserId(userId: String, callback: (String) -> Unit) {
//        Log.d("data", usersReference.toString())
        usersReference.child(userId).child("email").get().addOnSuccessListener { dataSnapshot ->
            val email = dataSnapshot.getValue(String::class.java)
            if (email != null) {
                callback(email)
            } else {
//                Log.d("Database", "No email found for user: $userId")
                callback("Email not found")
            }
        }.addOnFailureListener {
            Log.d("Database", "Failed to fetch email for user: $userId", it)
            callback("Failed to fetch email")
        }
    }

    fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: ""
    }

    fun launchSignIn(signInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val providers = arrayListOf(
            AuthUI.IdpConfig.GoogleBuilder().build()
        )
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
        signInLauncher.launch(signInIntent)
    }

    fun signOut(onComplete: () -> Unit) {
        AuthUI.getInstance()
            .signOut(context)
            .addOnCompleteListener {
                onComplete()
            }
    }
}
