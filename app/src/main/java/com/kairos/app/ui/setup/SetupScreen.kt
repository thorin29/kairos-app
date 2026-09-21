package com.kairos.app.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer

@Composable
fun SetupScreen() {
    val container = rememberContainer()
    val vm: SetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SetupViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android 17: reaching a LAN server needs a runtime permission. Ask for it
    // first when the entered address is local, then connect either way.
    val localNetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> vm.connect() }

    fun onConnect() {
        if (com.kairos.app.data.remote.LocalNetwork.needsPermission(context, ui.url.trim())) {
            localNetLauncher.launch(com.kairos.app.data.remote.LocalNetwork.PERMISSION)
        } else {
            vm.connect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Kairos", style = MaterialTheme.typography.displaySmall)
        Text(
            "Connect to your household's server to get started.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = ui.url,
            onValueChange = vm::onUrlChange,
            label = { Text("Server address") },
            placeholder = { Text("https://…") },
            singleLine = true,
            isError = ui.error != null,
            enabled = !ui.connecting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
            onClick = { onConnect() },
            enabled = !ui.connecting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            if (ui.connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
                Text("Connecting…")
            } else {
                Text("Connect")
            }
        }
    }
}
