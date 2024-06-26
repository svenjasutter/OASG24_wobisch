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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.firebase.database.ValueEventListener


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
                locationService.startOwnLocationUpdates(locationPermissionRequest)
            } else {
                Log.e("LocationUpdate", "Permission denied by user")
            }
        }
    }

    private lateinit var databaseService: DatabaseService
    private val usersData = mutableStateOf<List<UserLocation>>(listOf())

    private lateinit var calculationService: CalculationService
    private val currentBearing = mutableFloatStateOf(361f)
    private val currentDistance = mutableFloatStateOf(0f)

    private val isShowingDirection = mutableStateOf(false)

    private var locationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthenticationService(this)
        isUserLoggedIn.value = authService.isUserLoggedIn()

        databaseService = DatabaseService(authService)
        calculationService = CalculationService()

        initializeLocationService()

        getLocationData()

        setContent {
            LocalContext.current
            OASGTheme {
                if (isUserLoggedIn.value) {
                    if (!isShowingDirection.value) {
                        MainContent(
                            name = authService.getCurrentUserEmail(),
                            users = usersData.value,
                            onUserClick = { user ->
                                // Start updates to arrow
                                // Do this every second until back button is pressed
//                                calculateLocationToUser(user)
                                startTrackingUser(user)
                                isShowingDirection.value = true
                            },
                            onSignOut = {
                                if (::locationService.isInitialized) {
                                    locationService.stopOwnLocationUpdates()
                                }
                                authService.signOut { isUserLoggedIn.value = false }
                            },
                            currentBearing = currentBearing.floatValue,
                            currentDistance = currentDistance.floatValue,
                            currentUser = authService.getCurrentUserId(),
                        )
                    } else {
                        DirectionScreen(
                            bearingState = currentBearing,
                            distanceState = currentDistance,
                            onBack = { isShowingDirection.value = false }
                        )
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
//            Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude}")
            databaseService.updateLocation(location)
        }
        locationService.startOwnLocationUpdates(locationPermissionRequest)
    }

    private fun getLocationData() {
        databaseService.getAllLocations { locations ->
//            for (location in locations) {
//                Log.d("App", "User: ${location.userId} Location: ${location.latitude}, ${location.longitude}, ${location.timestamp}")
//            }
            usersData.value = locations
//            Log.d("all userdata:", usersData.value.toString())
        }
    }

    private fun getUserLocationById(thisUserId: String): UserLocation? {
        return usersData.value.find { it.userId == thisUserId }
    }

    // this function pauses the location update if the app is not in the foreground
    //    override fun onPause() {
    //        super.onPause()
    //        locationService.stopLocationUpdates()
    //    }

    private fun startTrackingUser(user: UserLocation) {
        stopTrackingUser()

        user.userId?.let { userId ->
            locationListener = databaseService.trackUserLocation(userId) { location ->
                Log.d("Tracking Location", "User ID: $userId, Location: $location")
                val currentUserLocation = getUserLocationById(authService.getCurrentUserId())

                currentUserLocation?.let {
                    updateDistanceAndBearing(it, location)
                } ?: Log.d("Calc", "No current user location found")
            }
        }
    }
    private fun updateDistanceAndBearing(currentUserLocation: UserLocation, otherUserLocation: UserLocation) {
        if (currentUserLocation.latitude != null && currentUserLocation.longitude != null &&
            otherUserLocation.latitude != null && otherUserLocation.longitude != null) {

            currentDistance.floatValue = calculationService.getDistanceInMeters(
                currentUserLocation.latitude,
                currentUserLocation.longitude,
                otherUserLocation.latitude,
                otherUserLocation.longitude
            )

            currentBearing.floatValue = calculationService.calculateBearing(
                currentUserLocation.latitude,
                currentUserLocation.longitude,
                otherUserLocation.latitude,
                otherUserLocation.longitude
            )

            Log.d("Location Calc", "Distance: ${currentDistance.floatValue}, Bearing: ${currentBearing.floatValue}")
        } else {
            Log.d("Calc Error", "Invalid location data")
        }
    }

    private fun stopTrackingUser() {
        locationListener?.let { listener ->
            val userRef = databaseService.getReferenceForUser(authService.getCurrentUserId())
            userRef.removeEventListener(listener)
            locationListener = null
        }
    }
}

@Composable
fun MainContent(
    name: String,
    users: List<UserLocation>,
    onUserClick: (UserLocation) -> Unit,
    onSignOut: () -> Unit,
    currentBearing: Float,
    currentDistance: Float,
    currentUser: String,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StartBlock(name = name, onSignOut = onSignOut)
        UsersList(users = users, onUserClick = onUserClick, currentUser = currentUser)
        if (currentBearing != 361f) {
            DirectionArrow(bearing = currentBearing, distance = currentDistance)
        }
    }
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
fun StartBlock(name: String, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
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
        StartBlock("You", onSignOut = {})
    }
}

@Composable
fun UsersList(users: List<UserLocation>, onUserClick: (UserLocation) -> Unit, currentUser: String) {
    Column(modifier = Modifier.padding(32.dp)) {
        for (user in users) {
            if (user.userId != currentUser){
                Button(
                    onClick = { onUserClick(user) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(text = "Location for user ${user.userId}") // ${user.userMail}
                }
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
        Spacer(modifier = Modifier
            .height(16.dp)
            .width(128.dp))
        Text(text = "$distance meters away")
    }
}

@Composable
fun DirectionScreen(bearingState: State<Float>, distanceState: State<Float>, onBack: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Button(onClick = {
            onBack()
        }) {
            Text("Back")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (bearingState.value != 361f) {
            DirectionArrow(bearing = bearingState.value, distance = distanceState.value)
        }
    }
}
