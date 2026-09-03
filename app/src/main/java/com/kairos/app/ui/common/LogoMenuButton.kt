package com.kairos.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kairos.app.R

/** The Kairos logo used as the top-left menu control (opens the nav rail),
 *  matching the web — a logo, not a hamburger. */
@Composable
fun LogoMenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Image(
            painter = painterResource(R.drawable.kairos_logo),
            contentDescription = "Menu",
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}
