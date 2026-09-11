package com.kairos.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kairos.app.R

/** The "update available" badge dot color — matches the nav rail's dot. */
private val LogoUpdateDot = Color(0xFF4FC3F7)

/**
 * The Kairos logo used as the top-left menu control (opens the nav rail),
 * matching the web — a logo, not a hamburger.
 *
 * When [updateAvailable] is left null (the default) the button reads the app's
 * update state itself, so every screen's top-bar logo carries a small dot while
 * an update is pending — no per-screen wiring. The nav rail passes `false` on
 * its own copy of the logo to suppress the dot there, because once the menu is
 * open the pending update is shown on the profile (collapsed) or the update row
 * (expanded) instead.
 */
@Composable
fun LogoMenuButton(
    onClick: () -> Unit,
    updateAvailable: Boolean? = null,
) {
    val selfUpdate by rememberContainer().updateChecker.available.collectAsState()
    val showDot = updateAvailable ?: (selfUpdate != null)

    IconButton(onClick = onClick) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.kairos_logo),
                contentDescription = "Menu",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (showDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(LogoUpdateDot),
                )
            }
        }
    }
}
