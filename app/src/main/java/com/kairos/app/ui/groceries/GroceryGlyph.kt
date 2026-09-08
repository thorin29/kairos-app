package com.kairos.app.ui.groceries

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairos.app.R

private val GLYPH_DRAWABLES: Map<String, Int> = mapOf(
    "ic:napkin" to R.drawable.grocery_napkin,
    "ic:papertowel" to R.drawable.grocery_papertowel,
    "ic:waterbottle" to R.drawable.grocery_waterbottle,
    "ic:protein" to R.drawable.grocery_protein,
)

/**
 * Renders a grocery item's stored icon: a small picture for the "ic:*" tokens the
 * server's catalog guesser emits for items with no matching emoji (napkins,
 * paper towels, bottled water, protein), or the emoji/text otherwise. Unknown
 * tokens fall back to a box.
 */
@Composable
fun GroceryGlyph(
    icon: String,
    emojiStyle: TextStyle,
    size: Dp = 22.dp,
) {
    val drawable = GLYPH_DRAWABLES[icon]
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(size + 4.dp),
        )
    } else {
        Text(if (icon.startsWith("ic:")) "\uD83D\uDCE6" else icon, style = emojiStyle)
    }
}
