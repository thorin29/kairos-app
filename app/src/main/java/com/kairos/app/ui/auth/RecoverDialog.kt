package com.kairos.app.ui.auth

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.ApiException
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * "Recover this phone" — self-service re-enrollment when you're away from home.
 * Verify the account's own username + password; the server emails a one-time
 * code; entering it finishes enrollment through the normal join path. Needs both
 * the password and access to the account's email — no admin, browser, or PC.
 */
@Composable
fun RecoverDialog(onDismiss: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (codeSent) "Enter your code" else "Recover this phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!codeSent) {
                    Text(
                        "Enter your Kairos username and password. We\u2019ll email a " +
                            "one-time code to set this phone back up \u2014 no code from " +
                            "an admin needed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it; error = null },
                        label = { Text("Username or email") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    Text(
                        "If those details are correct, a one-time code is on its way " +
                            "to your email. Enter it here to finish setting up this phone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        label = { Text("Emailed code") },
                        singleLine = true,
                    )
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (!codeSent) {
                TextButton(
                    enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                container.sessionRepository.startRecovery(identifier, password)
                                codeSent = true
                            } catch (e: ApiException) {
                                error = e.error.message ?: "Couldn\u2019t send a code. Try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Send code") }
            } else {
                TextButton(
                    enabled = !busy && code.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                container.sessionRepository.join(code.trim(), password, Build.MODEL)
                                // Success flips the session to Ready and replaces this screen.
                            } catch (e: ApiException) {
                                error = "That code didn\u2019t work. Check it and try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Continue") }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
