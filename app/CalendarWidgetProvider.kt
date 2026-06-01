package com.deviceb.inkcapture

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class CalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DeviceBWidgetUtil.updateCalendarWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == DeviceBWidgetUtil.ACTION_REFRESH_CALENDAR) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            DeviceBWidgetUtil.updateCalendarWidgets(context, mgr, ids)
        }
    }
}
