package com.example.heraklionweatherproject

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MarineResponse(val current: MarineCurrent = MarineCurrent(), val daily: MarineDaily = MarineDaily())
@Serializable
data class MarineCurrent(
    val wave_height: Double? = null, val wave_direction: Double? = null,
    val wave_period: Double? = null, val sea_surface_temperature: Double? = null
)
@Serializable
data class MarineDaily(val time: List<String> = emptyList(), val wave_height_max: List<Double?> = emptyList())

@Serializable
data class AirQualityResponse(val current: AirCurrent = AirCurrent(), val hourly: AirHourly = AirHourly())
@Serializable
data class AirCurrent(
    val european_aqi: Double? = null, val pm10: Double? = null,
    val pm2_5: Double? = null, val dust: Double? = null
)
@Serializable
data class AirHourly(val time: List<String> = emptyList(), val dust: List<Double?> = emptyList())

@Serializable
data class ComparisonResponse(val hourly: ComparisonHourly = ComparisonHourly(), val daily: ComparisonDaily = ComparisonDaily())
@Serializable
data class ComparisonHourly(val time: List<String> = emptyList(), val temperature_2m: List<Double?> = emptyList())
@Serializable
data class ComparisonDaily(
    val time: List<String> = emptyList(), val temperature_2m_max: List<Double?> = emptyList(),
    val temperature_2m_min: List<Double?> = emptyList(), val daylight_duration: List<Double?> = emptyList()
)

@Serializable
data class HistoryResponse(val daily: HistoryDaily = HistoryDaily())
@Serializable
data class HistoryDaily(
    val time: List<String> = emptyList(), val weather_code: List<Int?> = emptyList(),
    val temperature_2m_max: List<Double?> = emptyList(), val temperature_2m_min: List<Double?> = emptyList(),
    val precipitation_sum: List<Double?> = emptyList()
)

/** Optional data sources. Each one may be missing without breaking the main forecast. */
@Serializable
data class WeatherExtras(
    val marine: MarineResponse? = null, val air: AirQualityResponse? = null,
    val comparison: ComparisonResponse? = null, val history: HistoryResponse? = null
)

data class WeatherSnapshot(val forecast: WeatherResponse, val extras: WeatherExtras, val updatedAt: Long)

/** Single place for fetching and caching, shared by the screen, the widget and the background worker. */
object WeatherRepository {
    private const val PREFS = "weather"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, 0)

    fun cachedForecast(context: Context): WeatherResponse? = runCatching {
        NetworkModule.json.decodeFromString<WeatherResponse>(prefs(context).getString("cache", null) ?: "")
    }.getOrNull()

    fun cachedExtras(context: Context): WeatherExtras = runCatching {
        NetworkModule.json.decodeFromString<WeatherExtras>(prefs(context).getString("extras", null) ?: "")
    }.getOrDefault(WeatherExtras())

    fun updatedAt(context: Context): Long = prefs(context).getLong("updated", 0L)

    suspend fun refresh(context: Context): WeatherSnapshot = coroutineScope {
        val api = NetworkModule.api
        val forecast = async { api.getHeraklionWeather() }
        val marine = async { optional { api.getMarine() } }
        val air = async { optional { api.getAirQuality() } }
        val comparison = async { optional { api.getComparison() } }
        val history = async { optional { api.getHistory(sameDayLastYear()) } }
        val data = forecast.await()
        val previous = withContext(Dispatchers.IO) { cachedExtras(context) }
        // A failed optional source keeps its last good value instead of disappearing.
        val extras = WeatherExtras(
            marine = marine.await() ?: previous.marine,
            air = air.await() ?: previous.air,
            comparison = comparison.await() ?: previous.comparison,
            history = history.await() ?: previous.history
        )
        val updated = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            prefs(context).edit()
                .putString("cache", NetworkModule.json.encodeToString(data))
                .putString("extras", NetworkModule.json.encodeToString(extras))
                .putLong("updated", updated)
                .apply()
        }
        WeatherWidgetProvider.updateAll(context)
        WeatherSnapshot(data, extras, updated)
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** Today's date one year ago in Greek time; 29 February falls back to 28 February. */
fun sameDayLastYear(now: Long = System.currentTimeMillis()): String {
    val zone = TimeZone.getTimeZone(ATHENS)
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = now; add(Calendar.YEAR, -1) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(calendar.time)
}
