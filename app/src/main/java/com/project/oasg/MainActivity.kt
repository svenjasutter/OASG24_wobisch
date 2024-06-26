package com.project.oasg

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.media3.common.util.NotificationUtil.createNotificationChannel
import com.google.firebase.database.ValueEventListener


class MainActivity : ComponentActivity(), OrientationService.AzimuthListener {
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
    private var OwnlocationListener: ValueEventListener? = null

    private var orientationService: OrientationService? = null
    private var isBound = false
    private var azimuth = mutableStateOf(0f)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as OrientationService.LocalBinder
            orientationService = binder.getService()
            orientationService?.setAzimuthListener(this@MainActivity)
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            orientationService = null
            isBound = false
        }
    }

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
                            myLocation = getUserLocationById(authService.getCurrentUserId()),
                            azimuth = azimuth.value
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
        Log.d("LOG", thisUserId)
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

        authService.getCurrentUserId().let { userId ->
            OwnlocationListener = databaseService.trackUserLocation(userId) { location ->
                Log.d("Tracking OWN Location", "User ID: $userId, Location: $location")
                val currentUserLocation = getUserLocationById(authService.getCurrentUserId())

                currentUserLocation?.let {
                    updateDistanceAndBearing(it, location)
                } ?: Log.d("Calc", "No current user location found")
            }
        }
    }

    private fun updateDistanceAndBearing(currentUserLocation: UserLocation, otherUserLocation: UserLocation) {
        Log.d("Update Callback", "Callback")
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
        OwnlocationListener?.let { listener ->
            val userRef = databaseService.getReferenceForUser(authService.getCurrentUserId())
            userRef.removeEventListener(listener)
            OwnlocationListener = null
        }
    }

    override fun onAzimuthChanged(azimuth: Float) {
        Log.d("MainActivity", "Received azimuth: ${this.azimuth}");
        this.azimuth.value = azimuth
    }

    override fun onStart() {
        super.onStart()
        Intent(this, OrientationService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            orientationService?.setAzimuthListener(null)
            unbindService(connection)
            isBound = false
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
    myLocation: UserLocation?,
    azimuth: Float
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StartBlock(name = name, onSignOut = onSignOut)
        UsersList(users = users, onUserClick = onUserClick, currentUser = currentUser)
        if (currentBearing != 361f) {
            DirectionArrow(bearing = currentBearing, distance = currentDistance)
        }
        OwnLocation(location = myLocation)
//        NorthArrow(azimuth = azimuth)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(text = "$name ", style = TextStyle(
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
fun OwnLocation(location: UserLocation?){
    Column {
        if (location != null) {
            Text("Latitude: ${location.latitude}", fontSize = 16.sp)
        }
        if (location != null) {
            Text("Longitude: ${location.longitude}", fontSize = 16.sp)
        }
    }
}

@Composable
fun DirectionArrow(bearing: Float, distance: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.arrow),
            contentDescription = "Direction Arrow",
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = bearing
                }
        )
        Spacer(modifier = Modifier.height(8.dp))
        val formattedDistance = String.format("%.2f meters away", distance)
        Text(
            text = formattedDistance,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
fun DirectionScreen(bearingState: State<Float>, distanceState: State<Float>, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = { onBack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Text("Back")
        }
        if (bearingState.value != 361f) {
            DirectionArrow(bearing = bearingState.value, distance = distanceState.value)
        } else {
            Text("No direction data available")
        }
    }
}

@Composable
fun NorthArrow(azimuth: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
        Image(
            painter = painterResource(id = R.drawable.arrow),
            contentDescription = "North Arrow",
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = azimuth
                }
        )
    }
}