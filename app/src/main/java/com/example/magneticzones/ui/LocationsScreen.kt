package com.example.magneticzones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LocationsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val zones by viewModel.zones.collectAsState()
    val sessions by viewModel.trainingSessions.collectAsState()
    val training by viewModel.trainingState.collectAsState()
    var newName by rememberSaveable { mutableStateOf("") }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Location Training", style = MaterialTheme.typography.headlineSmall)
        Text("Every 30-second visit stays separate. Repeat visits with small natural rotations and held/surface placements.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                newName, { newName = it }, Modifier.weight(1f),
                label = { Text("Location name") }, singleLine = true,
            )
            Button(
                onClick = { viewModel.addZone(newName); newName = "" },
                enabled = newName.isNotBlank(), modifier = Modifier.padding(top = 8.dp),
            ) { Text("Add") }
        }
        if (zones.isEmpty()) Card(Modifier.fillMaxWidth()) {
            Text("Add the Changing Table, Feeding Chair, Bottle Station, and Bassinet to begin.", Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(zones, key = { it.id }) { zone ->
                val visits = sessions.filter { it.zoneId == zone.id }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(zone.name, style = MaterialTheme.typography.titleMedium)
                            Text("${visits.size} visits • ${visits.sumOf { it.windows.size }} feature windows")
                        }
                        Button(
                            onClick = { viewModel.startTraining(zone.id) },
                            enabled = training is TrainingUiState.Idle || training is TrainingUiState.Complete,
                        ) { Text("Train") }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    TrainingDialog(training, viewModel::dismissTrainingResult)
}

@Composable
private fun TrainingDialog(state: TrainingUiState, dismiss: () -> Unit) {
    when (state) {
        TrainingUiState.Idle -> Unit
        is TrainingUiState.Countdown -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Training ${state.zone.name}") },
            text = { Text("Starting in ${state.seconds}…\n\nHold the phone as you naturally would.") },
        )
        is TrainingUiState.Recording -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Recording ${state.zone.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator({ (30 - state.remainingSeconds) / 30f }, Modifier.fillMaxWidth())
                    Text("${state.remainingSeconds} seconds remaining")
                    Text("${state.sampleCount} raw samples")
                    Text("Move and rotate the phone slightly, without deliberately sweeping the room.")
                }
            },
        )
        is TrainingUiState.Complete -> AlertDialog(
            onDismissRequest = dismiss,
            confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
            title = { Text("Training saved") },
            text = { Text("${state.zone.name}\n${state.sampleCount} samples\n${state.windowCount} feature windows") },
        )
    }
}
