package com.kairos.app.ui.groceries

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.nav.KairosIcons

/**
 * Renders a grocery item's stored icon: a drawn glyph for the "ic:*" tokens the
 * server's catalog guesser emits for items with no matching emoji (napkins,
 * bottled water), or the emoji/text otherwise. Unknown tokens fall back to a box.
 */
@Composable
fun GroceryGlyph(
    icon: String,
    emojiStyle: TextStyle,
    size: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    when (icon) {
        "ic:napkin" -> Icon(KairosIcons.Napkin, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        "ic:waterbottle" -> Icon(KairosIcons.WaterBottle, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        else -> Text(if (icon.startsWith("ic:")) "\uD83D\uDCE6" else icon, style = emojiStyle)
    }
}
