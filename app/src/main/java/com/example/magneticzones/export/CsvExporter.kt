package com.example.magneticzones.export

import com.example.magneticzones.model.*
import com.example.magneticzones.processing.FeatureNames

object CsvExporter {
    fun rawSamples(
        zones: List<Zone>,
        training: List<TrainingSession>,
        experiments: List<ExperimentSession>,
    ): String {
        val names = zones.associate { it.id to it.name }
        val header = listOf(
            "session_type", "session_id", "timestamp_millis", "location_label",
            "bx_uT", "by_uT", "bz_uT", "magnetic_magnitude_uT",
            "ax_m_s2", "ay_m_s2", "az_m_s2", "gx_rad_s", "gy_rad_s", "gz_rad_s",
            "azimuth_deg", "pitch_deg", "roll_deg", "predicted_location", "confidence", "actual_location"
        )
        return buildString {
            appendLine(header.joinToString(","))
            training.forEach { session ->
                session.samples.forEach { sample ->
                    appendLine(sampleRow("training", session.id, names[session.zoneId].orEmpty(), sample, "", "", ""))
                }
            }
            experiments.forEach { session ->
                session.samples.forEach { sample ->
                    val prediction = session.predictions.lastOrNull { it.timestampNanos <= sample.timestampNanos }
                    appendLine(sampleRow(
                        "test", session.id, names[session.actualZoneId].orEmpty(), sample,
                        prediction?.activeZoneId?.let { names[it] } ?: "Unknown",
                        prediction?.confidence?.toString().orEmpty(),
                        names[session.actualZoneId].orEmpty(),
                    ))
                }
            }
        }
    }

    fun featureWindows(
        zones: List<Zone>,
        training: List<TrainingSession>,
        experiments: List<ExperimentSession>,
    ): String {
        val names = zones.associate { it.id to it.name }
        val header = listOf("session_type", "session_id", "start_nanos", "end_nanos", "sample_count",
            "location_label", "predicted_location", "confidence") + FeatureNames.all
        return buildString {
            appendLine(header.joinToString(","))
            training.forEach { session -> session.windows.forEach { window ->
                appendLine(windowRow("training", session.id, names[session.zoneId].orEmpty(), "", "", window))
            } }
            experiments.forEach { session -> session.windows.forEach { window ->
                val prediction = session.predictions.lastOrNull { it.timestampNanos <= window.endNanos }
                appendLine(windowRow(
                    "test", session.id, names[session.actualZoneId].orEmpty(),
                    prediction?.activeZoneId?.let { names[it] } ?: "Unknown",
                    prediction?.confidence?.toString().orEmpty(), window,
                ))
            } }
        }
    }

    private fun sampleRow(
        type: String, id: String, label: String, sample: RawSensorSample,
        predicted: String, confidence: String, actual: String,
    ): String = listOf(
        type, id, sample.wallClockMillis, label,
        sample.magnetic.x, sample.magnetic.y, sample.magnetic.z, sample.magneticMagnitude,
        sample.acceleration?.x, sample.acceleration?.y, sample.acceleration?.z,
        sample.gyroscope?.x, sample.gyroscope?.y, sample.gyroscope?.z,
        sample.orientation?.azimuthDegrees, sample.orientation?.pitchDegrees, sample.orientation?.rollDegrees,
        predicted, confidence, actual,
    ).joinToString(",") { csv(it) }

    private fun windowRow(
        type: String, id: String, label: String, predicted: String, confidence: String, window: FeatureWindow,
    ): String = (listOf(
        type, id, window.startNanos, window.endNanos, window.sampleCount, label, predicted, confidence,
    ) + FeatureNames.all.map { window[it] }).joinToString(",") { csv(it) }

    private fun csv(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == ',' || it == '"' || it == '\n' }) "\"${text.replace("\"", "\"\"")}\"" else text
    }
}
