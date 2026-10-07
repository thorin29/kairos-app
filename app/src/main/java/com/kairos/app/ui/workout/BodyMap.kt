package com.kairos.app.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The clickable body, matching the web.
 *
 * Two separate ideas, deliberately not the same field:
 *
 *   SELECTION — `nav`. Which target a tap resolves to. One per region.
 *   SHADING   — `group`. Which muscle group the region lights up as.
 *
 * Tapping the lower back or glutes selects a deadlift, but those pixels shade
 * as Core (obliques, lumbar erectors) and Legs (glutes) — the lower back being
 * part of the core, not a thing apart from it.
 *
 * The server decides both, from one involvement table, and sends them per
 * series. Reimplementing that table here would mean two of them to keep honest
 * about what a deadlift works.
 */

/** One fixed colour per muscle group. Must match the web's MG_COLOR exactly. */
private val MUSCLE_COLOR: Map<String, Color> = mapOf(
    "CHEST" to Color(0xFFE34948),
    "BACK" to Color(0xFF1BAF7A),
    "LEGS" to Color(0xFF2A78D6),
    "SHOULDERS" to Color(0xFFEB6834),
    "ARMS" to Color(0xFF8A5CD6),
    "CORE" to Color(0xFFEDA100),
    "GLUTES" to Color(0xFFD6568F),
    "CALVES" to Color(0xFF2F9BB5),
    "FOREARMS" to Color(0xFF7A6AD8),
    "UPPER_BACK" to Color(0xFF139C86),
    "FULL_BODY" to Color(0xFFE87BA4),
)

fun muscleColor(group: String?): Color? = group?.let { MUSCLE_COLOR[it] }

/** The same colour, faded toward the surface, for a muscle only assisted. */
fun fadedMuscleColor(group: String?, surface: Color): Color? =
    muscleColor(group)?.let { blend(surface, it, 0.32f) }

private fun blend(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * Parsed geometry for one figure, plus a hit region per tap target.
 *
 * Built once and remembered: parsing eighty path strings and rasterising them
 * into regions on every recomposition would be felt.
 */
private class FigureGeometry(
    val specs: List<BodyPathSpec>,
    outlines: Map<String, String>,
    val w: Float,
    val h: Float,
) {
    val paths: List<Path> = specs.map { PathParser().parsePathString(it.d).toPath() }

    val outlinePaths: Map<String, Path> =
        outlines.mapValues { PathParser().parsePathString(it.value).toPath() }

    /**
     * One region per TARGET, unioned from its member paths — so a tap anywhere
     * in Legs resolves to Legs rather than to whichever of its thirteen
     * muscles happened to be hit.
     */
    private val regions: Map<String, android.graphics.Region> = buildMap {
        val clip = android.graphics.Region(0, 0, w.toInt() + 1, h.toInt() + 1)
        specs.forEachIndexed { i, spec ->
            val nav = spec.nav ?: return@forEachIndexed
            val r = android.graphics.Region()
            r.setPath(paths[i].asAndroidPath(), clip)
            val cur = this[nav]
            if (cur == null) put(nav, r) else cur.op(r, android.graphics.Region.Op.UNION)
        }
    }

    /**
     * Which target contains this point, in viewBox units. Null off the body.
     *
     * The artwork's white seams between muscles belong to no region, and they
     * add up: only about a fifth of the canvas is strictly inside a target. A
     * finger landing on a seam should not silently do nothing, so an exact miss
     * probes outward in a short spiral and takes the first target it meets.
     */
    fun hit(x: Float, y: Float): String? {
        exact(x, y)?.let { return it }
        for (r in PROBE_RADII) {
            for (a in 0 until 8) {
                val rad = a * (Math.PI / 4)
                val found = exact(
                    x + (r * kotlin.math.cos(rad)).toFloat(),
                    y + (r * kotlin.math.sin(rad)).toFloat(),
                )
                if (found != null) return found
            }
        }
        return null
    }

    private fun exact(x: Float, y: Float): String? =
        regions.entries.firstOrNull { it.value.contains(x.toInt(), y.toInt()) }?.key

    private companion object {
        /** viewBox units. The figure is ~530 wide, so this is a few device px. */
        val PROBE_RADII = listOf(4, 9, 15)
    }
}

@Composable
private fun BodyFigure(
    geo: FigureGeometry,
    selected: Set<String>,
    fills: Map<String, Color>,
    available: Set<String>?,
    inert: Color,
    ring: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .aspectRatio(geo.w / geo.h)
            .pointerInput(geo, available) {
                detectTapGestures { off ->
                    val s = size.width / geo.w
                    val nav = geo.hit(off.x / s, off.y / s) ?: return@detectTapGestures
                    if (available == null || nav in available) onSelect(nav)
                }
            },
    ) {
        val s = size.width / geo.w
        scale(s, s, pivot = Offset.Zero) {
            geo.specs.forEachIndexed { i, spec ->
                val c = if (spec.group == null) inert else fills[spec.group] ?: inert
                drawPath(geo.paths[i], c)
            }
            // One ring per selected target, traced from the UNION of its
            // regions. Stroking each path rings every internal muscle seam.
            selected.forEach { nav ->
                geo.outlinePaths[nav]?.let { drawPath(it, ring, style = Stroke(width = 4f)) }
            }
        }
    }
}

/**
 * @param selected tap targets currently lit. Several at once on a multi-group day.
 * @param fills muscle group -> colour. A group absent from this map draws inert.
 * @param available targets with nothing behind them are drawn but not tappable.
 * @param view "front", "back" or "both" — a deadlift is posterior-only.
 */
@Composable
fun BodyMap(
    selected: List<String>,
    fills: Map<String, Color>,
    view: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    available: List<String>? = null,
    inert: Color = Color(0xFFDCE3EA),
    ring: Color = Color(0xFF10202F),
) {
    val front = remember {
        FigureGeometry(BODY_FRONT_PATHS, BODY_FRONT_OUTLINES, BODY_FRONT_W, BODY_FRONT_H)
    }
    val back = remember {
        FigureGeometry(BODY_BACK_PATHS, BODY_BACK_OUTLINES, BODY_BACK_W, BODY_BACK_H)
    }
    val sel = remember(selected) { selected.toSet() }
    val avail = remember(available) { available?.toSet() }

    val showFront = view == "front" || view == "both"
    val showBack = view == "back" || view == "both"

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showFront) {
            BodyFigure(
                geo = front,
                selected = sel,
                fills = fills,
                available = avail,
                inert = inert,
                ring = ring,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
        if (showBack) {
            BodyFigure(
                geo = back,
                selected = sel,
                fills = fills,
                available = avail,
                inert = inert,
                ring = ring,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
