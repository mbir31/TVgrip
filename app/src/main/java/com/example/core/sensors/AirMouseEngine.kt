package com.example.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.model.AirMouseConfig
import com.example.core.model.TvCommand
import com.example.core.network.TvConnectionManager
import kotlin.math.abs
import kotlin.math.sign

class AirMouseEngine(
    private val context: Context,
    private val connectionManager: TvConnectionManager
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var config: AirMouseConfig = AirMouseConfig()
    var isEnabled: Boolean = false
        private set

    // Calibration offsets
    private var biasX = 0f
    private var biasY = 0f

    // Smoothed deltas
    private var smoothedDeltaX = 0f
    private var smoothedDeltaY = 0f

    fun start() {
        if (isEnabled) return
        isEnabled = true
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        if (!isEnabled) return
        isEnabled = false
        sensorManager.unregisterListener(this)
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
    }

    fun calibrateNeutral() {
        // Current bias established as center
        biasX = smoothedDeltaX
        biasY = smoothedDeltaY
    }

    fun resetCalibration() {
        biasX = 0f
        biasY = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // Raw angular velocity in rad/s: X is pitch (vertical tilt), Y is roll (horizontal tilt), Z is yaw
        // In portrait holding phone:
        // Yaw (Z) or Roll (Y) -> horizontal pointer move
        // Pitch (X) -> vertical pointer move
        val rawX = -event.values[2] // yaw for horizontal
        val rawY = -event.values[0] // pitch for vertical

        val adjustedX = rawX - biasX
        val adjustedY = rawY - biasY

        // Dead-zone filter
        val deadX = if (abs(adjustedX) < config.deadZone) 0f else adjustedX - (sign(adjustedX) * config.deadZone)
        val deadY = if (abs(adjustedY) < config.deadZone) 0f else adjustedY - (sign(adjustedY) * config.deadZone)

        // Acceleration and sensitivity
        val speedX = deadX * config.sensitivity * (1f + (abs(deadX) * config.acceleration))
        val speedY = deadY * config.sensitivity * (1f + (abs(deadY) * config.acceleration))

        // Exponential smoothing filter
        val alpha = (1.0f - config.smoothing).coerceIn(0.05f, 1.0f)
        smoothedDeltaX += (speedX - smoothedDeltaX) * alpha
        smoothedDeltaY += (speedY - smoothedDeltaY) * alpha

        val finalDx = if (config.invertX) -smoothedDeltaX else smoothedDeltaX
        val finalDy = if (config.invertY) -smoothedDeltaY else smoothedDeltaY

        if (abs(finalDx) > 0.005f || abs(finalDy) > 0.005f) {
            connectionManager.sendCommand(TvCommand.PointerMove(finalDx * 15f, finalDy * 15f))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
