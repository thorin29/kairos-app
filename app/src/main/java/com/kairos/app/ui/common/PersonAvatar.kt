package com.kairos.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.PersonDto

/**
 * A person's avatar for ordinary content surfaces (chores, school, money …),
 * unlike the sidebar-styled one in KairosRail. Renders the uploaded photo over
 * the device-authed /api/v1/avatars endpoint with the web's position/zoom
 * transform, else the person's emoji icon or initials on a colour-tinted disc.
 */
@Composable
fun PersonAvatar(person: PersonDto, size: Dp = 36.dp) {
    val container = rememberContainer()
    val label = person.avatarIcon ?: initialsOf(person.name)
    val base = container.sessionRepository.baseUrlRaw
    val url = person.avatarUrl
    val tint = parsePersonColor(person.color)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null && base != null) {
                val xf = parseAvatarXf(person.avatarPosition)
                SubcomposeAsyncImage(
                    model = ApiClient.resolveUrl(base, url),
                    imageLoader = container.imageLoader,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .graphicsLayer {
                            scaleX = xf.scale
                            scaleY = xf.scale
                            translationX = xf.tx / 100f * this.size.width
                            translationY = xf.ty / 100f * this.size.height
                        },
                    loading = { Initials(label, tint) },
                    error = { Initials(label, tint) },
                )
            } else {
                Initials(label, tint)
            }
        }
        // The person's colour is the ring (matches the web avatar), drawn on top
        // so it stays crisp over a photo edge.
        Box(Modifier.fillMaxSize().border(2.5.dp, tint, CircleShape))
    }
}

@Composable
private fun Initials(label: String, color: Color) {
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

private data class AvatarXf(val tx: Float, val ty: Float, val scale: Float)

private fun parseAvatarXf(value: String?): AvatarXf {
    val m = Regex("^(-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?) (\\d+(?:\\.\\d+)?)$")
        .find(value ?: "") ?: return AvatarXf(0f, 0f, 1f)
    return AvatarXf(
        m.groupValues[1].toFloatOrNull() ?: 0f,
        m.groupValues[2].toFloatOrNull() ?: 0f,
        m.groupValues[3].toFloatOrNull() ?: 1f,
    )
}

private fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { name.take(2).uppercase() }

private fun parsePersonColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF64748B)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF64748B)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF64748B)
    }
}
