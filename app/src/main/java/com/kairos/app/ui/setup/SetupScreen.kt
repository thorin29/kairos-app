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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    fun connectTo(url: String) {
        if (com.kairos.app.data.remote.LocalNetwork.needsPermission(context, url.trim())) {
            localNetLauncher.launch(com.kairos.app.data.remote.LocalNetwork.PERMISSION)
        } else {
            vm.connect()
        }
    }

    fun onConnect() = connectTo(ui.url)

    // Scan code (v0.519): an invitation QR carries the server and the code, so
    // a new phone connects and goes straight on to the invitation.
    var scanning by remember { mutableStateOf(false) }
    fun useJoin(scan: com.kairos.app.ui.auth.JoinScan?) {
        if (scan == null) return
        container.pendingJoinToken.value = scan.token
        scan.server?.let { server ->
            vm.onUrlChange(server)
            connectTo(server)
        }
    }
    // The same from an invitation link opened with the phone's camera.
    val linkServer by container.pendingJoinServer.collectAsState()
    LaunchedEffect(linkServer) {
        val server = linkServer ?: return@LaunchedEffect
        container.pendingJoinServer.value = null
        vm.onUrlChange(server)
        connectTo(server)
    }
    if (scanning) {
        com.kairos.app.ui.auth.QrScanDialog(
            onResult = { text ->
                scanning = false
                useJoin(com.kairos.app.ui.auth.parseJoin(text))
            },
            onClose = { scanning = false },
        )
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
        OutlinedButton(
            onClick = { scanning = true },
            enabled = !ui.connecting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) { Text("Scan code") }
    }
}
