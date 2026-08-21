package com.example.intervaltimer.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.intervaltimer.ui.theme.IntervalTimerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way; UI degrades gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            IntervalTimerTheme {
                val showSettings = remember { mutableStateOf(false) }
                val batteryExemptionNeeded = remember { mutableStateOf(isBatteryOptimizationBlocking()) }

                if (showSettings.value) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showSettings.value = false }
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings.value = true },
                        showBatteryHint = batteryExemptionNeeded.value,
                        onRequestBatteryExemption = { requestIgnoreBatteryOptimization() }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * We only surface the battery-optimization hint if the OS reports this app IS
     * currently subject to optimization — never ask unconditionally (spec §8: "Ne kérj
     * indokolatlanul akkumulátor-optimalizálás alóli kivételt").
     */
    private fun isBatteryOptimizationBlocking(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
