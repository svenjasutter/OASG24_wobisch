package com.project.oasg

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.project.oasg.ui.theme.OASGTheme


class MainActivity : ComponentActivity() {
    private lateinit var authService: AuthenticationService
    private val isUserLoggedIn = mutableStateOf(false)
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            isUserLoggedIn.value = true
            Log.d("LOGIN", "init location service")
            initializeLocationService()
        } else{
            isUserLoggedIn.value = false
        }
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

    private lateinit var databaseService: DatabaseService
    private val usersData = mutableStateOf<List<UserLocation>>(listOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthenticationService(this)
        isUserLoggedIn.value = authService.isUserLoggedIn()

        databaseService = DatabaseService(authService)

        initializeLocationService()

        setContent {
            val context = LocalContext.current
            OASGTheme {
                if (isUserLoggedIn.value) {
                    StartBlock(name = authService.getCurrentUserEmail(),
                        onSignOut = {
                        if (::locationService.isInitialized){
                            locationService.stopLocationUpdates()
                        }
                        authService.signOut { isUserLoggedIn.value = false }
                        },
                        onGetData = {
                            getLocationData()  // This will trigger the data fetching when the button is clicked
                        }
                    )
                    UsersList(usersData.value) { user ->
                        Log.d("UI", "Clicked on user ${user.userId}")
                    }
                } else {
                    LoginScreen(onSignIn = {
                        authService.launchSignIn(signInLauncher)
                    })
                }
            }
        }
    }

    private fun initializeLocationService() {
        locationService = LocationService(this) { location ->
            Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude}")
            databaseService.updateLocation(location)
        }
        locationService.startLocationUpdates(locationPermissionRequest)
    }

    private fun getLocationData() {
        databaseService.getAllLocations { locations ->
            for (location in locations) {
                Log.d("App", "User: ${location.userId} Location: ${location.latitude}, ${location.longitude}, ${location.timestamp}")
            }
            usersData.value = locations
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
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text("Please sign in")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSignIn) {
            Text("Sign In")
        }
    }
}

@Composable
fun StartBlock(name: String, onSignOut: () -> Unit, onGetData: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Hello $name!")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSignOut) {
            Text("Sign Out")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGetData) {
            Text("Get All User Data")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StartBlockPreview() {
    OASGTheme {
        StartBlock("You", onSignOut = {}, onGetData = {})
    }
}

@Composable
fun UsersList(users: List<UserLocation>, onUserClick: (UserLocation) -> Unit) {
    Column(modifier = Modifier.padding(164.dp)) {
        for (user in users) {
            Button(
                onClick = { onUserClick(user) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(text = "Location for user ${user.userId}: ${user.userMail}")
            }
        }
    }
}
