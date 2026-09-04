package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

// Bar proportions taken from the uploaded Claude Design SVGs (viewBox 304x80):
// shaft half-width 49, collars 11 wide, sleeves 89 long / 22 tall, collars 30
// tall, straight shaft 8 tall, EZ dip 12; flat corners, no end caps, a light
// outline. Plates keep real mm sizes (SCALE) and the sleeve extends to hold them.
private const val SCALE = 0.42f
private const val SHAFT_HALF = 49f
private const val COLLAR = 11f
private const val END = 14f
private const val SLEEVE_MIN = 89f
private const val AXIS = 150f
private const val VIEW_H = 300f
private const val SLEEVE_H = 22f
private const val COLLAR_H = 30f
private const val SHAFT_H = 8f
private const val EZ_AMP = 12f

private val SLEEVE = Color(0xFFB7BDC4)
private val COLLAR_C = Color(0xFF7C848D)
private val SHAFT = Color(0xFF9AA1A9)
private val OUTLINE = Color(0x596F767E) // #6f767e @ 0.35

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightCalculatorScreen(onBack: () -> Unit) {
    var bar by remember { mutableStateOf(BARS[0]) }
    var loaded by remember { mutableStateOf(listOf<String>()) } // plate ids, one entry = one pair

    val total = bar.weight + loaded.sumOf { (PLATE_BY_ID[it]?.weight ?: 0.0) * 2 }
    val perSide = loaded.mapNotNull { PLATE_BY_ID[it] }
        .sortedWith(compareByDescending<Plate> { it.diameterMm }.thenByDescending { it.weight })
    val counts = loaded.groupingBy { it }.eachCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 12.dp),
            ) {
                Barbell(perSide, bar.type)
            }

            Text(
                "${fmtWeight(total)} lb",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // Bar selector + Clear
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(start = 8.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BARS.forEach { b ->
                        val on = b.label == bar.label
                        Text(
                            b.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable { bar = b }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                TextButton(onClick = { loaded = emptyList() }, enabled = loaded.isNotEmpty()) {
                    Text("Clear")
                }
            }

            // Loaded (tap to remove a pair)
            if (loaded.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    counts.forEach { (id, n) ->
                        val p = PLATE_BY_ID[id] ?: return@forEach
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(p.color)
                                .clickable { loaded = removeLast(loaded, id) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${fmtWeight(p.weight)} ×$n  ✕",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (p.darkLabel) Color(0xFF1F1F22) else Color.White,
                            )
                        }
                    }
                }
            }

            // Plate pickers
            PlateRow("Bumpers", BUMPERS) { loaded = loaded + it }
            PlateRow("Steel", STEEL) { loaded = loaded + it }
            PlateRow("Fractional", FRACTIONS) { loaded = loaded + it }
        }
    }
}

private fun removeLast(list: List<String>, id: String): List<String> {
    val i = list.lastIndexOf(id)
    if (i == -1) return list
    return list.toMutableList().also { it.removeAt(i) }
}

@Composable
private fun PlateRow(title: String, plates: List<Plate>, onAdd: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plates.forEach { p ->
                Box(
                    Modifier
                        .size(width = 52.dp, height = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.color)
                        .border(2.dp, Color(0x33000000), RoundedCornerShape(12.dp))
                        .clickable { onAdd(p.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        fmtWeight(p.weight),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (p.darkLabel) Color(0xFF1F1F22) else Color.White,
                    )
                }
            }
        }
    }
}

/** The loaded barbell — bar (straight or EZ) with plates stepping out from each
 *  collar, mirrored. Ported from the web Barbell (weight-calculator.tsx). */
@Composable
private fun Barbell(perSide: List<Plate>, barType: String) {
    val measurer = rememberTextMeasurer()
    val sideThickness = perSide.sumOf { (it.thicknessMm * SCALE).toDouble() }.toFloat()
    val sleeveLen = maxOf(SLEEVE_MIN, sideThickness + END)
    val half = SHAFT_HALF + COLLAR + sleeveLen
    val width = half * 2f
    val cx = width / 2f

    Canvas(Modifier.fillMaxWidth().aspectRatio(width / VIEW_H)) {
        val k = size.width / width
        fun sx(v: Float) = v * k
        fun sy(v: Float) = v * k
        // Fill + your light outline, flat corners (no rounding).
        fun barRect(x: Float, y: Float, w: Float, h: Float, c: Color) {
            drawRect(color = c, topLeft = Offset(sx(x), sy(y)), size = Size(w * k, h * k))
            drawRect(color = OUTLINE, topLeft = Offset(sx(x), sy(y)), size = Size(w * k, h * k), style = Stroke(width = 1f))
        }
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) {
            drawRoundRect(
                color = c,
                topLeft = Offset(sx(x), sy(y)),
                size = Size(w * k, h * k),
                cornerRadius = CornerRadius(r * k, r * k),
            )
        }

        // sleeves
        barRect(cx + SHAFT_HALF + COLLAR, AXIS - SLEEVE_H / 2, sleeveLen, SLEEVE_H, SLEEVE)
        barRect(cx - SHAFT_HALF - COLLAR - sleeveLen, AXIS - SLEEVE_H / 2, sleeveLen, SLEEVE_H, SLEEVE)
        // collars
        barRect(cx + SHAFT_HALF, AXIS - COLLAR_H / 2, COLLAR, COLLAR_H, COLLAR_C)
        barRect(cx - SHAFT_HALF - COLLAR, AXIS - COLLAR_H / 2, COLLAR, COLLAR_H, COLLAR_C)

        // shaft — straight bar, or your EZ W-path
        if (barType == "ez") {
            val path = ezPath(cx, k)
            drawPath(path, OUTLINE, style = Stroke(width = 10f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(path, SHAFT, style = Stroke(width = 8f * k, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else {
            barRect(cx - SHAFT_HALF, AXIS - SHAFT_H / 2, 2 * SHAFT_HALF, SHAFT_H, SHAFT)
        }

        // plates — laid from each collar outward, mirrored.
        var x = cx + SHAFT_HALF + COLLAR
        for (p in perSide) {
            val w = p.thicknessMm * SCALE
            val h = p.diameterMm * SCALE
            val r = min(3f, w / 2f)
            // right
            rrect(x, AXIS - h / 2f, w, h, r, p.color)
            drawRoundRectStroke(x, AXIS - h / 2f, w, h, r, k)
            // left (mirror)
            val lx = width - x - w
            rrect(lx, AXIS - h / 2f, w, h, r, p.color)
            drawRoundRectStroke(lx, AXIS - h / 2f, w, h, r, k)
            // label (rotated) when the plate is wide enough
            if (w * k > 15f) {
                drawPlateLabel(measurer, fmtWeight(p.weight), sx(x + w / 2f), sy(AXIS), p.darkLabel)
                drawPlateLabel(measurer, fmtWeight(p.weight), sx(lx + w / 2f), sy(AXIS), p.darkLabel)
            }
            x += w + 1.5f
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectStroke(
    x: Float, y: Float, w: Float, h: Float, r: Float, k: Float,
) {
    drawRoundRect(
        color = Color(0x59000000),
        topLeft = Offset(x * k, y * k),
        size = Size(w * k, h * k),
        cornerRadius = CornerRadius(r * k, r * k),
        style = Stroke(width = 1f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlateLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    cxPx: Float,
    cyPx: Float,
    dark: Boolean,
) {
    val result = measurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    rotate(90f, pivot = Offset(cxPx, cyPx)) {
        drawText(
            result,
            color = if (dark) Color(0xFF1F1F22) else Color.White,
            topLeft = Offset(cxPx - result.size.width / 2f, cyPx - result.size.height / 2f),
        )
    }
}

/** The EZ-curl W shaft, matching the uploaded SVG exactly:
 *  flat from each collar to ±31, dips down 12 at ±18, peak at centre. */
private fun ezPath(cx: Float, k: Float): Path {
    fun x(off: Float) = (cx + off) * k
    val top = AXIS * k
    val dip = (AXIS + EZ_AMP) * k
    val p = Path()
    p.moveTo(x(-SHAFT_HALF), top)   // cx-49
    p.lineTo(x(-31f), top)
    p.lineTo(x(-18f), dip)
    p.lineTo(x(0f), top)
    p.lineTo(x(18f), dip)
    p.lineTo(x(31f), top)
    p.lineTo(x(SHAFT_HALF), top)    // cx+49
    return p
}
