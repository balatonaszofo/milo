package com.example.magneticzones.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.magneticzones.experiment.ExperimentAnalytics

@Composable
fun ExperimentScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val zones by viewModel.zones.collectAsState()
    val sessions by viewModel.experimentSessions.collectAsState()
    val state by viewModel.experimentState.collectAsState()
    var selectedZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    if (selectedZoneId == null && zones.isNotEmpty()) selectedZoneId = zones.first().id

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Independent Validation", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose the true location before starting. Test visits are never added to training, so these results expose generalization failures.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("ACTUAL LOCATION")
        zones.forEach { zone ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedZoneId = zone.id }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selectedZoneId == zone.id, onClick = { selectedZoneId = zone.id })
                Text(zone.name)
            }
        }
        Button(
            onClick = { selectedZoneId?.let(viewModel::startExperiment) },
            enabled = selectedZoneId != null && (state is ExperimentUiState.Idle || state is ExperimentUiState.Complete),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("START 30-SECOND TEST") }

        SectionTitle("PREVIOUS TESTS")
        if (sessions.isEmpty()) Text("No validation tests recorded yet.")
        sessions.asReversed().take(12).forEach { session ->
            val zoneName = zones.firstOrNull { it.id == session.actualZoneId }?.name ?: "Deleted zone"
            val metrics = ExperimentAnalytics.metrics(session)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(zoneName, style = MaterialTheme.typography.titleMedium)
                    Text("Accuracy ${metrics.accuracy.percent()} • Unknown ${metrics.unknownFraction.percent()}")
                    Text("Average confidence ${metrics.averageConfidence.percent()} • Wrong transitions ${metrics.incorrectTransitions}")
                    Text("Recognized in ${metrics.recognitionTimeMillis?.let { "${it / 1000.0} s" } ?: "not recognized"}")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    ExperimentDialog(state, zones.associate { it.id to it.name }, viewModel::dismissExperimentResult)
}

@Composable
private fun ExperimentDialog(state: ExperimentUiState, names: Map<String, String>, dismiss: () -> Unit) {
    when (state) {
        ExperimentUiState.Idle -> Unit
        is ExperimentUiState.Countdown -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Actual: ${state.zone.name}") },
            text = { Text("Test starts in ${state.seconds}…\n\nUse the phone naturally; do not copy the training motion exactly.") },
        )
        is ExperimentUiState.Recording -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Testing ${state.zone.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { (30 - state.remainingSeconds) / 30f }, Modifier.fillMaxWidth())
                    Text("${state.remainingSeconds} seconds remaining")
                    Text("${state.predictionCount} independent prediction windows")
                }
            },
        )
        is ExperimentUiState.Complete -> AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
            title = { Text("Actual: ${state.zone.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.metrics.predictionFractions.entries.sortedByDescending { it.value }.forEach { (id, fraction) ->
                        Text("${id?.let { names[it] } ?: "Unknown"}: ${fraction.percent()}")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Accuracy: ${state.metrics.accuracy.percent()}")
                    Text("Average confidence: ${state.metrics.averageConfidence.percent()}")
                    Text("Incorrect transitions: ${state.metrics.incorrectTransitions}")
                    Text("Unknown time: ${state.metrics.unknownFraction.percent()}")
                    Text("Recognition time: ${state.metrics.recognitionTimeMillis?.let { "${(it / 1000.0).decimals(1)} s" } ?: "not recognized"}")
                }
            },
        )
    }
}
