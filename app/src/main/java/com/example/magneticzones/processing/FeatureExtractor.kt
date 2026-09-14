package com.example.magneticzones.processing

import com.example.magneticzones.model.FeatureWindow
import com.example.magneticzones.model.RawSensorSample
import kotlin.math.abs
import kotlin.math.sqrt

object FeatureNames {
    val magneticAxes = listOf(
        "mag_x_mean", "mag_x_std", "mag_x_min", "mag_x_max",
        "mag_y_mean", "mag_y_std", "mag_y_min", "mag_y_max",
        "mag_z_mean", "mag_z_std", "mag_z_min", "mag_z_max",
    )
    val magneticMagnitude = listOf(
        "mag_mean", "mag_std", "mag_min", "mag_max", "mag_range",
        "mag_p10", "mag_p50", "mag_p90", "mag_rate_of_change",
    )
    val motion = listOf(
        "accel_magnitude_mean", "accel_magnitude_variance",
        "gyro_magnitude_mean", "gyro_magnitude_variance", "stationary_fraction",
    )
    val orientation = listOf("pitch_mean", "pitch_std", "roll_mean", "roll_std")
    val all = magneticAxes + magneticMagnitude + motion + orientation
}

class FeatureExtractor {
    fun extract(samples: List<RawSensorSample>): FeatureWindow? {
        if (samples.size < 4) return null
        val ordered = samples.sortedBy { it.timestampNanos }
        val values = linkedMapOf<String, Double>()

        addStats(values, "mag_x", ordered.map { it.magnetic.x.toDouble() })
        addStats(values, "mag_y", ordered.map { it.magnetic.y.toDouble() })
        addStats(values, "mag_z", ordered.map { it.magnetic.z.toDouble() })

        val magnitudes = ordered.map { it.magneticMagnitude.toDouble() }
        val magStats = stats(magnitudes)
        values["mag_mean"] = magStats.mean
        values["mag_std"] = magStats.std
        values["mag_min"] = magStats.min
        values["mag_max"] = magStats.max
        values["mag_range"] = magStats.max - magStats.min
        values["mag_p10"] = percentile(magnitudes, 0.10)
        values["mag_p50"] = percentile(magnitudes, 0.50)
        values["mag_p90"] = percentile(magnitudes, 0.90)
        val seconds = (ordered.last().timestampNanos - ordered.first().timestampNanos) / 1e9
        values["mag_rate_of_change"] = if (seconds > 0) {
            magnitudes.zipWithNext { a, b -> abs(b - a) }.sum() / seconds
        } else 0.0

        val accel = ordered.mapNotNull { it.acceleration?.magnitude?.toDouble() }
        val gyro = ordered.mapNotNull { it.gyroscope?.magnitude?.toDouble() }
        values["accel_magnitude_mean"] = accel.averageOrZero()
        values["accel_magnitude_variance"] = variance(accel)
        values["gyro_magnitude_mean"] = gyro.averageOrZero()
        values["gyro_magnitude_variance"] = variance(gyro)
        values["stationary_fraction"] = ordered.count { sample ->
            val a = sample.acceleration?.magnitude ?: Float.MAX_VALUE
            val g = sample.gyroscope?.magnitude ?: Float.MAX_VALUE
            abs(a - EARTH_GRAVITY) < 0.35f && g < 0.12f
        }.toDouble() / ordered.size

        val orientations = ordered.mapNotNull { it.orientation }
        addMeanStd(values, "pitch", orientations.map { it.pitchDegrees.toDouble() })
        addMeanStd(values, "roll", orientations.map { it.rollDegrees.toDouble() })
        return FeatureWindow(
            ordered.first().timestampNanos,
            ordered.last().timestampNanos,
            ordered.size,
            values,
        )
    }

    fun rollingWindows(
        samples: List<RawSensorSample>,
        windowMillis: Long = 2_000,
        stepMillis: Long = 500,
    ): List<FeatureWindow> {
        if (samples.isEmpty()) return emptyList()
        val ordered = samples.sortedBy { it.timestampNanos }
        val windowNanos = windowMillis * 1_000_000
        val stepNanos = stepMillis * 1_000_000
        val result = mutableListOf<FeatureWindow>()
        var start = ordered.first().timestampNanos
        while (start + windowNanos <= ordered.last().timestampNanos) {
            val end = start + windowNanos
            extract(ordered.filter { it.timestampNanos in start..end })?.let(result::add)
            start += stepNanos
        }
        return result
    }

    private fun addStats(target: MutableMap<String, Double>, prefix: String, data: List<Double>) {
        val s = stats(data)
        target["${prefix}_mean"] = s.mean
        target["${prefix}_std"] = s.std
        target["${prefix}_min"] = s.min
        target["${prefix}_max"] = s.max
    }

    private fun addMeanStd(target: MutableMap<String, Double>, prefix: String, data: List<Double>) {
        val s = stats(data)
        target["${prefix}_mean"] = s.mean
        target["${prefix}_std"] = s.std
    }

    private fun stats(data: List<Double>): Stats {
        if (data.isEmpty()) return Stats(0.0, 0.0, 0.0, 0.0)
        val mean = data.average()
        val std = sqrt(data.sumOf { (it - mean) * (it - mean) } / data.size)
        return Stats(mean, std, data.min(), data.max())
    }

    private fun variance(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val mean = data.average()
        return data.sumOf { (it - mean) * (it - mean) } / data.size
    }

    private fun percentile(data: List<Double>, fraction: Double): Double {
        if (data.isEmpty()) return 0.0
        val sorted = data.sorted()
        val position = fraction.coerceIn(0.0, 1.0) * sorted.lastIndex
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        val weight = position - lower
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }

    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    private data class Stats(val mean: Double, val std: Double, val min: Double, val max: Double)

    companion object { private const val EARTH_GRAVITY = 9.80665f }
}
