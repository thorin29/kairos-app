package com.kairos.app.ui.groceries

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
    "ic:sorbet" to R.drawable.grocery_sorbet,
)

/** Bundled Material Design Icons (Apache-2.0) the catalog emits as `mdi:<name>`
 *  for items with no good emoji. Tinted to the content color at render. */
private val MDI_DRAWABLES: Map<String, Int> = mapOf(
    "mdi:cup-outline" to R.drawable.mdi_cup_outline,
    "mdi:sack" to R.drawable.mdi_sack,
    "mdi:spoon-sugar" to R.drawable.mdi_spoon_sugar,
    "mdi:shaker-outline" to R.drawable.mdi_shaker_outline,
    "mdi:bottle-tonic-outline" to R.drawable.mdi_bottle_tonic_outline,
    "mdi:soy-sauce" to R.drawable.mdi_soy_sauce,
    "mdi:bottle-soda-classic" to R.drawable.mdi_bottle_soda_classic,
    "mdi:spray-bottle" to R.drawable.mdi_spray_bottle,
    "mdi:pump" to R.drawable.mdi_pump,
    "mdi:diaper-outline" to R.drawable.mdi_diaper_outline,
    "mdi:box" to R.drawable.mdi_box,
    "mdi:bowl-mix-outline" to R.drawable.mdi_bowl_mix_outline,
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
    val mdi = MDI_DRAWABLES[icon]
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(size + 4.dp),
        )
    } else if (mdi != null) {
        Icon(
            painter = painterResource(mdi),
            contentDescription = null,
            modifier = Modifier.size(size + 2.dp),
        )
    } else {
        // Unknown prefixed token (ic:/mdi:/other) -> box, never the raw string.
        Text(if (Regex("^[a-z]+:").containsMatchIn(icon)) "\uD83D\uDCE6" else icon, style = emojiStyle)
    }
}
