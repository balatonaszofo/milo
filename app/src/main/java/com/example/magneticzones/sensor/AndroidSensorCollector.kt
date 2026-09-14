package com.example.magneticzones.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.magneticzones.model.Orientation
import com.example.magneticzones.model.RawSensorSample
import com.example.magneticzones.model.SensorAvailability
import com.example.magneticzones.model.SensorStatus
import com.example.magneticzones.model.Vector3
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class AndroidSensorCollector(context: Context) : SensorCollector, SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _samples = MutableSharedFlow<RawSensorSample>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples = _samples.asSharedFlow()
    private val _status = MutableStateFlow(
        SensorStatus(
            SensorAvailability(
                magnetometer != null,
                accelerometer != null,
                gyroscope != null,
                rotationVector != null,
            )
        )
    )
    override val status = _status.asStateFlow()

    private var latestMagnetic: Vector3? = null
    private var latestAcceleration: Vector3? = null
    private var latestGyroscope: Vector3? = null
    private var latestOrientation: Orientation? = null
    private var lastMagneticTimestamp = 0L
    private var frequencyHz = 0f
    private var started = false

    override fun start() {
        if (started) return
        started = true
        listOfNotNull(magnetometer, accelerometer, gyroscope, rotationVector).forEach { sensor ->
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        if (!started) return
        manager.unregisterListener(this)
        started = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> latestAcceleration = event.vector()
            Sensor.TYPE_GYROSCOPE -> latestGyroscope = event.vector()
            Sensor.TYPE_ROTATION_VECTOR -> latestOrientation = orientationFromRotation(event.values)
            Sensor.TYPE_MAGNETIC_FIELD -> {
                latestMagnetic = event.vector()
                if (rotationVector == null) latestOrientation = fallbackOrientation()
                updateFrequency(event.timestamp)
                val acceleration = latestAcceleration
                val gyro = latestGyroscope
                val moving = if (acceleration != null && gyro != null) {
                    abs(acceleration.magnitude - SensorManager.GRAVITY_EARTH) >= 0.35f || gyro.magnitude >= 0.12f
                } else true
                _status.value = _status.value.copy(samplingFrequencyHz = frequencyHz, isMoving = moving)
                _samples.tryEmit(
                    RawSensorSample(
                        timestampNanos = event.timestamp,
                        wallClockMillis = System.currentTimeMillis(),
                        magnetic = latestMagnetic!!,
                        acceleration = acceleration,
                        gyroscope = gyro,
                        orientation = latestOrientation,
                    )
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateFrequency(timestamp: Long) {
        if (lastMagneticTimestamp > 0) {
            val instantaneous = (1e9 / (timestamp - lastMagneticTimestamp).coerceAtLeast(1)).toFloat()
            frequencyHz = if (frequencyHz == 0f) instantaneous else 0.1f * instantaneous + 0.9f * frequencyHz
        }
        lastMagneticTimestamp = timestamp
    }

    private fun orientationFromRotation(values: FloatArray): Orientation {
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, values)
        SensorManager.getOrientation(matrix, angles)
        return Orientation(angles[0].degrees(), angles[1].degrees(), angles[2].degrees())
    }

    private fun fallbackOrientation(): Orientation? {
        val gravity = latestAcceleration ?: return null
        val magnetic = latestMagnetic ?: return null
        val matrix = FloatArray(9)
        val inclination = FloatArray(9)
        if (!SensorManager.getRotationMatrix(
                matrix,
                inclination,
                floatArrayOf(gravity.x, gravity.y, gravity.z),
                floatArrayOf(magnetic.x, magnetic.y, magnetic.z),
            )) return null
        val angles = FloatArray(3)
        SensorManager.getOrientation(matrix, angles)
        return Orientation(angles[0].degrees(), angles[1].degrees(), angles[2].degrees())
    }

    private fun SensorEvent.vector() = Vector3(values[0], values[1], values[2])
    private fun Float.degrees() = Math.toDegrees(toDouble()).toFloat()
}
