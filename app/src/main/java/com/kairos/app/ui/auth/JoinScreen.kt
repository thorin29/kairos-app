package com.kairos.app.ui.auth

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kairos.app.data.remote.dto.JoinCheckResponse
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * Redeems an invite from the app: sets a new account's password, or confirms an
 * existing account's, and enrolls this phone in one step. Reached from a
 * `kairos://join` deep link or by pasting the invite. Needs the server already
 * configured (handled by the onboarding order).
 */
@Composable
fun JoinScreen(token: String, onCancel: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var check by remember { mutableStateOf<JoinCheckResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        loading = true
        error = null
        check = runCatching { container.sessionRepository.joinCheck(token) }.getOrNull()
        loading = false
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val c = check
        when {
            loading -> CircularProgressIndicator()

            c == null || !c.valid -> {
                Text(
                    "This invite is invalid or has expired.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ask a parent for a new one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onCancel) { Text("Back") }
            }

            else -> {
                val setup = !c.hasPassword || c.purpose == "reset"
                val reset = c.purpose == "reset"
                Text(
                    if (c.name.isNotBlank()) "Hi ${c.name}" else "Welcome",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (reset) "Choose a new password for your account."
                    else if (setup) "Create a password to finish setting up your account."
                    else "Enter your password to add this phone to your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(if (setup) "New password" else "Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (setup) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        error = when {
                            password.isBlank() -> "Enter your password."
                            setup && password.length < 6 -> "Use at least 6 characters."
                            setup && password != confirm -> "Those passwords don't match."
                            else -> null
                        }
                        if (error != null) return@Button
                        submitting = true
                        scope.launch {
                            try {
                                container.sessionRepository.join(token, password, Build.MODEL)
                                container.pendingJoinToken.value = null
                            } catch (e: Exception) {
                                error = (e as? ApiException)?.error?.message
                                    ?: "Couldn't finish. Please try again."
                                submitting = false
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (submitting) "Working\u2026"
                        else if (setup) "Create account" else "Add this phone",
                    )
                }
                if (!setup) {
                    TextButton(onClick = { showForgot = true }) {
                        Text("Forgot your password?")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }

    if (showForgot) {
        ForgotPasswordDialog(onDismiss = { showForgot = false })
    }
}
