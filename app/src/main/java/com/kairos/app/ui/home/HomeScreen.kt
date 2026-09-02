package com.kairos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.ui.common.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(person: PersonDto) {
    val container = rememberContainer()
    val vm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kairos") },
                actions = {
                    IconButton(onClick = vm::signOut, enabled = !ui.busy) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Avatar(person)
            Text(
                person.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "@${person.shortName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${person.kind.lowercase().replaceFirstChar { it.uppercase() }} · ${person.role.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "You're enrolled. Your dashboard lands here next.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun Avatar(person: PersonDto) {
    // Uploaded avatar photos (avatarUrl) aren't loaded yet — they stay behind
    // Authelia on the public domain. The emoji icon or initials cover it for now.
    val label = person.avatarIcon
        ?: person.name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
            .ifBlank { person.shortName.take(2).uppercase() }

    Surface(
        modifier = Modifier.size(96.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
