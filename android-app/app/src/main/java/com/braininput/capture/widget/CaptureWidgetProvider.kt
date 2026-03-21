package com.braininput.capture.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.braininput.capture.CaptureActivity
import com.braininput.capture.R
import com.braininput.capture.util.CaptureLog

class CaptureWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_capture_bar)

            // Tap bar (icon or text) → open capture for typing
            val captureIntent = Intent(context, CaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val capturePending = PendingIntent.getActivity(
                context, 0, captureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_icon, capturePending)
            views.setOnClickPendingIntent(R.id.widget_hint, capturePending)

            // Tap mic → open capture in voice mode
            val voiceIntent = Intent(context, CaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("start_voice", true)
            }
            val voicePending = PendingIntent.getActivity(
                context, 1, voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_mic, voicePending)

            appWidgetManager.updateAppWidget(widgetId, views)
            CaptureLog.ui("Widget $widgetId updated")
        }
    }
}
