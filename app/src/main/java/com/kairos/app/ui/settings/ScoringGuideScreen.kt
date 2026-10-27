package com.kairos.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.data.local.PayloadCacheStore
import com.kairos.app.data.remote.dto.ScoringGuideDto
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

/**
 * "How scoring works": the server's plain-language guide (src/lib/scoring/guide.ts
 * on the web — the same text as the web's /scoring page, with the live numbers),
 * drawn as cards. The last copy is cached so it opens offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringGuideScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    var guide by remember { mutableStateOf<ScoringGuideDto?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        val cache = container.payloadCache
        if (guide == null) {
            guide = runCatching {
                cache.readAs("scoring-guide", "main", PayloadCacheStore.HOUSEHOLD, ScoringGuideDto.serializer())
            }.getOrNull()
        }
        val fresh = runCatching { container.sessionRepository.loadScoringGuide() }.getOrNull()
        if (fresh != null) {
            guide = fresh
            runCatching {
                cache.writeAs("scoring-guide", "main", PayloadCacheStore.HOUSEHOLD, ScoringGuideDto.serializer(), fresh)
            }
        } else if (guide == null) {
            failed = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How scoring works") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val g = guide
        when {
            g != null -> Column(
                Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                g.sections.forEach { s ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        s.lines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            failed -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn’t load this.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { attempt += 1 }) { Text("Retry") }
                }
            }
            else -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
