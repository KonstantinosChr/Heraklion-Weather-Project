package com.example.heraklionweatherproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.util.Date

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
                // ΔΙΟΡΘΩΣΗ 1: Νέος τρόπος δήλωσης του Locale
                val dayFormat = SimpleDateFormat("EEEE", Locale.Builder().setLanguage("el").setRegion("GR").build())
                dayFormat.format(date).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
        // Use _ instead of e, because no error
    } catch (_: Exception) {
        dateString
    }
}

// --- 5. UI  ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherScreen() }
    }
}

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark", systemDark)) }
    var fahrenheit by rememberSaveable { mutableStateOf(prefs.getBoolean("fahrenheit", false)) }
    var english by rememberSaveable { mutableStateOf(prefs.getString("language", "el") == "en") }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF8ED9F3), background = Color(0xFF0D1929), surface = Color(0xFF192B40),
        surfaceVariant = Color(0xFF22374D), onSurface = Color(0xFFEDF6FF)
    ) else lightColorScheme(
        primary = Color(0xFF076A89), background = Color(0xFFF1F6FA), surface = Color.White,
        surfaceVariant = Color(0xFFE0EDF4), onSurface = Color(0xFF142C41)
    )
    CompositionLocalProvider(LocalEnglish provides english) {
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize(), color = colors.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(tr("ΗΡΑΚΛΕΙΟ", "HERAKLION"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(tr("Κρήτη · Ελλάδα", "Crete · Greece"), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { settingsOpen = true }) {
                            Text(tr("Ρυθμίσεις", "Settings"))
                        }
                    }
                    Crossfade(targetState = when { state.data != null -> "content"; state.loading -> "loading"; else -> "error" },
                        animationSpec = tween(350), label = "screen") { screen ->
                        when (screen) {
                            "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CircularProgressIndicator()
                                    Text(tr("Ο καιρός στο Ηράκλειο…", "Weather in Heraklion…"))
                                }
                            }
                            "error" -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(tr("Δεν μπορέσαμε να φορτώσουμε τον καιρό", "Unable to load the weather"), style = MaterialTheme.typography.titleLarge)
                                    Text(tr("Δεν ήταν δυνατή η ενημέρωση. Έλεγξε τη σύνδεσή σου και δοκίμασε ξανά.", "Unable to update. Check your connection and try again."))
                                    Button(onClick = viewModel::fetchWeather) { Text(tr("Προσπάθεια ξανά", "Try again")) }
                                }
                            }
                            else -> state.data?.let { data ->
                                WeatherContent(data, state, fahrenheit, viewModel::fetchWeather)
                            }
                        }
                    }
                }
            }
            if (settingsOpen) {
                SettingsDialog(
                    english = english, dark = dark, fahrenheit = fahrenheit,
                    onLanguage = { value ->
                        english = value
                        prefs.edit().putString("language", if (value) "en" else "el").apply()
                    },
                    onDark = { value -> dark = value; prefs.edit().putBoolean("dark", value).apply() },
                    onFahrenheit = { value -> fahrenheit = value; prefs.edit().putBoolean("fahrenheit", value).apply() },
                    onDismiss = { settingsOpen = false }
                )
            }
        }
    }
}

@Composable
private fun WeatherContent(data: WeatherResponse, state: WeatherUiState, fahrenheit: Boolean,
                           refresh: () -> Unit) {
    var now by remember { mutableStateOf(athensTime("yyyy-MM-dd'T'HH:00")) }
    LaunchedEffect(Unit) {
        while (true) { now = athensTime("yyyy-MM-dd'T'HH:00"); delay(60_000) }
    }
    val hours = data.hourly.upcoming(now)
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "status") {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.updatedAt == 0L) tr("Αναμονή ενημέρωσης", "Waiting for update") else
                        "${if (state.cached) tr("Αποθηκευμένα", "Saved data") else tr("Ενημέρωση", "Updated")} · ${athensTime("dd/MM HH:mm", Date(state.updatedAt))}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = refresh, enabled = !state.loading) {
                        Text(if (state.loading) tr("Ανανέωση…", "Refreshing…") else tr("Ανανέωση ↻", "Refresh ↻"))
                    }
                }
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AnimatedVisibility(state.error != null, enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(150))) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(tr("Δεν ήταν δυνατή η ενημέρωση. Προβάλλονται τα τελευταία αποθηκευμένα δεδομένα.",
                            "Unable to update. Showing the last saved data."),
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        item(key = "hero") { Reveal { CurrentWeather(data.current, fahrenheit) } }
        item(key = "dailySummary") {
            DailySummaryCard(data, fahrenheit, now.substringBefore('T'))
        }

        item(key = "shareForecast") {
            ShareForecastButton(
                data, state, fahrenheit, now.substringBefore('T')
            )
        }

        item(key = "temperatureChart") {
            TemperatureChart(data.hourly, now, fahrenheit)
        }
        item(key = "metrics") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(tr("Υγρασία", "Humidity"), "${data.current.relative_humidity_2m}%", "💧", Modifier.weight(1f))
                MetricCard(tr("Άνεμος", "Wind"), "${data.current.wind_speed_10m} km/h", "💨", Modifier.weight(1f))
            }
        }
        item(key = "hourly") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(tr("Οι επόμενες 24 ώρες", "The next 24 hours"))
                if (hours.isEmpty()) Text(tr("Δεν υπάρχουν διαθέσιμα ωριαία δεδομένα. Δοκίμασε ανανέωση.", "No hourly data available. Try refreshing."))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(hours, key = { data.hourly.time[it] }) { i ->
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.width(88.dp).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(data.hourly.time[i].substringAfter('T'), style = MaterialTheme.typography.labelLarge)
                                Text(getWeatherIcon(data.hourly.weather_code[i] ?: -1, data.hourly.is_day.getOrNull(i) != 0), fontSize = 28.sp)
                                Text(temperature(requireNotNull(data.hourly.temperature_2m[i]), fahrenheit), fontWeight = FontWeight.Bold)
                                Text("💧 ${data.hourly.precipitation_probability.getOrNull(i)?.let { "$it%" } ?: "—"}",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Text(tr("Ώρες Ελλάδας · 💧 Πιθανότητα βροχής", "Greek local time · 💧 Chance of rain"), style = MaterialTheme.typography.labelSmall)
            }
        }
        item(key = "weekTitle") { SectionTitle(tr("Η εβδομάδα σου", "Your week")) }
        items((0 until data.daily.count).toList(), key = { data.daily.time[it] }) { i ->
            DailyForecastItem(data.daily, i, fahrenheit)
        }
        item(key = "footer") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Πάτησε μια ημέρα για λεπτομέρειες.\nΔεδομένα: Open-Meteo · Πρόγνωση, όχι επίσημες προειδοποιήσεις.", "Tap a day for details.\nData: Open-Meteo · Forecast, not official weather warnings."),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Reveal(content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f,
        animationSpec = tween(550, easing = FastOutSlowInEasing), label = "entrance")
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 32.dp.toPx() }) { content() }
}

@Composable
private fun CurrentWeather(current: Current, fahrenheit: Boolean) {
    val day = current.is_day == 1
    val sky by animateColorAsState(if (day) Color(0xFF086D93) else Color(0xFF202C61),
        animationSpec = tween(700), label = "sky")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
        .background(Brush.verticalGradient(listOf(sky, if (day) Color(0xFF104461) else Color(0xFF101831))))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (day) tr("ΗΡΑΚΛΕΙΟ ΤΩΡΑ · ΗΜΕΡΑ", "HERAKLION NOW · DAY") else tr("ΗΡΑΚΛΕΙΟ ΤΩΡΑ · ΝΥΧΤΑ", "HERAKLION NOW · NIGHT"), color = Color(0xFFC2E8F7),
                style = MaterialTheme.typography.labelLarge)
            Text(getWeatherIcon(current.weather_code, day), fontSize = 76.sp)
            AnimatedContent(targetState = temperature(current.temperature_2m, fahrenheit),
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }) togetherWith
                            fadeOut(tween(120))
                }, label = "temperature") { value ->
                Text(value, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            }
            Text(localWeatherDescription(current.weather_code), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("${tr("Αίσθηση", "Feels like")} ${temperature(current.apparent_temperature, fahrenheit)}", color = Color(0xFFC2E8F7))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun MetricCard(title: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 24.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DailyForecastItem(daily: Daily, i: Int, fahrenheit: Boolean) {
    var expanded by rememberSaveable(daily.time[i]) { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(300)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(localDayName(daily.time[i]), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(getWeatherIcon(daily.weather_code[i]), fontSize = 24.sp)
                Column(horizontalAlignment = Alignment.End) {
                    Text(temperature(daily.temperature_2m_max[i], fahrenheit), fontWeight = FontWeight.Bold)
                    Text(temperature(daily.temperature_2m_min[i], fahrenheit), style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "−" else "+")
            }
            if (expanded) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text(localWeatherDescription(daily.weather_code[i]))
                    Text("${tr("Πιθανότητα βροχής", "Chance of rain")}: ${daily.precipitation_probability_max.getOrNull(i)?.let { "$it%" } ?: "—"}")
                    Text("${tr("Μέγιστος δείκτης UV", "Maximum UV index")}: ${daily.uv_index_max.getOrNull(i) ?: "—"}")
                    Text("${tr("Ανατολή", "Sunrise")}: ${daily.sunrise.getOrNull(i)?.substringAfter('T') ?: "—"}   ·   ${tr("Δύση", "Sunset")}: ${daily.sunset.getOrNull(i)?.substringAfter('T') ?: "—"}")
                }
            }
        }
    }
}
