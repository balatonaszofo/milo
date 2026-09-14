package com.example.magneticzones.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.magneticzones.model.RawSensorSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GraphSignal { Magnitude, X, Y, Z }

@Composable
fun ExplorerScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val live by viewModel.liveSensors.collectAsState()
    val status by viewModel.sensorStatus.collectAsState()
    val sample = live.latest
    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sensor Explorer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${status.samplingFrequencyHz.decimals(1)} Hz  •  ${if (status.isMoving) "MOVING" else "STATIONARY"}  •  " +
                (sample?.let { SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(it.wallClockMillis)) } ?: "waiting"),
            style = MaterialTheme.typography.bodyMedium,
        )
        AvailabilityRow("Magnetometer", status.availability.magnetometer)
        AvailabilityRow("Accelerometer", status.availability.accelerometer)
        AvailabilityRow("Gyroscope", status.availability.gyroscope)
        AvailabilityRow("Rotation vector", status.availability.rotationVector)
        if (!status.availability.magnetometer) {
            Text("This device has no magnetometer; training and recognition cannot run.", color = MaterialTheme.colorScheme.error)
        }
        SensorValues(sample)
        MagneticGraph(live.magneticHistory)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AvailabilityRow(name: String, available: Boolean) {
    Text("$name: ${if (available) "available" else "unavailable"}", style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun SensorValues(sample: RawSensorSample?) {
    SensorCard("MAGNETOMETER") {
        ValueGrid(listOf(
            "Bx" to sample?.magnetic?.x, "By" to sample?.magnetic?.y,
            "Bz" to sample?.magnetic?.z, "Magnitude μT" to sample?.magneticMagnitude,
        ))
    }
    SensorCard("ACCELEROMETER") {
        ValueGrid(listOf("Ax" to sample?.acceleration?.x, "Ay" to sample?.acceleration?.y, "Az" to sample?.acceleration?.z))
    }
    SensorCard("GYROSCOPE") {
        ValueGrid(listOf("Gx" to sample?.gyroscope?.x, "Gy" to sample?.gyroscope?.y, "Gz" to sample?.gyroscope?.z))
    }
    SensorCard("ORIENTATION") {
        ValueGrid(listOf(
            "Pitch°" to sample?.orientation?.pitchDegrees,
            "Roll°" to sample?.orientation?.rollDegrees,
            "Azimuth°" to sample?.orientation?.azimuthDegrees,
        ))
    }
}

@Composable
private fun SensorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun ValueGrid(values: List<Pair<String, Number?>>) {
    values.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(value?.decimals() ?: "—", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MagneticGraph(samples: List<RawSensorSample>) {
    var signal by remember { mutableStateOf(GraphSignal.Magnitude) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("MAGNETIC HISTORY • 10 SECONDS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GraphSignal.entries.forEach { option ->
                    FilterChip(selected = signal == option, onClick = { signal = option }, label = { Text(option.name) })
                }
            }
            val values = samples.map {
                when (signal) {
                    GraphSignal.Magnitude -> it.magneticMagnitude
                    GraphSignal.X -> it.magnetic.x
                    GraphSignal.Y -> it.magnetic.y
                    GraphSignal.Z -> it.magnetic.z
                }
            }
            val min = values.minOrNull() ?: 0f
            val max = values.maxOrNull() ?: 1f
            Text("Range ${min.decimals(1)} to ${max.decimals(1)} μT", style = MaterialTheme.typography.labelMedium)
            Canvas(
                Modifier.fillMaxWidth().height(180.dp).background(Color(0xFFF1F2F6), RoundedCornerShape(8.dp))
            ) {
                if (values.size < 2) return@Canvas
                drawLine(Color.LightGray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                val spread = (max - min).coerceAtLeast(0.01f)
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = index.toFloat() / values.lastIndex * size.width
                    val y = size.height - ((value - min) / spread * size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(0xFF3F51B5), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }
    }
}
