package com.example.WellBeingMotivationApp

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.WellBeingMotivationApp.DbViewModel.DailyRecordViewModel
import com.example.WellBeingMotivationApp.DbViewModel.Repository
import com.example.WellBeingMotivationApp.MapViewModel.MapViewModel
import com.example.WellBeingMotivationApp.MidnightTaskViewModel.MidnightTaskViewModel
import com.example.WellBeingMotivationApp.roomDb.DailyRecord
import com.example.WellBeingMotivationApp.roomDb.DailyRecordDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.time.LocalDate


class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fineLocationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var backgroundLocationPermissionLauncher: ActivityResultLauncher<String>

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            DailyRecordDatabase::class.java,
            name = "daily_record.db"
        ).build()
    }

    private val DbViewModel by viewModels<DailyRecordViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DailyRecordViewModel(Repository(db), applicationContext) as T
                }
            }
        }
    )

    private val MidnightTaskViewModel by viewModels<MidnightTaskViewModel> (
        factoryProducer = {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MidnightTaskViewModel(applicationContext) as T
                }
            }
        }
    )

    private val mapViewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        activityRecognitionPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ){ granted ->
            if(!granted){
                Log.e("Activity Recognition Perm Denied", "Activity Recognition permission was denied by the user.")
            }
            checkForPermissions(activityRecognitionPermissionLauncher, cameraPermissionLauncher, fineLocationPermissionLauncher, backgroundLocationPermissionLauncher, 1)
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ){ granted ->
            if(!granted){
                Log.e("Camera Perm Denied", "Camera permission was denied by the user.")
            }
            checkForPermissions(activityRecognitionPermissionLauncher, cameraPermissionLauncher, fineLocationPermissionLauncher, backgroundLocationPermissionLauncher, 2)
        }

        fineLocationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if(granted){
                mapViewModel.fetchUserLocation(this, fusedLocationClient)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED)) {
                    backgroundLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                else {
                    mapViewModel.createGeofences(this)
                }
            }
        }

        backgroundLocationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if(granted){
                mapViewModel.createGeofences(this)
            }
        }

        checkForPermissions(activityRecognitionPermissionLauncher, cameraPermissionLauncher, fineLocationPermissionLauncher, backgroundLocationPermissionLauncher, 0)

        //val context = applicationContext
        //val db = Room.databaseBuilder(context, DailyRecordDatabase::class.java, "daily_record.db").build()
        //context.deleteDatabase("daily_record.db")
        super.onCreate(savedInstanceState)
        val todayDate = LocalDate.now().toString()
        DbViewModel.getRecordForToday(todayDate).observe(this) { records ->
            if (records.isEmpty()) {
                val newRecord = DailyRecord(
                    date = todayDate,
                    picturesTaken = 0,
                    picturesTakenSmiling = 0,
                    stepsTaken = 0,
                    timeSpentRunning = 0,
                    timeSpentWalking = 0,
                    visitedRecCenter = 0,
                    visitedCampusCenter = 0,
                    visitedMorgan = 0,
                    moodPoints = 0,
                )
                DbViewModel.insertRecord(newRecord)
            }
        }

        DbViewModel.getTop5Records().observe(this) { records ->
            val record2 = if (records.size >= 2) records[1] else null
            record2?.let {
                val smileProportionalityPoints = if (it.picturesTaken >= 5) Math.min(
                    80,
                    ((it.picturesTakenSmiling.toFloat() / it.picturesTaken) * 80).toInt()
                ) else 0
                val stepsTakenPoints = Math.min(60, ((it.stepsTaken.toFloat() / 7500) * 60).toInt())
                val visitedRecCenterPoints = Math.min(30, it.visitedRecCenter * 30)
                val visitedCampusCenterPoints = Math.min(30, it.visitedCampusCenter * 30)
                val visitedMorganPoints = Math.min(30, it.visitedMorgan * 30)
                val timeSpentRunning =
                    Math.min(60, ((it.timeSpentRunning.toFloat() / 1800000) * 60).toInt())
                val timeSpentWalking =
                    Math.min(60, ((it.timeSpentWalking.toFloat() / 1800000) * 60).toInt())
                val updatedRecord = it.copy(
                    moodPoints =
                        smileProportionalityPoints + stepsTakenPoints + visitedRecCenterPoints + visitedCampusCenterPoints + visitedMorganPoints + timeSpentRunning + timeSpentWalking
                )
                DbViewModel.updateRecord(updatedRecord)
            }
        }

        DbViewModel.updateMidnightFlag(false)
        MidnightTaskViewModel.setMidnightTask()
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "face_detection_screen") {
                composable("face_detection_screen") {
                    FaceDetectionScreen(
                        mapViewModel,
                        navController,
                        DbViewModel,
                        applicationContext
                    )
                }

                composable("location_tracking_screen") {
                    LocationTrackingScreen(
                        mapViewModel,
                        navController,
                        DbViewModel,
                        applicationContext
                    )
                }

                composable("activity_detection_screen") {
                    ActivityDetectionScreen(
                        mapViewModel,
                        navController,
                        DbViewModel,
                        applicationContext
                    )
                }

                composable("data_display_screen") {
                    DataDisplayScreen(
                        mapViewModel,
                        navController,
                        DbViewModel,
                        applicationContext
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MidnightTaskViewModel.cancelMidnightTask()
    }

    private fun checkForPermissions(activityRecognitionPermissionLauncher: ActivityResultLauncher<String>, cameraPermissionLauncher: ActivityResultLauncher<String>, fineLocationPermissionLauncher: ActivityResultLauncher<String>, backgroundLocationPermissionLauncher: ActivityResultLauncher<String>, startIndex: Int) {

        for (i in startIndex until 3){
            if(i == 0 && (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED)) {
                activityRecognitionPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                break
            }

            if(i == 1 && (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED)) {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                break
            }

            if (i > 1) {
                var fineLocationPermission = (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED)

                if (!fineLocationPermission){
                    fineLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
                else {
                    mapViewModel.fetchUserLocation(this, fusedLocationClient)

                    var backgroundLocationPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) true
                    else (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                            )

                    if(!backgroundLocationPermission) {
                        backgroundLocationPermissionLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    else {
                        mapViewModel.createGeofences(this)
                    }
                }
            }
        }
    }
}