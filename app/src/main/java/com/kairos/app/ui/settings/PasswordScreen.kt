package com.kairos.app.ui.settings

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.launch

/**
 * Change your own password. The server checks the current one and the rules
 * (web `changeOwnPassword`) and says what's wrong in plain words. This phone
 * stays signed in; other phones and browsers ask for the new password.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val mismatch = again.isNotEmpty() && next != again
    val ready = current.isNotEmpty() && next.isNotEmpty() && next == again && !busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password") },
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
            PasswordField("Current password", current) { current = it; message = null }
            PasswordField("New password", next) { next = it; message = null }
            PasswordField("New password again", again, isError = mismatch) { again = it; message = null }
            if (mismatch) {
                Text("The new passwords don’t match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val r = runCatching { container.sessionRepository.changePassword(current, next) }.getOrNull()
                        busy = false
                        message = when {
                            r == null -> false to "Couldn’t reach the server. Try again when you’re online."
                            r.ok -> {
                                current = ""; next = ""; again = ""
                                true to "Password changed. Other devices will ask for it."
                            }
                            else -> false to (r.error ?: "Couldn’t change it.")
                        }
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (busy) "Saving…" else "Change password") }
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

@Composable
private fun PasswordField(label: String, value: String, isError: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
