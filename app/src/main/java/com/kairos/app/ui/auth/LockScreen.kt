package com.kairos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * The phone is enrolled but logged out. It stays this person's phone, so getting
 * back in is just their username + password — no new code. A different account
 * is rejected; to hand the phone to someone else, a parent revokes it (forcing a
 * fresh code) or the app is reinstalled.
 */
@Composable
fun LockScreen(person: PersonDto) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }

    val whose = person.name

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sign in as $whose to unlock this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; error = null },
            label = { Text("Username or email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (identifier.isBlank() || password.isBlank()) {
                    error = "Enter your username and password."
                    return@Button
                }
                submitting = true
                scope.launch {
                    try {
                        container.sessionRepository.unlock(identifier, password)
                    } catch (e: Exception) {
                        error = (e as? ApiException)?.error?.message
                            ?: "That didn't work. Check your details and try again."
                        submitting = false
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (submitting) "Signing in\u2026" else "Log in") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showForgot = true }) {
            Text("Forgot your password?")
        }
    }

    if (showForgot) {
        ForgotPasswordDialog(onDismiss = { showForgot = false })
    }
}
