package com.example.magneticzones.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecognitionScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.recognition.collectAsState()
    val config by viewModel.config.collectAsState()
    val detection = state.detection
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Live Recognition", style = MaterialTheme.typography.headlineSmall)
        if (state.trainingWindowCount == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("No training windows yet. Add and train at least two locations before interpreting recognition.", Modifier.padding(14.dp))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CURRENT ZONE", style = MaterialTheme.typography.labelLarge)
                Text(
                    detection?.activeName?.uppercase() ?: "UNKNOWN",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("Confidence ${(detection?.confidence ?: 1.0).percent()}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle("STABILITY PIPELINE")
                Text("Raw prediction: ${detection?.rawName ?: "Unknown"}")
                Text("Smoothed prediction: ${detection?.smoothedName ?: "Unknown"}")
                Text("Active zone: ${detection?.activeName ?: "Unknown"}", fontWeight = FontWeight.Bold)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("ALL CANDIDATES")
                state.probabilities.forEach { (name, probability) ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name)
                            Text(probability.percent())
                        }
                        LinearProgressIndicator(progress = { probability.toFloat() }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("DEBUG THRESHOLDS")
                Text("Activation confidence: ${config.activationConfidence.percent()}")
                Slider(
                    value = config.activationConfidence.toFloat(),
                    onValueChange = { viewModel.updateActivationConfidence(it.toDouble()) },
                    valueRange = 0.30f..0.95f,
                )
                Text("Unknown distance scale: ${config.unknownDistanceScale.decimals(1)}")
                Slider(
                    value = config.unknownDistanceScale.toFloat(),
                    onValueChange = { viewModel.updateUnknownDistanceScale(it.toDouble()) },
                    valueRange = 0.3f..6f,
                )
                Text("Required stable time: ${(config.dwellMillis / 1000.0).decimals(1)} s")
                Slider(
                    value = config.dwellMillis.toFloat(),
                    onValueChange = { viewModel.updateDwellMillis(it.toLong()) },
                    valueRange = 1_000f..5_000f,
                    steps = 7,
                )
                Text(
                    "Unknown distance controls how quickly an unfamiliar fingerprint loses known-zone probability. Tune it only with independent validation visits.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
