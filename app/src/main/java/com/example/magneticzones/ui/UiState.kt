package com.example.magneticzones.ui

import com.example.magneticzones.model.ExperimentMetrics
import com.example.magneticzones.model.RawSensorSample
import com.example.magneticzones.model.SmoothedDetection
import com.example.magneticzones.model.Zone

data class LiveSensorUiState(
    val latest: RawSensorSample? = null,
    val magneticHistory: List<RawSensorSample> = emptyList(),
)

data class RecognitionUiState(
    val detection: SmoothedDetection? = null,
    val probabilities: List<Pair<String, Double>> = listOf("Unknown" to 1.0),
    val trainingWindowCount: Int = 0,
)

sealed interface TrainingUiState {
    data object Idle : TrainingUiState
    data class Countdown(val zone: Zone, val seconds: Int) : TrainingUiState
    data class Recording(val zone: Zone, val remainingSeconds: Int, val sampleCount: Int) : TrainingUiState
    data class Complete(val zone: Zone, val sampleCount: Int, val windowCount: Int) : TrainingUiState
}

sealed interface ExperimentUiState {
    data object Idle : ExperimentUiState
    data class Countdown(val zone: Zone, val seconds: Int) : ExperimentUiState
    data class Recording(val zone: Zone, val remainingSeconds: Int, val predictionCount: Int) : ExperimentUiState
    data class Complete(val zone: Zone, val metrics: ExperimentMetrics) : ExperimentUiState
}
