package com.example.intervaltimer

import android.app.Application
import com.example.intervaltimer.notification.NotificationHelper

class IntervalTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cheap, one-time setup. No background work, no analytics, no network init.
        NotificationHelper.ensureChannel(this)
    }
}
