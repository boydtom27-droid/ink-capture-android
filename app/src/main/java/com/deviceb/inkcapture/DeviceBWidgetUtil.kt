package com.deviceb.inkcapture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object DeviceBWidgetUtil {
    const val RELAY_BASE = "https://device-b-relay.onrender.com"
    const val RELAY_TOKEN = "abc123xyz789"

    const val ACTION_REFRESH_TASKS = "com.deviceb.inkcapture.REFRESH_TASKS"
    const val ACTION_REFRESH_CALENDAR = "com.deviceb.inkcapture.REFRESH_CALENDAR"
    const val ACTION_REFRESH_SCRIBBLE = "com.deviceb.inkcapture.REFRESH_SCRIBBLE"

    fun updateAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        updateTaskWidgets(context, mgr, mgr.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java)))
        updateCalendarWidgets(context, mgr, mgr.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java)))
        updateScribbleWidgets(context, mgr, mgr.getAppWidgetIds(ComponentName(context, ScribbleWidgetProvider::class.java)))
    }

    fun updateTaskWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        for (id in ids) {
            val loading = RemoteViews(context.packageName, R.layout.widget_tasks)
            loading.setTextViewText(R.id.widget_schedule, "Refreshing…")
            loading.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, ACTION_REFRESH_TASKS, id))
            manager.updateAppWidget(id, loading)
        }
        thread {
            val result = runCatching { fetchText("$RELAY_BASE/api/widget/tasks?token=$RELAY_TOKEN") }
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, ACTION_REFRESH_TASKS, id))
                if (result.isSuccess) {
                    fillTasksView(views, result.getOrThrow())
                } else {
                    views.setTextViewText(R.id.widget_title, "Device B")
                    views.setTextViewText(R.id.widget_schedule, "Refresh failed")
                    views.setTextViewText(R.id.widget_task_1, result.exceptionOrNull()?.javaClass?.simpleName ?: "Error")
                    clearTaskLines(views, 2)
                    views.setTextViewText(R.id.widget_footer, timestamp())
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    fun updateCalendarWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateImageWidgets(context, manager, ids, CalendarWidgetProvider::class.java, ACTION_REFRESH_CALENDAR, "Calendar", "$RELAY_BASE/api/widget/calendar.png?token=$RELAY_TOKEN&w=1000&h=800")
    }

    fun updateScribbleWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateImageWidgets(context, manager, ids, ScribbleWidgetProvider::class.java, ACTION_REFRESH_SCRIBBLE, "Scribble", "$RELAY_BASE/api/widget/scribble.png?token=$RELAY_TOKEN&w=1000&h=700")
    }

    private fun updateImageWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, cls: Class<*>, action: String, title: String, url: String) {
        if (ids.isEmpty()) return
        for (id in ids) {
            val loading = RemoteViews(context.packageName, R.layout.widget_image)
            loading.setTextViewText(R.id.widget_image_title, title)
            loading.setTextViewText(R.id.widget_image_footer, "Refreshing…")
            loading.setOnClickPendingIntent(R.id.widget_image_refresh, refreshIntent(context, action, id))
            manager.updateAppWidget(id, loading)
        }
        thread {
            val result = runCatching { fetchBitmap(url) }
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_image)
                views.setTextViewText(R.id.widget_image_title, title)
                views.setOnClickPendingIntent(R.id.widget_image_refresh, refreshIntent(context, action, id))
                if (result.isSuccess) {
                    views.setImageViewBitmap(R.id.widget_image, result.getOrThrow())
                    views.setTextViewText(R.id.widget_image_footer, "Updated ${timestamp()}")
                } else {
                    views.setTextViewText(R.id.widget_image_footer, "Refresh failed: ${result.exceptionOrNull()?.javaClass?.simpleName ?: "Error"}")
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun fillTasksView(views: RemoteViews, json: String) {
        val obj = JSONObject(json)
        val updated = obj.optString("updated", timestamp())
        val date = obj.optString("date", "")
        val current = obj.optString("current_schedule", "")
        val next = obj.optString("next_schedule", "")
        views.setTextViewText(R.id.widget_title, "Device B  $date")
        val sched = when {
            current.isNotBlank() -> "NOW: $current"
            next.isNotBlank() -> "NEXT: $next"
            else -> "No active schedule"
        }
        views.setTextViewText(R.id.widget_schedule, sched.take(55))
        val arr = obj.optJSONArray("tasks")
        val ids = intArrayOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3, R.id.widget_task_4, R.id.widget_task_5)
        for (i in ids.indices) {
            if (arr != null && i < arr.length()) {
                val t = arr.getJSONObject(i)
                val urgent = if (t.optBoolean("urgent")) "! " else ""
                val loc = t.optString("location", "")
                val line = "${i + 1}. $urgent${t.optString("text", "")}${if (loc.isNotBlank()) " — $loc" else ""}"
                views.setTextViewText(ids[i], line.take(58))
            } else {
                views.setTextViewText(ids[i], "")
            }
        }
        views.setTextViewText(R.id.widget_footer, "Updated $updated")
    }

    private fun clearTaskLines(views: RemoteViews, startIndex: Int) {
        val ids = intArrayOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3, R.id.widget_task_4, R.id.widget_task_5)
        for (i in (startIndex - 1).coerceAtLeast(0) until ids.size) views.setTextViewText(ids[i], "")
    }

    private fun fetchText(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
        }
        return try {
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw RuntimeException("HTTP $code $body")
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchBitmap(urlStr: String): Bitmap {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 20000
            requestMethod = "GET"
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            BitmapFactory.decodeStream(conn.inputStream) ?: throw RuntimeException("decode_failed")
        } finally {
            conn.disconnect()
        }
    }

    private fun refreshIntent(context: Context, action: String, widgetId: Int): PendingIntent {
        val cls = when (action) {
            ACTION_REFRESH_CALENDAR -> CalendarWidgetProvider::class.java
            ACTION_REFRESH_SCRIBBLE -> ScribbleWidgetProvider::class.java
            else -> TaskWidgetProvider::class.java
        }
        val intent = Intent(context, cls).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, widgetId + action.hashCode(), intent, flags)
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm", Locale.UK).format(Date())
}
