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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


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

    private lateinit var calculationService: CalculationService
    private val currentBearing = mutableStateOf(361f)
    private val currentDistance = mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthenticationService(this)
        isUserLoggedIn.value = authService.isUserLoggedIn()

        databaseService = DatabaseService(authService)
        calculationService = CalculationService()

        initializeLocationService()

        getLocationData()

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
                            getLocationData()
                        }
                    )
                    UsersList(usersData.value) { user ->
                        Log.d("UI", "Clicked on user ${user.userId}")
                        calculateLocationToUser(user)
                    }
                    if (currentBearing.value != 361f) { // TODO: Think about this
                        DirectionArrow(bearing = currentBearing.value, distance = currentDistance.value)
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

    // this method gets called when location of user is updated in database (every few seconds)
    private fun getLocationData() {
        databaseService.getAllLocations { locations ->
            for (location in locations) {
                Log.d("App", "User: ${location.userId} Location: ${location.latitude}, ${location.longitude}, ${location.timestamp}")
            }
            usersData.value = locations
        }
    }

    fun getUserLocationById(thisUserId: String): UserLocation? {
        return usersData.value.find { it.userId == thisUserId }
    }

    private fun calculateLocationToUser(otherUser: UserLocation){
        var thisUserId = authService.getCurrentUserId()
        if (thisUserId == otherUser.userId){
            Log.d("Calc", "You can not compare with yourself")
        } else{
            val thisUser = getUserLocationById(thisUserId)
            if (thisUser?.latitude != null && thisUser.longitude != null && otherUser.latitude != null && otherUser.longitude != null){
                currentDistance.value = calculationService.getDistanceInMeters(thisUser.latitude, thisUser.longitude, otherUser.latitude, otherUser.longitude)
                currentBearing.value = calculationService.calculateBearing(thisUser.latitude, thisUser.longitude, otherUser.latitude, otherUser.longitude)
            } else {
                Log.d("Calc", "No current user was found")
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$name you need another beer!", style = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        )
        Button(onClick = onSignOut) {
            Text("Sign Out")
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
    Column(modifier = Modifier.padding(32.dp)) {
        for (user in users) {
            Button(
                onClick = { onUserClick(user) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(text = "Location for user ${user.userId}") // ${user.userMail}
            }
        }
    }
}

@Composable
fun DirectionArrow(bearing: Float, distance: Float) {
    Column(modifier = Modifier.padding(175.dp)) {
        Image(
            painter = painterResource(id = R.drawable.arrow),
            contentDescription = "Direction Arrow",
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    rotationZ = bearing
                }
        )
        Spacer(modifier = Modifier.height(16.dp).width(128.dp))
        Text(text = "$distance meters away")
    }
}