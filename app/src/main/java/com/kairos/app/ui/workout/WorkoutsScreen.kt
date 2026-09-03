package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer

/**
 * The Workouts section page. Step 1: TODAY — today's scheduled exercises with
 * inline weight/reps logging and mark-done/rest, in the nav shell. History, the
 * progress graph, the weight calculator, browse, and plan/rotation come next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: WorkoutLogViewModel = viewModel(
        factory = viewModelFactory {
            // null date -> the server resolves "today" and returns the concrete date.
            initializer { WorkoutLogViewModel(container.sessionRepository, null) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.savedTick) {
        if (ui.savedTick > 0) snackbar.showSnackbar("Workout saved")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workouts") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        WorkoutLogContent(Modifier.padding(inner), vm)
    }
}
