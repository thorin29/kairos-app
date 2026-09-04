package com.kairos.app.ui.workout

import androidx.compose.ui.graphics.Color

/**
 * Plate/bar config for the Weight calculator, ported from the web
 * (src/lib/workouts/plates.ts). Diameter/thickness in mm (approx real plates) so
 * the barbell drawing scales realistically. Colours per lb.
 */
data class Plate(
    val id: String,
    val weight: Double,
    val color: Color,
    val darkLabel: Boolean,
    val diameterMm: Int,
    val thicknessMm: Int,
)

data class BarOption(val weight: Double, val label: String, val type: String) // "straight" | "ez"

private val BLACK = Color(0xFF1F1F22)
private val GREEN = Color(0xFF2F9E44)
private val YELLOW = Color(0xFFF2C037)
private val BLUE = Color(0xFF1C7ED6)
private val RED = Color(0xFFE03131)
private val GREY = Color(0xFF6B7280)

val BUMPERS = listOf(
    Plate("b55", 55.0, RED, false, 450, 95),
    Plate("b45", 45.0, BLUE, false, 450, 78),
    Plate("b35", 35.0, YELLOW, true, 450, 62),
    Plate("b25", 25.0, GREEN, false, 450, 48),
    Plate("b10", 10.0, BLACK, false, 450, 26),
)
val STEEL = listOf(
    Plate("s10", 10.0, GREY, false, 232, 25),
    Plate("s5", 5.0, GREY, false, 205, 20),
    Plate("s2_5", 2.5, GREY, false, 160, 16),
)
val FRACTIONS = listOf(
    Plate("f1", 1.0, RED, false, 135, 13),
    Plate("f075", 0.75, BLUE, false, 125, 12),
    Plate("f05", 0.5, YELLOW, true, 115, 11),
    Plate("f025", 0.25, GREEN, false, 105, 10),
)
val ALL_PLATES = BUMPERS + STEEL + FRACTIONS
val PLATE_BY_ID = ALL_PLATES.associateBy { it.id }

val BARS = listOf(
    BarOption(45.0, "45", "straight"),
    BarOption(15.0, "15", "straight"),
    BarOption(19.0, "EZ", "ez"),
)

fun fmtWeight(lb: Double): String =
    if (lb == lb.toLong().toDouble()) lb.toLong().toString()
    else (Math.round(lb * 100) / 100.0).toString()
