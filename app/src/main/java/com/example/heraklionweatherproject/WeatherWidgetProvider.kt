package com.example.heraklionweatherproject

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home screen widget. It shows the cached forecast; the app and the background worker keep it fresh. */
class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    override fun onEnabled(context: Context) = BackgroundRefresh.sync(context)
    override fun onDisabled(context: Context) = BackgroundRefresh.sync(context)

    companion object {
        fun hasWidgets(context: Context): Boolean = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java)).isNotEmpty()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val english = AppPrefs.english(context)
            val fahrenheit = AppPrefs.fahrenheit(context)
            val data = WeatherRepository.cachedForecast(context)
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            views.setTextViewText(R.id.widget_city, if (english) "HERAKLION" else "ΗΡΑΚΛΕΙΟ")
            if (data == null) {
                views.setTextViewText(R.id.widget_temp, "—")
                views.setTextViewText(R.id.widget_desc, if (english) "Open the app to load" else "Άνοιξε την εφαρμογή")
                views.setTextViewText(R.id.widget_range, "")
            } else {
                val c = data.current
                views.setTextViewText(R.id.widget_icon, getWeatherIcon(c.weather_code, c.is_day == 1))
                views.setTextViewText(R.id.widget_temp, temperature(c.temperature_2m, fahrenheit))
                views.setTextViewText(R.id.widget_desc, forecastCondition(c.weather_code, english))
                val i = data.daily.time.indexOf(athensTime("yyyy-MM-dd"))
                val range = if (i in 0 until data.daily.count)
                    "↑ ${temperature(data.daily.temperature_2m_max[i], fahrenheit)}  ↓ ${temperature(data.daily.temperature_2m_min[i], fahrenheit)}"
                else ""
                views.setTextViewText(R.id.widget_range, range)
                val updated = WeatherRepository.updatedAt(context)
                if (updated > 0) views.setTextViewText(R.id.widget_city,
                    (if (english) "HERAKLION" else "ΗΡΑΚΛΕΙΟ") + " · " + athensTime("HH:mm", java.util.Date(updated)))
            }
            val open = PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
