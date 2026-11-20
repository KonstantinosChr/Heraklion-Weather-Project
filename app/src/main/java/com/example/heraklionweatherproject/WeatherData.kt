package com.example.heraklionweatherproject

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Serializable
data class WeatherResponse(val current: Current, val daily: Daily, val hourly: Hourly = Hourly())
@Serializable
data class Current(
    val temperature_2m: Double, val relative_humidity_2m: Int,
    val apparent_temperature: Double, val weather_code: Int, val wind_speed_10m: Double,
    val time: String = "", val is_day: Int = 1,
    val wind_gusts_10m: Double? = null, val wind_direction_10m: Double? = null,
    val pressure_msl: Double? = null, val visibility: Double? = null,
    val cloud_cover: Double? = null, val dew_point_2m: Double? = null
)
@Serializable
data class Daily(
    val time: List<String>, val weather_code: List<Int>,
    val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>,
    val sunrise: List<String?> = emptyList(), val sunset: List<String?> = emptyList(),
    val uv_index_max: List<Double?> = emptyList(),
    val precipitation_probability_max: List<Int?> = emptyList(),
    val wind_speed_10m_max: List<Double?> = emptyList(), val wind_gusts_10m_max: List<Double?> = emptyList(),
    val wind_direction_10m_dominant: List<Double?> = emptyList(), val daylight_duration: List<Double?> = emptyList()
) {
    val count: Int get() = minOf(time.size, weather_code.size, temperature_2m_max.size, temperature_2m_min.size)
}
@Serializable
data class Hourly(
    val time: List<String> = emptyList(), val temperature_2m: List<Double?> = emptyList(),
    val weather_code: List<Int?> = emptyList(), val precipitation_probability: List<Int?> = emptyList(),
    val is_day: List<Int?> = emptyList(), val wind_speed_10m: List<Double?> = emptyList(),
    val uv_index: List<Double?> = emptyList()
) {
    fun upcoming(now: String): List<Int> = time.indices.filter {
        time[it] >= now && temperature_2m.getOrNull(it) != null && weather_code.getOrNull(it) != null
    }.take(24)
}
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getHeraklionWeather(
        @Query("latitude") lat: Double = HERAKLION_LAT,
        @Query("longitude") lon: Double = HERAKLION_LON,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code," +
                "wind_speed_10m,is_day,wind_gusts_10m,wind_direction_10m,pressure_msl,visibility,cloud_cover,dew_point_2m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max," +
                "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant,daylight_duration",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability,is_day,wind_speed_10m,uv_index",
        @Query("forecast_days") days: Int = FORECAST_DAYS,
        @Query("timezone") timezone: String = ATHENS
    ): WeatherResponse

    /** Same forecast endpoint, but starting yesterday, for the "compared with yesterday" card. */
    @GET("v1/forecast")
    suspend fun getComparison(
        @Query("latitude") lat: Double = HERAKLION_LAT,
        @Query("longitude") lon: Double = HERAKLION_LON,
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,daylight_duration",
        @Query("past_days") pastDays: Int = 1,
        @Query("forecast_days") days: Int = 1,
        @Query("timezone") timezone: String = ATHENS
    ): ComparisonResponse

    /** The marine model needs a sea grid cell, so the point is just off the Heraklion coast. */
    @GET("https://marine-api.open-meteo.com/v1/marine")
    suspend fun getMarine(
        @Query("latitude") lat: Double = 35.36,
        @Query("longitude") lon: Double = HERAKLION_LON,
        @Query("current") current: String = "wave_height,wave_direction,wave_period,sea_surface_temperature",
        @Query("daily") daily: String = "wave_height_max",
        @Query("timezone") timezone: String = ATHENS
    ): MarineResponse

    @GET("https://air-quality-api.open-meteo.com/v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double = HERAKLION_LAT,
        @Query("longitude") lon: Double = HERAKLION_LON,
        @Query("current") current: String = "european_aqi,pm10,pm2_5,dust",
        @Query("hourly") hourly: String = "dust",
        @Query("forecast_days") days: Int = 2,
        @Query("timezone") timezone: String = ATHENS
    ): AirQualityResponse

    @GET("https://archive-api.open-meteo.com/v1/archive")
    suspend fun getHistory(
        @Query("start_date") start: String,
        @Query("end_date") end: String = start,
        @Query("latitude") lat: Double = HERAKLION_LAT,
        @Query("longitude") lon: Double = HERAKLION_LON,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum",
        @Query("timezone") timezone: String = ATHENS
    ): HistoryResponse
}
const val HERAKLION_LAT = 35.3387
const val HERAKLION_LON = 25.1442
const val ATHENS = "Europe/Athens"
const val FORECAST_DAYS = 14
object NetworkModule {
    val json = Json { ignoreUnknownKeys = true }
    val api: WeatherApi by lazy {
        Retrofit.Builder().baseUrl("https://api.open-meteo.com/")
            .client(OkHttpClient.Builder().callTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(WeatherApi::class.java)
    }
}
data class WeatherUiState(
    val data: WeatherResponse? = null, val loading: Boolean = true,
    val error: String? = null, val updatedAt: Long = 0L, val cached: Boolean = false,
    val extras: WeatherExtras = WeatherExtras()
)
class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState = _uiState.asStateFlow()
    private var fetching = false
    init {
        viewModelScope.launch {
            val (cached, extras) = withContext(Dispatchers.IO) {
                WeatherRepository.cachedForecast(app) to WeatherRepository.cachedExtras(app)
            }
            _uiState.value = WeatherUiState(data = cached, extras = extras,
                updatedAt = WeatherRepository.updatedAt(app), cached = cached != null)
            fetchWeather()
        }
    }
    /** Called on resume and periodically; only refreshes when the data is older than [maxAgeMillis]. */
    fun refreshIfStale(maxAgeMillis: Long = 30 * 60 * 1000L) {
        if (System.currentTimeMillis() - _uiState.value.updatedAt >= maxAgeMillis) fetchWeather()
    }
    fun fetchWeather() {
        if (fetching) return
        fetching = true
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = WeatherRepository.refresh(app)
                _uiState.value = WeatherUiState(snapshot.forecast, loading = false,
                    updatedAt = snapshot.updatedAt, extras = snapshot.extras)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, cached = _uiState.value.data != null,
                    error = "Δεν ήταν δυνατή η ενημέρωση. Έλεγξε τη σύνδεσή σου και δοκίμασε ξανά.")
            } finally { fetching = false }
        }
    }
}
fun temperature(celsius: Double, fahrenheit: Boolean): String =
    "${(if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()}°${if (fahrenheit) "F" else "C"}"
fun getWeatherIcon(code: Int, isDay: Boolean = true): String = when (code) {
    0, 1 -> if (isDay) (if (code == 0) "☀️" else "🌤️") else "🌙"
    2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}
fun weatherDescription(code: Int): String = when (code) {
    0 -> "Καθαρός ουρανός"
    1 -> "Σχεδόν αίθριος"
    2 -> "Λίγες νεφώσεις"
    3 -> "Συννεφιά"
    45, 48 -> "Ομίχλη"
    51, 53, 55, 56, 57 -> "Ψιλόβροχο"
    61, 63, 65, 66, 67 -> "Βροχή"
    80, 81, 82 -> "Μπόρες"
    71, 73, 75, 77, 85, 86 -> "Χιονόπτωση"
    95, 96, 99 -> "Καταιγίδα"
    else -> "Άγνωστες συνθήκες"
}
fun athensTime(pattern: String, date: Date = Date()): String = SimpleDateFormat(pattern, Locale.forLanguageTag("el-GR")).apply {
    timeZone = TimeZone.getTimeZone("Europe/Athens")
}.format(date)
fun getDayName(date: String): String {
    if (date == athensTime("yyyy-MM-dd")) return "Σήμερα"
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Europe/Athens"); isLenient = false }
        athensTime("EEEE", requireNotNull(parser.parse(date))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("el-GR")) }
    }.getOrDefault(date)
}
