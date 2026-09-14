package com.example.magneticzones.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.magneticzones.classification.LocationClassifier
import com.example.magneticzones.classification.TemporalSmoother
import com.example.magneticzones.data.ZoneRepository
import com.example.magneticzones.experiment.ExperimentAnalytics
import com.example.magneticzones.export.CsvExporter
import com.example.magneticzones.model.*
import com.example.magneticzones.processing.FeatureExtractor
import com.example.magneticzones.sensor.SensorCollector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(
    private val collector: SensorCollector,
    private val repository: ZoneRepository,
    private val extractor: FeatureExtractor,
    private val classifier: LocationClassifier,
) : ViewModel() {
    val zones = repository.zones
    val trainingSessions = repository.trainingSessions
    val experimentSessions = repository.experimentSessions
    val automationRules = repository.automationRules
    val sensorStatus = collector.status

    private val _liveSensors = MutableStateFlow(LiveSensorUiState())
    val liveSensors = _liveSensors.asStateFlow()
    private val _recognition = MutableStateFlow(RecognitionUiState())
    val recognition = _recognition.asStateFlow()
    private val _trainingState = MutableStateFlow<TrainingUiState>(TrainingUiState.Idle)
    val trainingState = _trainingState.asStateFlow()
    private val _experimentState = MutableStateFlow<ExperimentUiState>(ExperimentUiState.Idle)
    val experimentState = _experimentState.asStateFlow()
    private val _config = MutableStateFlow(DetectionConfig())
    val config = _config.asStateFlow()

    private val recentSamples = ArrayDeque<RawSensorSample>()
    private val smoother = TemporalSmoother(_config.value)
    private var experimentSmoother = TemporalSmoother(_config.value)
    private var lastUiTimestamp = 0L
    private var lastClassificationTimestamp = 0L
    private var trainingSamples: MutableList<RawSensorSample>? = null
    private var experimentSamples: MutableList<RawSensorSample>? = null
    private var experimentWindows: MutableList<FeatureWindow>? = null
    private var experimentPredictions: MutableList<PredictionRecord>? = null
    private var trainingJob: Job? = null
    private var experimentJob: Job? = null

    init {
        viewModelScope.launch { collector.samples.collect(::onSample) }
        viewModelScope.launch {
            combine(zones, trainingSessions) { currentZones, sessions -> currentZones to sessions }
                .collect { (currentZones, sessions) ->
                    classifier.fit(currentZones, sessions)
                    _recognition.update { it.copy(trainingWindowCount = sessions.sumOf { s -> s.windows.size }) }
                }
        }
    }

    fun startSensors() = collector.start()
    fun stopSensors() = collector.stop()

    fun addZone(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addZone(name) }
    }

    fun deleteZone(zoneId: String) {
        if (trainingJob?.isActive == true || experimentJob?.isActive == true) return
        viewModelScope.launch { repository.deleteZone(zoneId) }
    }

    fun startTraining(zoneId: String, durationSeconds: Int = 30) {
        if (trainingJob?.isActive == true || experimentJob?.isActive == true) return
        val zone = zones.value.firstOrNull { it.id == zoneId } ?: return
        trainingJob = viewModelScope.launch {
            countdown { seconds -> _trainingState.value = TrainingUiState.Countdown(zone, seconds) }
            val started = System.currentTimeMillis()
            val captured = mutableListOf<RawSensorSample>()
            trainingSamples = captured
            for (remaining in durationSeconds downTo 1) {
                _trainingState.value = TrainingUiState.Recording(zone, remaining, captured.size)
                delay(1_000)
            }
            trainingSamples = null
            val windows = extractor.rollingWindows(captured)
            val session = TrainingSession(
                UUID.randomUUID().toString(), zone.id, started, System.currentTimeMillis(), captured.toList(), windows
            )
            repository.saveTrainingSession(session)
            _trainingState.value = TrainingUiState.Complete(zone, captured.size, windows.size)
        }
    }

    fun dismissTrainingResult() { if (trainingJob?.isActive != true) _trainingState.value = TrainingUiState.Idle }

    fun startExperiment(zoneId: String, durationSeconds: Int = 30) {
        if (trainingJob?.isActive == true || experimentJob?.isActive == true) return
        val zone = zones.value.firstOrNull { it.id == zoneId } ?: return
        experimentJob = viewModelScope.launch {
            countdown { seconds -> _experimentState.value = ExperimentUiState.Countdown(zone, seconds) }
            experimentSmoother = TemporalSmoother(_config.value)
            experimentSmoother.reset()
            val started = System.currentTimeMillis()
            val captured = mutableListOf<RawSensorSample>()
            val windows = mutableListOf<FeatureWindow>()
            val predictions = mutableListOf<PredictionRecord>()
            experimentSamples = captured
            experimentWindows = windows
            experimentPredictions = predictions
            for (remaining in durationSeconds downTo 1) {
                _experimentState.value = ExperimentUiState.Recording(zone, remaining, predictions.size)
                delay(1_000)
            }
            experimentSamples = null
            experimentWindows = null
            experimentPredictions = null
            val session = ExperimentSession(
                UUID.randomUUID().toString(), zone.id, started, System.currentTimeMillis(),
                captured.toList(), windows.toList(), predictions.toList(),
            )
            repository.saveExperimentSession(session)
            _experimentState.value = ExperimentUiState.Complete(zone, ExperimentAnalytics.metrics(session))
        }
    }

    fun dismissExperimentResult() { if (experimentJob?.isActive != true) _experimentState.value = ExperimentUiState.Idle }

    fun updateActivationConfidence(value: Double) = updateConfig(_config.value.copy(activationConfidence = value))
    fun updateUnknownDistanceScale(value: Double) = updateConfig(_config.value.copy(unknownDistanceScale = value))
    fun updateDwellMillis(value: Long) = updateConfig(_config.value.copy(dwellMillis = value))

    fun saveAutomationRule(rule: AutomationRule) {
        viewModelScope.launch { repository.saveAutomationRule(rule) }
    }

    fun setAutomationRuleEnabled(rule: AutomationRule, enabled: Boolean) {
        saveAutomationRule(rule.copy(enabled = enabled))
    }

    fun deleteAutomationRule(ruleId: String) {
        viewModelScope.launch { repository.deleteAutomationRule(ruleId) }
    }

    fun rawCsv() = CsvExporter.rawSamples(zones.value, trainingSessions.value, experimentSessions.value)
    fun featureCsv() = CsvExporter.featureWindows(zones.value, trainingSessions.value, experimentSessions.value)
    fun confusionMatrix() = ExperimentAnalytics.confusionMatrix(experimentSessions.value)

    private suspend fun countdown(update: (Int) -> Unit) {
        for (seconds in 3 downTo 1) { update(seconds); delay(1_000) }
    }

    private fun updateConfig(value: DetectionConfig) {
        _config.value = value
        smoother.configure(value)
        experimentSmoother.configure(value)
    }

    private fun onSample(sample: RawSensorSample) {
        trainingSamples?.add(sample)
        experimentSamples?.add(sample)
        recentSamples.addLast(sample)
        val tenSecondsAgo = sample.timestampNanos - 10_000_000_000L
        while (recentSamples.firstOrNull()?.timestampNanos?.let { it < tenSecondsAgo } == true) recentSamples.removeFirst()

        if (sample.timestampNanos - lastUiTimestamp >= 100_000_000L) {
            lastUiTimestamp = sample.timestampNanos
            _liveSensors.value = LiveSensorUiState(sample, recentSamples.toList())
        }
        if (sample.timestampNanos - lastClassificationTimestamp < 500_000_000L) return
        val twoSecondsAgo = sample.timestampNanos - 2_000_000_000L
        val window = extractor.extract(recentSamples.filter { it.timestampNanos >= twoSecondsAgo }) ?: return
        lastClassificationTimestamp = sample.timestampNanos
        val raw = classifier.classify(window, _config.value.unknownDistanceScale)
        val detection = smoother.update(raw)
        _recognition.value = RecognitionUiState(
            detection,
            raw.probabilities.map { it.name to it.probability },
            _recognition.value.trainingWindowCount,
        )
        if (experimentSamples != null) {
            val testDetection = experimentSmoother.update(raw)
            experimentWindows?.add(window)
            experimentPredictions?.add(PredictionRecord(
                sample.timestampNanos,
                testDetection.rawZoneId,
                testDetection.smoothedZoneId,
                testDetection.activeZoneId,
                testDetection.confidence,
            ))
        }
    }

    override fun onCleared() {
        collector.stop()
        super.onCleared()
    }
}
