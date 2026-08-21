package com.example.intervaltimer.wear.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.intervaltimer.shared.model.TimerRunState
import com.example.intervaltimer.wear.WatchViewModel
import java.util.concurrent.TimeUnit

/**
 * Watch UI kept deliberately shallow: state + next-signal + interval presets + START/PAUSE/STOP
 * all reachable within one scroll, per spec §39's "few seconds to start" goal — adapted for
 * a round, small screen. No settings screen on the watch; a standalone user is expected to
 * just pick a preset and go.
 */
@Composable
fun WearApp(viewModel: WatchViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)
        ) {
            item { StatusText(uiState.runState) }
            item {
                val timerLabel = if (uiState.nextTimerIndex == 0) "T1" else "T2"
                Text(
                    text = "$timerLabel  ${uiState.remainingMillis?.let { formatMillis(it) } ?: formatMillis(uiState.config.intervalMillis)}",
                    style = MaterialTheme.typography.display2
                )
            }
            item { Spacer(Modifier.height(4.dp)) }

            if (uiState.runState != TimerRunState.RUNNING) {
                item {
                    Text("Timer 1", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                }
                items(presets) { (label, millis) ->
                    Chip(
                        onClick = { viewModel.updateInterval(millis) },
                        label = { Text(label) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        Text("Timer 2", style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold)
                    }
                }
                items(presets) { (label, millis) ->
                    Chip(
                        onClick = { viewModel.updateInterval2(millis) },
                        label = { Text(label) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                when (uiState.runState) {
                    TimerRunState.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.pause() }) { Text("Pause") }
                        Button(onClick = { viewModel.stop() }, colors = ButtonDefaults.secondaryButtonColors()) { Text("Stop") }
                    }
                    TimerRunState.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.start() }) { Text("Folytat") }
                        Button(onClick = { viewModel.stop() }, colors = ButtonDefaults.secondaryButtonColors()) { Text("Stop") }
                    }
                    else -> Button(onClick = { viewModel.start() }) { Text("Start") }
                }
            }

            item {
                Chip(
                    onClick = { viewModel.testSignal() },
                    label = { Text("Teszt jelzés") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusText(runState: TimerRunState) {
    val label = when (runState) {
        TimerRunState.RUNNING -> "AKTÍV"
        TimerRunState.PAUSED -> "SZÜNETEL"
        else -> "LEÁLLÍTVA"
    }
    Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.caption1)
}

private val presets = listOf(
    "30 mp" to 30_000L,
    "1 perc" to 60_000L,
    "5 perc" to 300_000L,
    "10 perc" to 600_000L,
    "30 perc" to 1_800_000L,
)

private fun formatMillis(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
