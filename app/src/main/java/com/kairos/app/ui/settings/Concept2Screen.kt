package com.kairos.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.Concept2StatusDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** "Oct 31, 8:02 AM", or "not yet". */
private fun checkedLabel(iso: String?): String = try {
    if (iso == null) "not yet"
    else OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (_: Exception) {
    "not yet"
}

/**
 * Connect this person's Concept2 Logbook with their token (the same as the
 * web profile). Rows then land in the day's Cardio. The token goes to the
 * server once and never comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Concept2Screen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<Concept2StatusDto?>(null) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(Unit) {
        status = runCatching { container.sessionRepository.concept2Status() }.getOrNull()
        if (status == null) message = false to "Couldn’t reach the server. Try again when you’re online."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Concept2") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = status
            if (s?.connected == true) {
                Text("Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Last checked ${checkedLabel(s.checkedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val ok = runCatching { container.sessionRepository.disconnectConcept2() }.isSuccess
                            busy = false
                            if (ok) status = Concept2StatusDto(connected = false)
                            else message = false to "Couldn’t reach the server. Try again when you’re online."
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Disconnect") }
            } else if (s != null) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; message = null },
                    label = { Text("Logbook token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Concept2 Logbook → Edit Profile → Applications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable { runCatching { uri.openUri("https://log.concept2.com") } },
                )
                Button(
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                status = container.sessionRepository.connectConcept2(token.trim())
                                token = ""
                                message = true to "Connected."
                            } catch (e: ApiException) {
                                message = false to e.error.message
                            } catch (_: Exception) {
                                message = false to "Couldn’t reach the server. Try again when you’re online."
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && token.trim().length >= 16,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (busy) "Connecting…" else "Connect") }
            }
            message?.let { (ok, text) ->
                Text(
                    text,
                    color = if (ok) Color(0xFF047857) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

