package com.example.magneticzones.classification

import com.example.magneticzones.model.ClassificationResult
import com.example.magneticzones.model.DetectionConfig
import com.example.magneticzones.model.SmoothedDetection

class TemporalSmoother(private var config: DetectionConfig = DetectionConfig()) {
    private val smoothed = mutableMapOf<String?, Double>()
    private var candidateId: String? = null
    private var candidateName = "Unknown"
    private var candidateSinceNanos = 0L
    private var activeId: String? = null
    private var activeName = "Unknown"

    fun configure(value: DetectionConfig) { config = value }

    fun reset() {
        smoothed.clear()
        candidateId = null
        candidateName = "Unknown"
        candidateSinceNanos = 0L
        activeId = null
        activeName = "Unknown"
    }

    fun update(raw: ClassificationResult): SmoothedDetection {
        val keys = smoothed.keys + raw.probabilities.map { it.zoneId }
        keys.forEach { id ->
            val incoming = raw.probabilities.firstOrNull { it.zoneId == id }?.probability ?: 0.0
            smoothed[id] = EMA_ALPHA * incoming + (1 - EMA_ALPHA) * (smoothed[id] ?: incoming)
        }
        val strongest = smoothed.maxByOrNull { it.value }
        val strongestId = strongest?.key
        val strongestConfidence = strongest?.value ?: 1.0
        val strongestName = raw.probabilities.firstOrNull { it.zoneId == strongestId }?.name ?: "Unknown"

        if (strongestId != candidateId) {
            candidateId = strongestId
            candidateName = strongestName
            candidateSinceNanos = raw.timestampNanos
        }
        val stableForMillis = (raw.timestampNanos - candidateSinceNanos) / 1_000_000
        val mayActivate = strongestConfidence >= config.activationConfidence
        val activeIsWeak = (smoothed[activeId] ?: 0.0) < config.releaseConfidence
        if (stableForMillis >= config.dwellMillis && mayActivate && (strongestId == activeId || activeIsWeak)) {
            activeId = strongestId
            activeName = candidateName
        }

        return SmoothedDetection(
            raw.timestampNanos,
            raw.predictedZoneId,
            raw.predictedName,
            strongestId,
            strongestName,
            activeId,
            activeName,
            strongestConfidence,
        )
    }

    private companion object { const val EMA_ALPHA = 0.35 }
}
