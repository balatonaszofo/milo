package com.example.magneticzones.experiment

import com.example.magneticzones.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentAnalyticsTest {
    @Test fun computesIndependentValidationMetrics() {
        val sample = RawSensorSample(1_000_000_000L, 0, Vector3(0f, 0f, 0f), null, null, null)
        val session = ExperimentSession(
            "test", "a", 0, 10, listOf(sample), emptyList(),
            listOf(
                PredictionRecord(1_000_000_000L, null, null, null, 0.4),
                PredictionRecord(2_000_000_000L, "a", "a", "a", 0.8),
                PredictionRecord(3_000_000_000L, "b", "b", "b", 0.7),
                PredictionRecord(4_000_000_000L, "a", "a", "a", 0.9),
            ),
        )
        val metrics = ExperimentAnalytics.metrics(session)
        assertEquals(0.5, metrics.accuracy, 0.0001)
        assertEquals(0.25, metrics.unknownFraction, 0.0001)
        assertEquals(1, metrics.incorrectTransitions)
        assertEquals(1_000L, metrics.recognitionTimeMillis)
    }
}
