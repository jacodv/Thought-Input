package com.thoughtinput.capture

import android.app.Application
import com.thoughtinput.capture.util.CaptureLog

class ThoughtInputApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CaptureLog.ui("Thought Input application started")
    }
}
