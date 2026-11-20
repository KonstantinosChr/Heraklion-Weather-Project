package com.example.heraklionweatherproject

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.util.Date


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackgroundRefresh.sync(this)
        setContent { WeatherScreen() }
    }
}

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    var themeMode by rememberSaveable { mutableStateOf(AppPrefs.themeMode(context)) }
    var fahrenheit by rememberSaveable { mutableStateOf(prefs.getBoolean("fahrenheit", false)) }
    var english by rememberSaveable { mutableStateOf(prefs.getString("language", "el") == "en") }
    var notifications by rememberSaveable { mutableStateOf(AppPrefs.notificationsEnabled(context)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    // Status bar icons must stay readable: dark icons on the light theme, light icons on the dark one.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
        AppPrefs.setNotificationsEnabled(context, granted)
        BackgroundRefresh.sync(context)
    }

    // Auto refresh: on every return to the app and every 30 minutes while it stays open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(30 * 60_000L); viewModel.refreshIfStale() }
    }

    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF8ED9F3), background = Color(0xFF0D1929), surface = Color(0xFF192B40),
        surfaceVariant = Color(0xFF22374D), onSurface = Color(0xFFEDF6FF)
    ) else lightColorScheme(
        primary = Color(0xFF076A89), background = Color(0xFFF1F6FA), surface = Color.White,
        surfaceVariant = Color(0xFFE0EDF4), onSurface = Color(0xFF142C41)
    )
    // Dynamic background: the screen picks up a soft tint of the current sky.
    val current = state.data?.current
    val kind = current?.let { skyKind(it.weather_code, it.is_day == 1) } ?: SkyKind.CLEAR_DAY
    val tint by animateColorAsState(skyColors(kind, current?.is_day != 0).first, tween(900), label = "tint")
    val background = Brush.verticalGradient(listOf(lerp(colors.background, tint, if (dark) 0.35f else 0.22f), colors.background))
    CompositionLocalProvider(LocalEnglish provides english) {
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize(), color = colors.background) {
                Column(Modifier.fillMaxSize().background(background).safeDrawingPadding()) {
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
                    english = english, themeMode = themeMode, fahrenheit = fahrenheit, notifications = notifications,
                    onLanguage = { value ->
                        english = value
                        prefs.edit().putString("language", if (value) "en" else "el").apply()
                        WeatherWidgetProvider.updateAll(context)
                    },
                    onTheme = { value -> themeMode = value; AppPrefs.setThemeMode(context, value) },
                    onFahrenheit = { value ->
                        fahrenheit = value
                        prefs.edit().putBoolean("fahrenheit", value).apply()
                        WeatherWidgetProvider.updateAll(context)
                    },
                    onNotifications = { value ->
                        if (value && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifications = value
                            AppPrefs.setNotificationsEnabled(context, value)
                            BackgroundRefresh.sync(context)
                        }
                    },
                    onDismiss = { settingsOpen = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherContent(data: WeatherResponse, state: WeatherUiState, fahrenheit: Boolean,
                           refresh: () -> Unit) {
    var now by remember { mutableStateOf(athensTime("yyyy-MM-dd'T'HH:00")) }
    LaunchedEffect(Unit) {
        while (true) { now = athensTime("yyyy-MM-dd'T'HH:00"); delay(60_000) }
    }
    val english = LocalEnglish.current
    val today = now.substringBefore('T')
    val month = today.substring(5, 7).toIntOrNull() ?: 1
    val dayIndex = data.daily.time.indexOf(today)
    val hours = data.hourly.upcoming(now)
    val extras = state.extras
    val dayName: (String) -> String = { date -> dayLabel(date, english) }
    val alerts = remember(data, extras, english, today) { weatherAlerts(data, extras, english, today, month, dayName) }
    val activities = remember(data, extras, english, now, fahrenheit) {
        activitySuggestions(data, extras, hours, today, fahrenheit, english, dayName)
    }
    val comparison = remember(data, extras, now) {
        compareWithYesterday(extras.comparison, data.current.temperature_2m, now,
            data.daily.temperature_2m_max.getOrNull(dayIndex), today, data.daily.daylight_duration.getOrNull(dayIndex))
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
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
                Text(tr("Σύρε προς τα κάτω για ανανέωση", "Pull down to refresh"), style = MaterialTheme.typography.labelSmall)
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
        if (alerts.isNotEmpty()) item(key = "alerts") { AlertsSection(alerts) }
        item(key = "hero") { Reveal { CurrentWeather(data.current, fahrenheit) } }
        item(key = "dailySummary") { DailySummaryCard(data, fahrenheit, today) }
        comparison?.let { item(key = "comparison") { ComparisonCard(it, fahrenheit) } }
        item(key = "shareForecast") { ShareForecastButton(data, state, fahrenheit, today) }
        item(key = "temperatureChart") { TemperatureChart(data.hourly, now, fahrenheit) }
        item(key = "activities") { ActivitiesCard(activities) }
        item(key = "metrics") { MetricsGrid(data.current, fahrenheit) }
        item(key = "wind") { WindCard(data.current, data.daily, dayIndex, month) }
        extras.marine?.let { item(key = "sea") { SeaCard(it, data.current, data.daily.temperature_2m_max.getOrNull(dayIndex), fahrenheit) } }
        extras.air?.let { item(key = "air") { AirQualityCard(it, now) } }
        if (dayIndex >= 0) item(key = "sunMoon") { SunMoonCard(data.daily, dayIndex, comparison?.daylightDiffSeconds) }
        extras.history?.let { item(key = "history") { HistoryCard(it, data.daily.temperature_2m_max.getOrNull(dayIndex), fahrenheit) } }
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
        item(key = "weekTitle") { SectionTitle(tr("Οι επόμενες ${data.daily.count} ημέρες", "The next ${data.daily.count} days")) }
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
    val kind = skyKind(current.weather_code, day)
    val (topColor, bottomColor) = skyColors(kind, day)
    val sky by animateColorAsState(topColor, animationSpec = tween(700), label = "sky")
    val ground by animateColorAsState(bottomColor, animationSpec = tween(700), label = "ground")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
        .background(Brush.verticalGradient(listOf(sky, ground)))) {
        WeatherParticles(kind, Modifier.matchParentSize())
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
            Text("${tr("Αίσθηση", "Feels like")} ${temperature(current.apparent_temperature, fahrenheit)} · 💨 ${current.wind_speed_10m.roundToInt()} km/h",
                color = Color(0xFFC2E8F7))
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
