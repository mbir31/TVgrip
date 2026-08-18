package com.example.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.model.MotionSteeringConfig
import kotlin.math.abs
import kotlin.math.sin

class MotionSteeringEngine(
    private val context: Context,
    var onSteeringUpdate: ((angle: Float, accel: Float, pitch: Float) -> Unit)? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var isEnabled: Boolean = false
        private set

    var config: MotionSteeringConfig = MotionSteeringConfig()

    private var centerOffset: Float = 0f

    fun start() {
        if (isEnabled) return
        isEnabled = true
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!isEnabled) return
        isEnabled = false
        sensorManager.unregisterListener(this)
        onSteeringUpdate?.invoke(0f, 0f, 0f)
    }

    fun calibrateCenter() {
        centerOffset = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // In landscape mode: Roll (orientation[2]) determines steering wheel rotation
            val roll = orientation[2] - centerOffset // Radians
            val rawSteer = (sin(roll) * config.sensitivity).coerceIn(-1.0f, 1.0f)
            val filteredSteer = if (abs(rawSteer) < config.deadZone) 0f else rawSteer

            val pitch = orientation[1].coerceIn(-1.0f, 1.0f)
            onSteeringUpdate?.invoke(filteredSteer, 0f, pitch)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Fallback for accelerometer: Y-axis tilt in landscape
            val rawSteer = (-event.values[1] / 9.8f * config.sensitivity).coerceIn(-1.0f, 1.0f)
            val filteredSteer = if (abs(rawSteer) < config.deadZone) 0f else rawSteer
            onSteeringUpdate?.invoke(filteredSteer, 0f, 0f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
