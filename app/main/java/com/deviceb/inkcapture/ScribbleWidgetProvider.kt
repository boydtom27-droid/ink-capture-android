package com.deviceb.inkcapture

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class ScribbleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DeviceBWidgetUtil.updateScribbleWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == DeviceBWidgetUtil.ACTION_REFRESH_SCRIBBLE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ScribbleWidgetProvider::class.java))
            DeviceBWidgetUtil.updateScribbleWidgets(context, mgr, ids)
        }
    }
}
