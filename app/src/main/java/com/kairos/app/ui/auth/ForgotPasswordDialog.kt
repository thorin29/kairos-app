package com.kairos.app.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.launch

/**
 * "Forgot your password?" — asks the server to email a single-use reset code to
 * the address on file, then confirms without revealing whether the account
 * exists. Shared by the enroll and lock screens.
 */
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sent) "Check your email" else "Reset your password") },
        text = {
            if (sent) {
                Text(
                    "If that account has an email on file, a reset code is on its " +
                        "way. Enter it here to choose a new password.",
                )
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Your name or email") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = {
                    val id = text.trim()
                    if (id.isNotBlank()) {
                        scope.launch {
                            runCatching { container.sessionRepository.requestReset(id) }
                            sent = true
                        }
                    }
                }) { Text("Send reset code") }
            }
        },
        dismissButton = {
            if (!sent) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
