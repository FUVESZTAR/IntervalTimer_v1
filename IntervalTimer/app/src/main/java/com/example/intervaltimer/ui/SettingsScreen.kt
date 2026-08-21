package com.example.intervaltimer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.intervaltimer.settings.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TimerViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    val autoRestore by repository.autoRestoreOnBootFlow.collectAsStateWithLifecycle(initialValue = false)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var watchEnabled by remember(uiState.config) { mutableStateOf(uiState.config.watchEnabled) }
    var phoneEnabled by remember(uiState.config) { mutableStateOf(uiState.config.phoneEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beállítások") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsRow(
                title = "Automatikus indulás újraindítás után",
                subtitle = "Ha aktív időzítés közben indul újra a telefon, folytatódjon automatikusan."
            ) {
                Switch(checked = autoRestore, onCheckedChange = { scope.launch { repository.setAutoRestoreOnBoot(it) } })
            }

            SettingsRow(
                title = "Telefon jelzés",
                subtitle = "A telefon adjon hangot/rezgést a jelzéskor."
            ) {
                Switch(checked = phoneEnabled, onCheckedChange = {
                    phoneEnabled = it
                    viewModel.updateConfig(uiState.config.copy(phoneEnabled = it))
                })
            }

            SettingsRow(
                title = "Óra jelzés",
                subtitle = "A Galaxy Watch5 is jelezzen a jelzéskor, ha elérhető."
            ) {
                Switch(checked = watchEnabled, onCheckedChange = {
                    watchEnabled = it
                    viewModel.updateConfig(uiState.config.copy(watchEnabled = it))
                })
            }

            HorizontalDivider()

            Text("Az alapértelmezett intervallum, jelzéstípus, hang és rezgésminta automatikusan mentésre kerül a főképernyőn végzett módosításokkor.",
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelLarge)
        }
        control()
    }
}
