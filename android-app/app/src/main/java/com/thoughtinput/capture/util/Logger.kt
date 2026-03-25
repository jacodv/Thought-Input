package com.thoughtinput.capture.util

import android.util.Log

object CaptureLog {
    private const val TAG = "ThoughtInput"

    fun ui(message: String) = Log.i("$TAG.UI", message)
    fun network(message: String) = Log.i("$TAG.Network", message)
    fun speech(message: String) = Log.i("$TAG.Speech", message)
    fun store(message: String) = Log.i("$TAG.Store", message)
    fun tile(message: String) = Log.i("$TAG.Tile", message)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG.$tag", message, throwable)
    }
}
