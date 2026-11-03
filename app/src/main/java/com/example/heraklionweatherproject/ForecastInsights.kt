package com.example.heraklionweatherproject

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** All temperatures remain Celsius until formatted for display. */
data class TemperatureSample(val time: String, val timestamp: Long, val celsius: Double)

fun forecastTimestamp(value: String): Long? = runCatching {
    require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}").matches(value))
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Europe/Athens")
        isLenient = false
    }.parse(value)?.time
}.getOrNull()

fun temperatureSamples(hourly: Hourly, start: String): List<TemperatureSample> {
    val startMillis = forecastTimestamp(start) ?: return emptyList()
    val endMillis = startMillis + 24 * 60 * 60 * 1000L
    return hourly.time.mapIndexedNotNull { index, time ->
        val millis = forecastTimestamp(time) ?: return@mapIndexedNotNull null
        val value = hourly.temperature_2m.getOrNull(index) ?: return@mapIndexedNotNull null
        if (millis < startMillis || millis >= endMillis || !value.isFinite()) null
        else TemperatureSample(time, millis, value)
    }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
}

fun forecastCondition(code: Int, english: Boolean): String {
    if (!english) return weatherDescription(code)
    return when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        80, 81, 82 -> "Rain showers"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Unknown conditions"
    }
}

/** Deterministic summary: no invented advice or unsupported time-of-day predictions. */
fun dailySummary(data: WeatherResponse, fahrenheit: Boolean, english: Boolean,
                 date: String = athensTime("yyyy-MM-dd")): String {
    val daily = data.daily
    val i = daily.time.take(daily.count).indexOf(date)
    if (i < 0) return if (english) "No forecast available for today. Refresh to get the latest data."
    else "Δεν υπάρχει διαθέσιμη πρόβλεψη για σήμερα. Κάνε ανανέωση για νεότερα δεδομένα."
    val condition = forecastCondition(daily.weather_code[i], english)
    val min = temperature(daily.temperature_2m_min[i], fahrenheit)
    val max = temperature(daily.temperature_2m_max[i], fahrenheit)
    val rain = daily.precipitation_probability_max.getOrNull(i)?.takeIf { it in 0..100 }
    val base = if (english) "$condition today, with temperatures from $min to $max."
    else "Σήμερα: $condition, με θερμοκρασία από $min έως $max."
    return base + if (rain == null) "" else if (english) " Maximum chance of rain: $rain%."
    else " Μέγιστη πιθανότητα βροχής: $rain%."
}
