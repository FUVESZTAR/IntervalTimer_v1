package com.example.intervaltimer.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.intervaltimer.wear.presentation.WearApp

/**
 * Entry point for the watch app. Works fully standalone: installing this .apk on the
 * Galaxy Watch5 (via Play Store / sideload / Wear app installer) and opening it lets the
 * user configure and run an interval timer entirely on-device — no paired phone required,
 * no network, no Bluetooth. If a companion phone with the app IS connected, phone-driven
 * sessions are additionally mirrored here via PhoneListenerService (mode B), but that is
 * independent of this screen's own start/pause/stop controls (mode C).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: WatchViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* degrade gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            WearApp(viewModel = viewModel)
        }
    }
}
