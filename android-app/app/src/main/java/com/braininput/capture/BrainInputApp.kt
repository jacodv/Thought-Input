package com.braininput.capture

import android.app.Application
import com.braininput.capture.util.CaptureLog

class BrainInputApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CaptureLog.ui("Brain Input application started")
    }
}
