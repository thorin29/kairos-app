package com.kairos.app.ui.enroll

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.common.SentenceCaps

@Composable
fun EnrollScreen(
    onNeedSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val vm: EnrollViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EnrollViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Enter your code", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A parent generates a one-time code in the admin area. Type it here to pair this device.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = ui.code,
            onValueChange = vm::onCodeChange,
            label = { Text("Enrollment code") },
            placeholder = { Text("ABCD-EF23") },
            singleLine = true,
            isError = ui.error != null,
            enabled = !ui.enrolling,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = ui.deviceName,
            onValueChange = vm::onDeviceNameChange,
            keyboardOptions = SentenceCaps,
            label = { Text("Device name (optional)") },
            singleLine = true,
            enabled = !ui.enrolling,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
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

        if (ui.needSignIn) {
            Button(
                onClick = onNeedSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text("Sign in")
            }
        } else {
            Button(
                onClick = vm::enroll,
                enabled = !ui.enrolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                if (ui.enrolling) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text("Enrolling…")
                } else {
                    Text("Enroll this device")
                }
            }
        }

        TextButton(
            onClick = onBack,
            enabled = !ui.enrolling,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Back")
        }
    }
}
