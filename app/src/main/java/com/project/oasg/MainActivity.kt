package com.project.oasg

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.project.oasg.ui.theme.OASGTheme
import android.Manifest


class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthenticationService
    private val isUserLoggedIn = mutableStateOf(false)
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        isUserLoggedIn.value = res.resultCode == RESULT_OK
    }

    private lateinit var locationService: LocationService
    private val locationPermissionRequest: ActivityResultLauncher<String> by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                locationService.startLocationUpdates(locationPermissionRequest)
            } else {
                Log.e("LocationUpdate", "Permission denied by user")
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthenticationService(this)
        isUserLoggedIn.value = authService.isUserLoggedIn()

        locationService = LocationService(this) { location ->
            Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude}")
        }
        locationService.startLocationUpdates(locationPermissionRequest)

        setContent {
            val context = LocalContext.current
            OASGTheme {
                if (isUserLoggedIn.value) {
                    StartBlock(name = authService.getCurrentUserEmail(), onSignOut = {
                        if (::locationService.isInitialized){
                            locationService.stopLocationUpdates()
                        }
                        authService.signOut { isUserLoggedIn.value = false }
                    })
                } else {
                    LoginScreen(onSignIn = {
                        authService.launchSignIn(signInLauncher)
                    })
                }
            }
        }
    }

    // this function pauses the location update if the app is not in the foreground
//    override fun onPause() {
//        super.onPause()
//        locationService.stopLocationUpdates()
//    }
}

@Composable
fun LoginScreen(onSignIn: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Please sign in")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSignIn) {
            Text("Sign In")
        }
    }
}

@Composable
fun StartBlock(name: String, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Hello $name!")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSignOut) {
            Text("Sign Out")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StartBlockPreview() {
    OASGTheme {
        StartBlock("You", onSignOut = {})
    }
}