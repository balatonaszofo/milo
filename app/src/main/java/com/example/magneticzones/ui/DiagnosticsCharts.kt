package com.example.magneticzones.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.magneticzones.model.TrainingSession
import com.example.magneticzones.model.Zone

private val chartColors = listOf(
    Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFF009688), Color(0xFFFF9800),
    Color(0xFF673AB7), Color(0xFF795548), Color(0xFF00ACC1), Color(0xFF7CB342),
)

@Composable
fun FeatureScatterPlot(zones: List<Zone>, sessions: List<TrainingSession>) {
    val points = zones.flatMapIndexed { index, zone ->
        sessions.filter { it.zoneId == zone.id }.flatMap { it.windows }.map { window ->
            Triple(window["mag_mean"], window["mag_std"], index)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("2D FEATURE OVERLAP")
            Text("Each dot is a training window: magnitude mean (x) vs magnitude standard deviation (y).")
            if (points.isEmpty()) Text("Train locations to populate the plot.")
            else {
                val minX = points.minOf { it.first }
                val maxX = points.maxOf { it.first }
                val minY = points.minOf { it.second }
                val maxY = points.maxOf { it.second }
                Canvas(
                    Modifier.fillMaxWidth().height(240.dp)
                        .background(Color(0xFFF1F2F6), RoundedCornerShape(8.dp))
                ) {
                    val xSpan = (maxX - minX).coerceAtLeast(0.01)
                    val ySpan = (maxY - minY).coerceAtLeast(0.01)
                    points.forEach { (xValue, yValue, colorIndex) ->
                        val x = 12f + ((xValue - minX) / xSpan).toFloat() * (size.width - 24f)
                        val y = size.height - 12f - ((yValue - minY) / ySpan).toFloat() * (size.height - 24f)
                        drawCircle(chartColors[colorIndex % chartColors.size].copy(alpha = 0.65f), 6f, Offset(x, y))
                    }
                }
                Text("Magnitude mean: ${minX.decimals(1)}…${maxX.decimals(1)} μT")
                Text("Magnitude std dev: ${minY.decimals(2)}…${maxY.decimals(2)} μT")
                zones.forEachIndexed { index, zone ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Spacer(Modifier.size(12.dp).background(chartColors[index % chartColors.size], CircleShape))
                        Text(zone.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
