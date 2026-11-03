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
import kotlinx.serialization.encodeToString
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
    val time: String = "", val is_day: Int = 1
)
@Serializable
data class Daily(
    val time: List<String>, val weather_code: List<Int>,
    val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>,
    val sunrise: List<String?> = emptyList(), val sunset: List<String?> = emptyList(),
    val uv_index_max: List<Double?> = emptyList(),
    val precipitation_probability_max: List<Int?> = emptyList()
) {
    val count: Int get() = minOf(time.size, weather_code.size, temperature_2m_max.size, temperature_2m_min.size)
}
@Serializable
data class Hourly(
    val time: List<String> = emptyList(), val temperature_2m: List<Double?> = emptyList(),
    val weather_code: List<Int?> = emptyList(), val precipitation_probability: List<Int?> = emptyList(),
    val is_day: List<Int?> = emptyList()
) {
    fun upcoming(now: String): List<Int> = time.indices.filter {
        time[it] >= now && temperature_2m.getOrNull(it) != null && weather_code.getOrNull(it) != null
    }.take(24)
}
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getHeraklionWeather(
        @Query("latitude") lat: Double = 35.3387,
        @Query("longitude") lon: Double = 25.1442,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability,is_day",
        @Query("forecast_days") days: Int = 7,
        @Query("timezone") timezone: String = "Europe/Athens"
    ): WeatherResponse
}
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
    val error: String? = null, val updatedAt: Long = 0L, val cached: Boolean = false
)
class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("weather", 0)
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState = _uiState.asStateFlow()
    private var fetching = false
    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching { NetworkModule.json.decodeFromString<WeatherResponse>(prefs.getString("cache", null) ?: "") }.getOrNull()
            }
            _uiState.value = WeatherUiState(data = cached, updatedAt = prefs.getLong("updated", 0), cached = cached != null)
            fetchWeather()
        }
    }
    fun fetchWeather() {
        if (fetching) return
        fetching = true
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val data = NetworkModule.api.getHeraklionWeather()
                val updated = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    prefs.edit().putString("cache", NetworkModule.json.encodeToString(data)).putLong("updated", updated).apply()
                }
                _uiState.value = WeatherUiState(data, loading = false, updatedAt = updated)
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
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "⛅" else "☁️"
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
