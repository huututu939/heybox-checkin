package com.heybox.checkin

import android.app.Application
import com.heybox.checkin.data.Diagnostics

class HeyboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashHandler(this)
    }
}
