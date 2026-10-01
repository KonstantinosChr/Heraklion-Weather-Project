package com.example.heraklionweatherproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// --- 1. Data Models (API Open-Meteo) ---
@Serializable
data class WeatherResponse(
    val current: Current,
    val daily: Daily
)

@Serializable
data class Current(
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val apparent_temperature: Double,
    val weather_code: Int,
    val wind_speed_10m: Double
)

@Serializable
data class Daily(
    val time: List<String>,
    val weather_code: List<Int>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>
)

// --- 2. Network & API ---
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getHeraklionWeather(
        @Query("latitude") lat: Double = 35.3387,
        @Query("longitude") lon: Double = 25.1442,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    val api: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApi::class.java)
    }
}

// --- 3. ViewModel ---
sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val data: WeatherResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init { fetchWeather() }

    fun fetchWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val data = NetworkModule.api.getHeraklionWeather()
                _uiState.value = WeatherUiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Αποτυχία σύνδεσης: ${e.message}")
            }
        }
    }
}

// --- 4. Helper ---
fun getWeatherIcon(code: Int): String {
    return when (code) {
        0 -> "☀️"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "❄️"
        80, 81, 82 -> "🌧️"
        95, 96, 99 -> "⛈️"
        else -> "❓"
    }
}
fun getDayName(dateString: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = format.parse(dateString) ?: return dateString

        val calendar = Calendar.getInstance()
        calendar.time = date

        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        when {
            calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Σήμερα"
            calendar.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "Αύριο"
            else -> {
                val dayFormat = SimpleDateFormat("EEEE", Locale("el", "GR"))
                dayFormat.format(date).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    } catch (e: Exception) {
        dateString
    }
}

// --- 5. UI  ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFE3F2FD)
                ) {
                    WeatherScreen()
                }
            }
        }
    }
}

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is WeatherUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is WeatherUiState.Success -> {
            val current = state.data.current
            val daily = state.data.daily

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(text = "Ηράκλειο", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Text(text = "Κρήτη", fontSize = 18.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = getWeatherIcon(current.weather_code), fontSize = 80.sp)
                    Text(text = "${current.temperature_2m}°C", fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Αίσθηση: ${current.apparent_temperature}°C", fontSize = 16.sp, color = Color.DarkGray)

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard(title = "Υγρασία", value = "${current.relative_humidity_2m}%", icon = "💧")
                        MetricCard(title = "Άνεμος", value = "${current.wind_speed_10m} km/h", icon = "💨")
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(text = "Πρόβλεψη Εβδομάδας", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(daily.time.size) { index ->
                    DailyForecastItem(
                        date = daily.time[index],
                        weatherCode = daily.weather_code[index],
                        minTemp = daily.temperature_2m_min[index],
                        maxTemp = daily.temperature_2m_max[index]
                    )
                }
            }
        }
        is WeatherUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Σφάλμα", color = Color.Red, fontSize = 24.sp)
                    Text(text = state.message, modifier = Modifier.padding(16.dp))
                    Button(onClick = { viewModel.fetchWeather() }) {
                        Text("Προσπάθεια Ξανά")
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: String) {
    Card(
        modifier = Modifier.size(140.dp, 100.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 24.sp)
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = title, fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun DailyForecastItem(date: String, weatherCode: Int, minTemp: Double, maxTemp: Double) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = getDayName(date), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(text = getWeatherIcon(weatherCode), fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Text(text = "${minTemp.toInt()}°", color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                Text(text = "${maxTemp.toInt()}°", fontWeight = FontWeight.Bold)
            }
        }
    }
}