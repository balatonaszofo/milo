package com.example.magneticzones.classification

import com.example.magneticzones.model.ClassificationResult
import com.example.magneticzones.model.FeatureWindow
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone
import com.example.magneticzones.model.ZoneProbability
import com.example.magneticzones.processing.FeatureNames
import kotlin.math.exp
import kotlin.math.sqrt

interface LocationClassifier {
    fun fit(zones: List<Zone>, sessions: List<TrainingSession>)
    fun classify(window: FeatureWindow, unknownDistanceScale: Double): ClassificationResult
}
/** Explainable k-NN over standardized window features. Axis features deliberately receive
 * lower weight than magnitude statistics so normal phone rotation cannot dominate matching. */
class NormalizedKnnClassifier(private val neighborCount: Int = 7) : LocationClassifier {
    private data class LabeledWindow(val zoneId: String, val values: DoubleArray)

    private var zoneNames = emptyMap<String, String>()
    private var training = emptyList<LabeledWindow>()
    private var means = DoubleArray(FeatureNames.all.size)
    private var scales = DoubleArray(FeatureNames.all.size) { 1.0 }
    private val weights = FeatureNames.all.map { name ->
        when {
            name.startsWith("mag_x") || name.startsWith("mag_y") || name.startsWith("mag_z") -> 0.35
            name.startsWith("mag_") -> 1.0
            name.startsWith("accel_") || name.startsWith("gyro_") -> 0.45
            name == "stationary_fraction" -> 0.30
            else -> 0.20
        }
    }.toDoubleArray()

    override fun fit(zones: List<Zone>, sessions: List<TrainingSession>) {
        zoneNames = zones.associate { it.id to it.name }
        val raw = sessions.flatMap { session ->
            session.windows.map { session.zoneId to vector(it) }
        }
        if (raw.isEmpty()) {
            training = emptyList()
            return
        }
        means = DoubleArray(FeatureNames.all.size) { index -> raw.map { it.second[index] }.average() }
        scales = DoubleArray(FeatureNames.all.size) { index ->
            val mean = means[index]
            sqrt(raw.map { (it.second[index] - mean) * (it.second[index] - mean) }.average())
                .coerceAtLeast(MIN_SCALE)
        }
        training = raw.map { (zoneId, values) -> LabeledWindow(zoneId, standardize(values)) }
    }

    override fun classify(window: FeatureWindow, unknownDistanceScale: Double): ClassificationResult {
        if (training.isEmpty()) return ClassificationResult.unknown(window.endNanos)
        val query = standardize(vector(window))
        val neighbors = training
            .map { it to distance(query, it.values) }
            .sortedBy { it.second }
            .take(neighborCount.coerceAtMost(training.size))
        val nearest = neighbors.first().second
        val zoneScores = neighbors.groupBy { it.first.zoneId }.mapValues { (_, rows) ->
            rows.sumOf { (_, distance) -> exp(-distance * distance) }
        }
        val scoreTotal = zoneScores.values.sum().coerceAtLeast(1e-12)
        val knownMass = exp(-nearest / unknownDistanceScale.coerceAtLeast(0.05)).coerceIn(0.0, 1.0)
        val probabilities = zoneNames.map { (id, name) ->
            ZoneProbability(id, name, (zoneScores[id] ?: 0.0) / scoreTotal * knownMass)
        }.plus(ZoneProbability(null, "Unknown", 1.0 - knownMass)).sortedByDescending { it.probability }
        val best = probabilities.first()
        return ClassificationResult(
            window.endNanos,
            probabilities,
            best.zoneId,
            best.name,
            best.probability,
            nearest,
        )
    }

    private fun vector(window: FeatureWindow) = FeatureNames.all.map { window[it] }.toDoubleArray()

    private fun standardize(values: DoubleArray) = DoubleArray(values.size) { index ->
        (values[index] - means[index]) / scales[index]
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Double {
        var weightedSquared = 0.0
        var weightTotal = 0.0
        for (index in a.indices) {
            val delta = a[index] - b[index]
            weightedSquared += weights[index] * delta * delta
            weightTotal += weights[index]
        }
        return sqrt(weightedSquared / weightTotal.coerceAtLeast(1e-12))
    }

    private companion object { const val MIN_SCALE = 0.05 }
}
