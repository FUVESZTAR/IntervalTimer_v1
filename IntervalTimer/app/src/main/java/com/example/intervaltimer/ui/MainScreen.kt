package com.example.intervaltimer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.intervaltimer.shared.model.SignalType
import com.example.intervaltimer.shared.model.SoundPattern
import com.example.intervaltimer.shared.model.TimerConfig
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.shared.model.VibrationPattern
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TimerViewModel,
    onOpenSettings: () -> Unit,
    showBatteryHint: Boolean,
    onRequestBatteryExemption: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRunning = uiState.runState == TimerRunState.RUNNING
    val isPaused = uiState.runState == TimerRunState.PAUSED
    val isEditable = !isRunning // interval/signal can only be changed while stopped/paused... actually spec: block only while RUNNING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interval Timer") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Beállítások")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            if (showBatteryHint) {
                BatteryOptimizationHint(onRequestBatteryExemption)
            }

            StatusBadge(uiState.runState)

            NextSignalDisplay(remainingMillis = uiState.remainingMillis, runState = uiState.runState)

            IntervalPicker(
                intervalMillis = uiState.config.intervalMillis,
                enabled = isEditable,
                onIntervalChange = { newMillis ->
                    viewModel.updateConfig(uiState.config.copy(intervalMillis = TimerConfig.coerceInterval(newMillis)))
                }
            )

            PresetRow(enabled = isEditable) { presetMillis ->
                viewModel.updateConfig(uiState.config.copy(intervalMillis = presetMillis))
            }

            SignalTypeSelector(
                selected = uiState.config.signalType,
                enabled = isEditable,
                onSelect = { viewModel.updateConfig(uiState.config.copy(signalType = it)) }
            )

            if (uiState.config.signalType == SignalType.SOUND_ONLY || uiState.config.signalType == SignalType.SOUND_AND_VIBRATION) {
                SoundPatternSelector(
                    selected = uiState.config.soundPattern,
                    enabled = isEditable,
                    onSelect = { viewModel.updateConfig(uiState.config.copy(soundPattern = it)) }
                )
            }

            if (uiState.config.signalType == SignalType.VIBRATION_ONLY || uiState.config.signalType == SignalType.SOUND_AND_VIBRATION) {
                VibrationPatternSelector(
                    selected = uiState.config.vibrationPattern,
                    enabled = isEditable,
                    onSelect = { viewModel.updateConfig(uiState.config.copy(vibrationPattern = it)) }
                )
            }

            OutlinedButton(onClick = { viewModel.testSignal() }, modifier = Modifier.fillMaxWidth()) {
                Text("TESZT JELZÉS")
            }

            ControlButtons(
                runState = uiState.runState,
                onStart = { viewModel.start() },
                onPause = { viewModel.pause() },
                onStop = { viewModel.stop() }
            )
        }
    }
}

@Composable
private fun BatteryOptimizationHint(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Az időzítés megbízható háttérben történő működéséhez engedélyezd, hogy az " +
                    "alkalmazás ne legyen akkumulátor-optimalizálva.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onRequest) { Text("Engedélyezés") }
        }
    }
}

@Composable
private fun StatusBadge(runState: TimerRunState) {
    val (label, color) = when (runState) {
        TimerRunState.RUNNING -> "AKTÍV" to com.example.intervaltimer.ui.theme.StateActiveGreen
        TimerRunState.PAUSED -> "SZÜNETEL" to com.example.intervaltimer.ui.theme.StatePausedAmber
        else -> "LEÁLLÍTVA" to com.example.intervaltimer.ui.theme.StateStoppedGray
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.large) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun NextSignalDisplay(remainingMillis: Long?, runState: TimerRunState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Következő jelzés", style = MaterialTheme.typography.labelLarge)
        Text(
            text = remainingMillis?.let { formatMillis(it) } ?: "--:--",
            style = MaterialTheme.typography.displayLarge
        )
    }
}

@Composable
private fun IntervalPicker(intervalMillis: Long, enabled: Boolean, onIntervalChange: (Long) -> Unit) {
    val totalSeconds = intervalMillis / 1000
    var hours by remember(intervalMillis) { mutableStateOf((totalSeconds / 3600).toInt()) }
    var minutes by remember(intervalMillis) { mutableStateOf(((totalSeconds % 3600) / 60).toInt()) }
    var seconds by remember(intervalMillis) { mutableStateOf((totalSeconds % 60).toInt()) }

    fun commit() {
        val newMillis = (hours * 3600L + minutes * 60L + seconds) * 1000L
        onIntervalChange(newMillis)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Intervallum", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField("óra", hours, 0..24, enabled) { hours = it; commit() }
            Text(":", style = MaterialTheme.typography.headlineMedium)
            NumberField("perc", minutes, 0..59, enabled) { minutes = it; commit() }
            Text(":", style = MaterialTheme.typography.headlineMedium)
            NumberField("mp", seconds, 0..59, enabled) { seconds = it; commit() }
        }
        if (intervalMillis < TimerConfig.MIN_INTERVAL_MILLIS) {
            Text(
                "Minimum 1 másodperc. Nagyon rövid intervallumoknál a pontosság a rendszer " +
                    "energiagazdálkodása miatt korlátozott lehet.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, enabled: Boolean, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: 0
                onChange(parsed.coerceIn(range))
            },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.width(72.dp)
        )
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private val presets = listOf(
    "30 mp" to 30_000L,
    "1 perc" to 60_000L,
    "2 perc" to 120_000L,
    "5 perc" to 300_000L,
    "10 perc" to 600_000L,
    "15 perc" to 900_000L,
    "30 perc" to 1_800_000L,
    "60 perc" to 3_600_000L,
)

@Composable
private fun PresetRow(enabled: Boolean, onSelect: (Long) -> Unit) {
    FlowRowLike {
        presets.forEach { (label, millis) ->
            OutlinedButton(onClick = { onSelect(millis) }, enabled = enabled) { Text(label) }
        }
    }
}

/** Tiny wrap-layout helper so we don't need the experimental FlowRow API. */
@Composable
private fun FlowRowLike(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .horizontalScrollFade(),
    ) { content() }
}

private fun Modifier.horizontalScrollFade(): Modifier = this // placeholder for potential fade/scroll styling

@Composable
private fun SignalTypeSelector(selected: SignalType, enabled: Boolean, onSelect: (SignalType) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Jelzés", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentButton("Hang", selected == SignalType.SOUND_ONLY, enabled) { onSelect(SignalType.SOUND_ONLY) }
            SegmentButton("Rezgés", selected == SignalType.VIBRATION_ONLY, enabled) { onSelect(SignalType.VIBRATION_ONLY) }
            SegmentButton("Mindkettő", selected == SignalType.SOUND_AND_VIBRATION, enabled) { onSelect(SignalType.SOUND_AND_VIBRATION) }
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

@Composable
private fun SoundPatternSelector(selected: SoundPattern, enabled: Boolean, onSelect: (SoundPattern) -> Unit) {
    val labels = mapOf(
        SoundPattern.SHORT_BEEP to "Rövid beep",
        SoundPattern.DOUBLE_BEEP to "Dupla beep",
        SoundPattern.SHORT_CHIME to "Csippanás",
        SoundPattern.LONG_TONE to "Hosszabb hang",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Hang", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (pattern, label) -> SegmentButton(label, selected == pattern, enabled) { onSelect(pattern) } }
        }
    }
}

@Composable
private fun VibrationPatternSelector(selected: VibrationPattern, enabled: Boolean, onSelect: (VibrationPattern) -> Unit) {
    val labels = mapOf(
        VibrationPattern.SHORT to "Rövid",
        VibrationPattern.MEDIUM to "Közepes",
        VibrationPattern.LONG to "Hosszú",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Rezgés", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { (pattern, label) -> SegmentButton(label, selected == pattern, enabled) { onSelect(pattern) } }
        }
    }
}

@Composable
private fun ControlButtons(runState: TimerRunState, onStart: () -> Unit, onPause: () -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (runState) {
            TimerRunState.RUNNING -> {
                Button(onClick = onPause) { Text("PAUSE") }
                OutlinedButton(onClick = onStop) { Text("STOP") }
            }
            TimerRunState.PAUSED -> {
                Button(onClick = onStart) { Text("FOLYTATÁS") }
                OutlinedButton(onClick = onStop) { Text("STOP") }
            }
            else -> {
                Button(onClick = onStart) { Text("START") }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
