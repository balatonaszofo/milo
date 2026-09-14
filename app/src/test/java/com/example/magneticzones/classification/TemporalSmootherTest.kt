package com.example.magneticzones.classification

import com.example.magneticzones.model.ClassificationResult
import com.example.magneticzones.model.DetectionConfig
import com.example.magneticzones.model.ZoneProbability
import org.junit.Assert.assertEquals
import org.junit.Test

class TemporalSmootherTest {
    @Test fun requiresStableDwellBeforeActivation() {
        val smoother = TemporalSmoother(DetectionConfig(activationConfidence = 0.6, dwellMillis = 2_000))
        assertEquals(null, smoother.update(result(1_000_000_000L, "a", 0.9)).activeZoneId)
        assertEquals(null, smoother.update(result(2_500_000_000L, "a", 0.9)).activeZoneId)
        assertEquals("a", smoother.update(result(3_100_000_000L, "a", 0.9)).activeZoneId)
    }

    @Test fun singleBadPredictionDoesNotSwitchActiveZone() {
        val smoother = TemporalSmoother(DetectionConfig(activationConfidence = 0.6, releaseConfidence = 0.3, dwellMillis = 1_000))
        smoother.update(result(1_000_000_000L, "a", 0.95))
        smoother.update(result(2_100_000_000L, "a", 0.95))
        val afterNoise = smoother.update(result(2_600_000_000L, "b", 0.95))
        assertEquals("a", afterNoise.activeZoneId)
    }

    private fun result(time: Long, id: String, confidence: Double) = ClassificationResult(
        time,
        listOf(ZoneProbability(id, id, confidence), ZoneProbability(null, "Unknown", 1 - confidence)),
        id,
        id,
        confidence,
        0.1,
    )
}
