package com.example.magneticzones.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.magneticzones.model.Zone

@Composable
fun DiagnosticsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val zones by viewModel.zones.collectAsState()
    val training by viewModel.trainingSessions.collectAsState()
    val experiments by viewModel.experimentSessions.collectAsState()
    val context = LocalContext.current
    var pendingCsv by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(pendingCsv) } }
    }

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Diagnostics & Export", style = MaterialTheme.typography.headlineSmall)
        Text("Overlap here is evidence against reliable zone detection—not something to tune away using the validation set.")
        DistributionTable(zones, training)
        FeatureScatterPlot(zones, training)
        ConfusionMatrix(zones, viewModel.confusionMatrix())
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("CSV EXPORT")
                Text("Raw export contains every stored sensor snapshot. Feature export contains each 2-second window and all classifier inputs.")
                Button(
                    onClick = { pendingCsv = viewModel.rawCsv(); exportLauncher.launch("magnetic-zones-raw.csv") },
                    enabled = training.isNotEmpty() || experiments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export raw samples") }
                OutlinedButton(
                    onClick = { pendingCsv = viewModel.featureCsv(); exportLauncher.launch("magnetic-zones-features.csv") },
                    enabled = training.isNotEmpty() || experiments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export feature windows") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SectionTitle("VALIDATION COVERAGE")
                Text("Run separate tests for:")
                Text("• portrait, landscape, face-up, and natural rotations")
                Text("• held in hand versus resting on the surface")
                Text("• stationary versus normal approach/movement")
                Text("• adjacent zones and an untrained neutral area")
                Text("• multiple visits and different times of day")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DistributionTable(zones: List<Zone>, sessions: List<com.example.magneticzones.model.TrainingSession>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("RAW MAGNETIC DISTRIBUTIONS")
            Text("Mean ± standard deviation; min…max, in μT", style = MaterialTheme.typography.bodySmall)
            zones.forEach { zone ->
                val samples = sessions.filter { it.zoneId == zone.id }.flatMap { it.samples }
                if (samples.isNotEmpty()) {
                    Text(zone.name, style = MaterialTheme.typography.titleSmall)
                    DistributionLine("Magnitude", samples.map { it.magneticMagnitude.toDouble() })
                    DistributionLine("Bx", samples.map { it.magnetic.x.toDouble() })
                    DistributionLine("By", samples.map { it.magnetic.y.toDouble() })
                    DistributionLine("Bz", samples.map { it.magnetic.z.toDouble() })
                }
            }
            if (sessions.isEmpty()) Text("Train locations to populate distributions.")
        }
    }
}

@Composable
private fun DistributionLine(label: String, values: List<Double>) {
    val mean = values.average()
    val std = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    Text("$label  ${mean.decimals(1)} ± ${std.decimals(1)}   ${values.min().decimals(1)}…${values.max().decimals(1)}")
}

@Composable
private fun ConfusionMatrix(zones: List<Zone>, matrix: Map<String, Map<String?, Double>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("VALIDATION CONFUSION MATRIX")
            Text("Rows are actual zones; columns are active predictions.", style = MaterialTheme.typography.bodySmall)
            if (matrix.isEmpty()) Text("Run validation tests to populate the matrix.")
            else Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row {
                        MatrixCell("Actual ↓ / Predicted →", 150)
                        zones.forEach { MatrixCell(it.name, 90) }
                        MatrixCell("Unknown", 90)
                    }
                    zones.filter { matrix.containsKey(it.id) }.forEach { actual ->
                        Row {
                            MatrixCell(actual.name, 150)
                            zones.forEach { predicted -> MatrixCell((matrix[actual.id]?.get(predicted.id) ?: 0.0).percent(), 90) }
                            MatrixCell((matrix[actual.id]?.get(null) ?: 0.0).percent(), 90)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(text: String, width: Int) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.width(width.dp).padding(1.dp)) {
        Text(text, Modifier.padding(7.dp), style = MaterialTheme.typography.bodySmall)
    }
}
