package com.thoughtinput.capture.util

import android.util.Log

object CaptureLog {
    private const val TAG = "ThoughtInput"

    fun ui(message: String) = Log.d("$TAG.UI", message)
    fun network(message: String) = Log.d("$TAG.Network", message)
    fun speech(message: String) = Log.d("$TAG.Speech", message)
    fun store(message: String) = Log.d("$TAG.Store", message)
    fun tile(message: String) = Log.d("$TAG.Tile", message)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG.$tag", message, throwable)
    }
}
