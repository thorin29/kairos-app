package com.kairos.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * GET /api/v1/hockey (web 0.600+). The server shapes everything with
 * buildHockeyView — event names, which button, saved location — so the app
 * only draws it. `state` is "ok", "off" (Hockey isn't set up) or "error".
 */
@Serializable
data class HockeyDto(
    val state: String = "off",
    val error: String? = null,
    val eventTypeId: String? = null,
    val days: List<String> = emptyList(),
    val rinks: List<String> = emptyList(),
    val checkedAt: String? = null,
    val warnings: List<String> = emptyList(),
    val sections: List<HockeySectionDto> = emptyList(),
)

@Serializable
data class HockeySectionDto(
    val name: String,
    val colA: String = "Signed up",
    val colB: String? = null,
    val link: String? = null,
    val rink: String? = null,
    val rows: List<HockeyRowDto> = emptyList(),
)

@Serializable
data class HockeyRowDto(
    val date: String,
    val start: String,
    val end: String,
    val endDayOffset: Int = 0,
    val minutes: Int = 0,
    val rink: String? = null,
    val skaters: HockeyCountDto? = null,
    val goalies: HockeyCountDto? = null,
    val players: HockeyCountDto? = null,
    val link: String? = null,
    val add: HockeyAddDto,
)

@Serializable
data class HockeyCountDto(
    val enrolled: Int,
    val max: Int? = null,
    val remaining: Int? = null,
)

@Serializable
data class HockeyAddDto(
    val title: String,
    val label: String = "Add to calendar",
    val location: String? = null,
)
