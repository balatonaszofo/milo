package com.example.magneticzones.classification

import com.example.magneticzones.model.FeatureWindow
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone
import com.example.magneticzones.processing.FeatureNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedKnnClassifierTest {
    private val zones = listOf(Zone("a", "Changing", 0), Zone("b", "Feeding", 0))

    @Test fun selectsNearestZoneAndReturnsAllProbabilities() {
        val classifier = trainedClassifier()
        val result = classifier.classify(window(0.1, 99), 2.4)
        assertEquals("a", result.predictedZoneId)
        assertEquals(3, result.probabilities.size)
        assertTrue(result.probabilities.first { it.zoneId == "a" }.probability > 0.8)
    }

    @Test fun distantWindowBecomesUnknown() {
        val result = trainedClassifier().classify(window(100.0, 99), 1.0)
        assertEquals(null, result.predictedZoneId)
        assertTrue(result.probabilities.first { it.zoneId == null }.probability > 0.9)
    }

    private fun trainedClassifier() = NormalizedKnnClassifier(3).also { classifier ->
        classifier.fit(zones, listOf(
            TrainingSession("ta", "a", 0, 1, emptyList(), listOf(window(0.0, 1), window(0.2, 2))),
            TrainingSession("tb", "b", 0, 1, emptyList(), listOf(window(10.0, 3), window(10.2, 4))),
        ))
    }

    private fun window(value: Double, timestamp: Long) = FeatureWindow(
        timestamp - 10, timestamp, 20, FeatureNames.all.associateWith { value }
    )
}
