package com.example.magneticzones.model

import kotlin.math.sqrt

data class Vector3(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

data class Orientation(val azimuthDegrees: Float, val pitchDegrees: Float, val rollDegrees: Float)

data class RawSensorSample(
    val timestampNanos: Long,
    val wallClockMillis: Long,
    val magnetic: Vector3,
    val acceleration: Vector3?,
    val gyroscope: Vector3?,
    val orientation: Orientation?,
) {
    val magneticMagnitude: Float get() = magnetic.magnitude
}

data class SensorAvailability(
    val magnetometer: Boolean = false,
    val accelerometer: Boolean = false,
    val gyroscope: Boolean = false,
    val rotationVector: Boolean = false,
)

data class SensorStatus(
    val availability: SensorAvailability = SensorAvailability(),
    val samplingFrequencyHz: Float = 0f,
    val isMoving: Boolean = false,
)

data class FeatureWindow(
    val startNanos: Long,
    val endNanos: Long,
    val sampleCount: Int,
    val values: Map<String, Double>,
) {
    operator fun get(name: String): Double = values[name] ?: 0.0
}

data class Zone(val id: String, val name: String, val createdAtMillis: Long)

enum class RuleTrigger {
    AT_LOCATION,
    LEAVING_HOME,
    CUSTOM,
}

data class AutomationRule(
    val id: String,
    val name: String,
    val trigger: RuleTrigger,
    val locationName: String?,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val prompt: String = "",
    val promptReferences: List<PromptReference> = emptyList(),
) {
    val description: String get() = prompt.ifBlank {
        when (trigger) {
            RuleTrigger.AT_LOCATION -> "When I'm at ${locationName ?: "a location"}, remind me if the stove is still on."
            RuleTrigger.LEAVING_HOME -> "When I'm heading out, remind me if the stove is still on."
            RuleTrigger.CUSTOM -> name
        }
    }

    fun withPrompt(title: String, text: String, references: List<PromptReference> = if (text == description) promptReferences else emptyList()): AutomationRule {
        val changed = text.trim() != description
        val leading = text.length - text.trimStart().length
        return copy(
            name = title.trim().ifBlank { "My rule" }, prompt = text.trim(),
            promptReferences = references.filter { it.matches(text) }.map { it.copy(start = it.start - leading) }.filter { it.matches(text.trim()) },
            trigger = if (changed) RuleTrigger.CUSTOM else trigger,
            locationName = if (changed) null else locationName,
        )
    }

    companion object {
        fun defaultStoveReminder() = AutomationRule(
            id = "default-stove-reminder",
            name = "Stove reminder",
            trigger = RuleTrigger.AT_LOCATION,
            locationName = "Couch",
            enabled = true,
            createdAtMillis = 0L,
        )
    }
}

data class TrainingSession(
    val id: String,
    val zoneId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val samples: List<RawSensorSample>,
    val windows: List<FeatureWindow>,
)

data class ZoneProbability(val zoneId: String?, val name: String, val probability: Double)

data class ClassificationResult(
    val timestampNanos: Long,
    val probabilities: List<ZoneProbability>,
    val predictedZoneId: String?,
    val predictedName: String,
    val confidence: Double,
    val nearestDistance: Double,
) {
    companion object {
        fun unknown(timestampNanos: Long) = ClassificationResult(
            timestampNanos,
            listOf(ZoneProbability(null, "Unknown", 1.0)),
            null,
            "Unknown",
            1.0,
            Double.POSITIVE_INFINITY,
        )
    }
}

data class SmoothedDetection(
    val timestampNanos: Long,
    val rawZoneId: String?,
    val rawName: String,
    val smoothedZoneId: String?,
    val smoothedName: String,
    val activeZoneId: String?,
    val activeName: String,
    val confidence: Double,
)

data class PredictionRecord(
    val timestampNanos: Long,
    val rawZoneId: String?,
    val smoothedZoneId: String?,
    val activeZoneId: String?,
    val confidence: Double,
)

data class ExperimentSession(
    val id: String,
    val actualZoneId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val samples: List<RawSensorSample>,
    val windows: List<FeatureWindow>,
    val predictions: List<PredictionRecord>,
)

data class ExperimentMetrics(
    val accuracy: Double,
    val averageConfidence: Double,
    val incorrectTransitions: Int,
    val recognitionTimeMillis: Long?,
    val unknownFraction: Double,
    val predictionFractions: Map<String?, Double>,
)

data class DetectionConfig(
    val activationConfidence: Double = 0.65,
    val releaseConfidence: Double = 0.42,
    val dwellMillis: Long = 2_500,
    val unknownDistanceScale: Double = 2.4,
)
