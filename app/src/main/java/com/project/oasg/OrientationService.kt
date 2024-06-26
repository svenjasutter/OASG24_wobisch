package com.project.oasg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.util.Log

class OrientationService : Service(), SensorEventListener {

    interface AzimuthListener {
        fun onAzimuthChanged(azimuth: Float)
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magneticField: Sensor? = null

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)

    private var azimuthListener: AzimuthListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): OrientationService = this@OrientationService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    fun setAzimuthListener(listener: AzimuthListener?) {
        this.azimuthListener = listener
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magneticField == null) {
            Log.e("OrientationService", "Required sensors not available")
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("OrientationService", "Sensors registered")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val azimuthInRadians = orientationAngles[0]
            Log.d("RAD", azimuthInRadians.toString())
            val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat() % 360

//            Log.d("OrientationService", "Azimuth (to North): $azimuthInDegrees degrees")

            azimuthListener?.onAzimuthChanged(azimuthInDegrees)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}
