package com.example.magneticzones.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun Atmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF8A5A2B).copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.08f),
                radius = size.width * 0.88f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF536B4F).copy(alpha = 0.12f), Color.Transparent),
                center = Offset(0f, size.height * 0.62f),
                radius = size.width * 0.72f,
            )
        )
    }
}

internal enum class RuleGlyph { Orbit, Spark, Settings, Back, Arrow, Add, More, Heat, Info }

@Composable
internal fun RuleIcon(glyph: RuleGlyph, description: String?, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(tint, Offset(x*w, y*h), Offset(xx*w, yy*h), stroke, StrokeCap.Round)
        when (glyph) {
            RuleGlyph.Add -> { line(.5f,.2f,.5f,.8f); line(.2f,.5f,.8f,.5f) }
            RuleGlyph.Arrow -> { line(.18f,.5f,.82f,.5f); line(.57f,.24f,.83f,.5f); line(.57f,.76f,.83f,.5f) }
            RuleGlyph.Back -> { line(.18f,.5f,.82f,.5f); line(.43f,.24f,.17f,.5f); line(.43f,.76f,.17f,.5f) }
            RuleGlyph.More -> for (i in 0..2) drawCircle(tint, radius = 1.5.dp.toPx(), center = Offset(w*(.25f+i*.25f),h*.5f))
            RuleGlyph.Orbit -> {
                drawOval(tint, topLeft = Offset(w*.07f,h*.22f), size = Size(w*.86f,h*.56f), style = Stroke(stroke))
                drawOval(tint, topLeft = Offset(w*.22f,h*.07f), size = Size(w*.56f,h*.86f), style = Stroke(stroke))
                drawCircle(tint, w*.07f)
            }
            RuleGlyph.Spark -> {
                val path = Path().apply {
                    moveTo(w*.48f,h*.08f); quadraticTo(w*.51f,h*.46f,w*.89f,h*.49f)
                    quadraticTo(w*.51f,h*.52f,w*.48f,h*.90f); quadraticTo(w*.45f,h*.52f,w*.07f,h*.49f)
                    quadraticTo(w*.45f,h*.46f,w*.48f,h*.08f); close()
                }
                drawPath(path,tint,style=Stroke(stroke)); line(.84f,.02f,.84f,.22f); line(.74f,.12f,.94f,.12f)
            }
            RuleGlyph.Settings -> {
                drawCircle(tint,w*.30f,style=Stroke(stroke)); drawCircle(tint,w*.10f,style=Stroke(stroke))
                for (i in 0..7) {
                    val a=i*Math.PI/4
                    line((.5+Math.cos(a)*.32).toFloat(),(.5+Math.sin(a)*.32).toFloat(),(.5+Math.cos(a)*.42).toFloat(),(.5+Math.sin(a)*.42).toFloat())
                }
            }
            RuleGlyph.Heat -> {
                for (i in 0..2) {
                    val x=w*(.25f+i*.25f)
                    drawPath(Path().apply { moveTo(x,h*.73f); cubicTo(x-w*.18f,h*.52f,x+w*.18f,h*.43f,x,h*.20f) },tint,style=Stroke(stroke,cap=StrokeCap.Round))
                }
                line(.16f,.89f,.84f,.89f)
            }
            RuleGlyph.Info -> { drawCircle(tint,w*.39f,style=Stroke(stroke)); line(.5f,.45f,.5f,.7f); drawCircle(tint,w*.045f,Offset(w*.5f,h*.3f)) }
        }
    }
}
