package com.thoughtinput.capture.tile

import android.content.Intent
import android.service.quicksettings.TileService
import com.thoughtinput.capture.CaptureActivity
import com.thoughtinput.capture.util.CaptureLog

class CaptureTileService : TileService() {

    override fun onClick() {
        super.onClick()
        CaptureLog.tile("Quick Settings tile tapped")

        val intent = Intent(this, CaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        CaptureLog.tile("Tile listening started")
    }

    override fun onStopListening() {
        super.onStopListening()
        CaptureLog.tile("Tile listening stopped")
    }

    override fun onTileAdded() {
        super.onTileAdded()
        CaptureLog.tile("Tile added to Quick Settings")
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        CaptureLog.tile("Tile removed from Quick Settings")
    }
}
