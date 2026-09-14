package com.example.magneticzones.experiment

import com.example.magneticzones.model.ExperimentMetrics
import com.example.magneticzones.model.ExperimentSession

object ExperimentAnalytics {
    fun metrics(session: ExperimentSession): ExperimentMetrics {
        val predictions = session.predictions
        if (predictions.isEmpty()) return ExperimentMetrics(0.0, 0.0, 0, null, 1.0, mapOf(null to 1.0))
        val grouped = predictions.groupingBy { it.activeZoneId }.eachCount()
        val fractions = grouped.mapValues { it.value.toDouble() / predictions.size }
        val transitions = predictions.zipWithNext().count { (a, b) ->
            a.activeZoneId != b.activeZoneId && b.activeZoneId != null && b.activeZoneId != session.actualZoneId
        }
        val firstCorrect = predictions.firstOrNull { it.activeZoneId == session.actualZoneId }
        val startNanos = session.samples.firstOrNull()?.timestampNanos
        return ExperimentMetrics(
            accuracy = fractions[session.actualZoneId] ?: 0.0,
            averageConfidence = predictions.map { it.confidence }.average(),
            incorrectTransitions = transitions,
            recognitionTimeMillis = if (firstCorrect != null && startNanos != null) {
                (firstCorrect.timestampNanos - startNanos) / 1_000_000
            } else null,
            unknownFraction = fractions[null] ?: 0.0,
            predictionFractions = fractions,
        )
    }

    fun confusionMatrix(sessions: List<ExperimentSession>): Map<String, Map<String?, Double>> =
        sessions.groupBy { it.actualZoneId }.mapValues { (_, tests) ->
            val ids = tests.flatMap { it.predictions }.map { it.activeZoneId }
            if (ids.isEmpty()) mapOf(null to 1.0)
            else ids.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / ids.size }
        }
}
