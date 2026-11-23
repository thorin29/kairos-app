package com.kairos.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

/**
 * Saved plans (v0.540), a button of its own on Workouts: My plans and the
 * household's plans to open, and saving, sharing, exporting or importing this
 * plan (moved here from the foot of the plan editor). The web's SavedPlans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPlansScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: EditPlanViewModel = viewModel(
        factory = viewModelFactory { initializer { EditPlanViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()
    var tab by remember { mutableStateOf("mine") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChooserTabs(
                selected = tab,
                onSelect = { tab = it },
                tabs = listOf("mine" to "My plans", "household" to "Household"),
            )
            PlanLibrary(mine = tab == "mine", onUsed = onBack)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "THIS PLAN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlanFileActions(vm = vm, ui = ui)
                }
            }
        }
    }
}
