package com.kairos.app.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.AccentOutlinedButton
import com.kairos.app.ui.common.FullPage
import com.kairos.app.ui.common.PageCard
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.common.AnimatedDialog

/** What Log another logs through the log flow (v0.491): its categories.
 *  v0.585: the pop-up became LogAnotherPage and the routines RoutinesPage (DayTools). */
internal val LOG_KINDS = listOf(
    "strength" to "Strength",
    "cardio" to "Cardio",
    "sport" to "Sport",
)

/** The log flow's categories for each of [LOG_KINDS]; "other" is all of them. */
internal fun logCategoriesFor(kind: String): List<String>? = when (kind) {
    "strength" -> listOf("WEIGHTS")
    "cardio" -> listOf("RUNNING", "ROWING", "RUCKING", "CARDIO")
    "sport" -> listOf("SPORT")
    else -> null
}
