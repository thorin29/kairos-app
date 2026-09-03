package com.kairos.app.ui.reauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.rememberContainer

@Composable
fun ReauthScreen(person: PersonDto?) {
    val container = rememberContainer()
    val vm: ReauthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ReauthViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sign in again", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (person != null) {
                "Your password changed, ${person.name}. Re-enter it to keep using this device."
            } else {
                "Your password changed. Re-enter it to keep using this device."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = ui.password,
            onValueChange = vm::onPassword,
            label = { Text("Password") },
            singleLine = true,
            enabled = !ui.submitting,
            isError = ui.error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.error != null) {
            Text(
                ui.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Button(
            onClick = vm::submit,
            enabled = !ui.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            if (ui.submitting) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text("Signing in…")
            } else {
                Text("Continue")
            }
        }

        TextButton(
            onClick = vm::signOut,
            enabled = !ui.submitting,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Sign out instead")
        }
    }
}
