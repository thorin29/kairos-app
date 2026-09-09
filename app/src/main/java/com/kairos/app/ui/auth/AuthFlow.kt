package com.kairos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * A blank phone: it has no enrollment yet, so the only way in is an invitation
 * code (create a password for a new person, confirm it to add a phone, or set a
 * new one for a reset). Entering it hands off to [JoinScreen]. Signing in with a
 * username and password happens on the lock screen, once a phone is enrolled.
 */
@Composable
fun AuthFlow() {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var showForgot by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Set up this phone", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the invitation code from your household (texted, read aloud, or " +
                "in your invite email).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Invitation code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val raw = code.trim()
                val token = if (raw.contains("token=")) {
                    raw.substringAfter("token=").substringBefore("&").trim()
                } else {
                    raw
                }
                if (token.isNotBlank()) container.pendingJoinToken.value = token
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showForgot = true }) {
            Text("Forgot your password?")
        }
        TextButton(onClick = {
            scope.launch { container.sessionRepository.changeServer() }
        }) { Text("Change server") }
    }

    if (showForgot) {
        ForgotPasswordDialog(onDismiss = { showForgot = false })
    }
}
