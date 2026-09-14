package com.example.magneticzones.processing

import com.example.magneticzones.model.RawSensorSample
import com.example.magneticzones.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureExtractorTest {
    @Test fun extractsMagnitudeAndAxisStatistics() {
        val samples = (0 until 10).map { index ->
            RawSensorSample(
                timestampNanos = index * 100_000_000L,
                wallClockMillis = index.toLong(),
                magnetic = Vector3(3f, 4f, index.toFloat()),
                acceleration = Vector3(0f, 0f, 9.80665f),
                gyroscope = Vector3(0f, 0f, 0f),
                orientation = null,
            )
        }
        val result = requireNotNull(FeatureExtractor().extract(samples))
        assertEquals(3.0, result["mag_x_mean"], 0.0001)
        assertEquals(4.5, result["mag_z_mean"], 0.0001)
        assertEquals(1.0, result["stationary_fraction"], 0.0001)
        assertEquals(5.0, result["mag_min"], 0.0001)
        assertEquals(kotlin.math.sqrt(106.0), result["mag_max"], 0.0001)
    }

    @Test fun rollingWindowsAreSeparateAndOverlapping() {
        val samples = (0..50).map { index ->
            RawSensorSample(index * 100_000_000L, index.toLong(), Vector3(1f, 2f, 3f), null, null, null)
        }
        val windows = FeatureExtractor().rollingWindows(samples, windowMillis = 2_000, stepMillis = 500)
        assertEquals(7, windows.size)
        assertEquals(2_000_000_000L, windows.first().endNanos - windows.first().startNanos)
    }
}
