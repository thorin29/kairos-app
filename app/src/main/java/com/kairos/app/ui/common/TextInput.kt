package com.kairos.app.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Default keyboard options for free-text fields (titles, names, notes, and the
 * like): ask the IME to capitalize the first letter of each sentence. Numeric,
 * email, password and URL fields keep their own options and don't use this.
 */
val SentenceCaps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
