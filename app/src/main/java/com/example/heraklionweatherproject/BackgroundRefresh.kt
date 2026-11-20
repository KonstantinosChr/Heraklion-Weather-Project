package com.example.heraklionweatherproject

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Schedules the periodic refresh only while something needs it (a widget or notifications). */
object BackgroundRefresh {
    private const val WORK = "heraklion-weather-refresh"

    fun sync(context: Context) {
        val work = WorkManager.getInstance(context.applicationContext)
        if (AppPrefs.notificationsEnabled(context) || WeatherWidgetProvider.hasWidgets(context)) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK)
        }
    }
}

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val snapshot = WeatherRepository.refresh(applicationContext)
        if (AppPrefs.notificationsEnabled(applicationContext)) WeatherNotifications.evaluate(applicationContext, snapshot)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}

/** Decides which notifications to send. Each kind is sent at most once per day. */
object WeatherNotifications {
    private const val CHANNEL = "weather"

    fun evaluate(context: Context, snapshot: WeatherSnapshot, nowMillis: Long = System.currentTimeMillis()) {
        val english = AppPrefs.english(context)
        val fahrenheit = AppPrefs.fahrenheit(context)
        val data = snapshot.forecast
        val today = athensTime("yyyy-MM-dd", Date(nowMillis))
        val hour = athensTime("HH", Date(nowMillis)).toInt()
        val daily = data.daily
        val d = daily.time.indexOf(today)
        if (d < 0) return
        fun t(el: String, en: String) = if (english) en else el

        if (hour in 7..10) send(context, "morning", today, 1, t("Καλημέρα από το Ηράκλειο ☀️", "Good morning from Heraklion ☀️"),
            dailySummary(data, fahrenheit, english, today))

        if (hour >= 18 && d + 1 < daily.count) {
            val rain = daily.precipitation_probability_max.getOrNull(d + 1) ?: 0
            if (rain >= 60) send(context, "rain", daily.time[d + 1], 2, t("Αύριο βρέχει 🌧️", "Rain tomorrow 🌧️"),
                t("Πιθανότητα βροχής $rain% αύριο. Μην ξεχάσεις ομπρέλα!", "$rain% chance of rain tomorrow. Don't forget an umbrella!"))
        }

        if (hour in 8..13) daily.uv_index_max.getOrNull(d)?.takeIf { it >= 8 }?.let { uv ->
            send(context, "uv", today, 3, t("Υψηλός δείκτης UV 🕶️", "High UV index 🕶️"),
                t("UV ${uv.roundToInt()} σήμερα. Αντηλιακό και σκιά το μεσημέρι.", "UV ${uv.roundToInt()} today. Sunscreen and shade at midday."))
        }

        daily.wind_gusts_10m_max.getOrNull(d)?.takeIf { it >= 60 }?.let { gust ->
            send(context, "wind", today, 4, t("Δυνατός αέρας 💨", "Strong wind 💨"),
                t("Ριπές έως ${gust.roundToInt()} km/h σήμερα (${beaufort(gust)} Μποφόρ).", "Gusts up to ${gust.roundToInt()} km/h today (force ${beaufort(gust)})."))
        }

        daily.temperature_2m_max[d].takeIf { it >= 37 }?.let { max ->
            send(context, "heat", today, 5, t("Καύσωνας 🔥", "Heatwave 🔥"),
                t("Έως ${temperature(max, fahrenheit)} σήμερα. Πίνε νερό και απόφυγε τον ήλιο 12:00–17:00.",
                    "Up to ${temperature(max, fahrenheit)} today. Drink water and avoid the sun 12:00–17:00."))
        }
    }

    private fun send(context: Context, kind: String, key: String, id: Int, title: String, text: String) {
        val sent = context.getSharedPreferences("notifications", 0)
        if (sent.getString(kind, null) == key) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            sent.edit().putString(kind, key).apply()
        } catch (_: SecurityException) { /* permission revoked meanwhile */ }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val english = AppPrefs.english(context)
        val channel = NotificationChannel(CHANNEL, if (english) "Weather" else "Καιρός", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = if (english) "Heraklion weather alerts" else "Ειδοποιήσεις καιρού Ηρακλείου" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
