package com.example.heraklionweatherproject

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---------- Shared building blocks ----------

@Composable
fun InfoCard(title: String, icon: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(icon, fontSize = 20.sp)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Alerts ----------

@Composable
fun AlertsSection(alerts: List<WeatherAlert>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        alerts.forEach { alert ->
            val tint = when (alert.severity) {
                Severity.SEVERE -> Color(0xFFE53935)
                Severity.WARNING -> Color(0xFFFB8C00)
                Severity.INFO -> MaterialTheme.colorScheme.primary
            }
            Surface(shape = RoundedCornerShape(20.dp), color = tint.copy(alpha = 0.16f)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(alert.icon, fontSize = 26.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(alert.title, fontWeight = FontWeight.Bold, color = tint)
                        Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ---------- Extra metrics ----------

@Composable
fun MetricsGrid(current: Current, fahrenheit: Boolean) {
    val metrics = listOfNotNull(
        Triple(tr("Υγρασία", "Humidity"), "${current.relative_humidity_2m}%", "💧"),
        current.pressure_msl?.let { Triple(tr("Πίεση", "Pressure"), "${it.roundToInt()} hPa", "🧭") },
        current.visibility?.let {
            val km = it / 1000
            Triple(tr("Ορατότητα", "Visibility"), if (km >= 10) "${km.roundToInt()} km" else "%.1f km".format(km), "👁️")
        },
        current.cloud_cover?.let { Triple(tr("Νεφοκάλυψη", "Cloud cover"), "${it.roundToInt()}%", "☁️") },
        current.dew_point_2m?.let { Triple(tr("Σημείο δρόσου", "Dew point"), temperature(it, fahrenheit), "🌡️") },
        Triple(tr("Αίσθηση", "Feels like"), temperature(current.apparent_temperature, fahrenheit), "🤚")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, value, icon) -> MetricCard(title, value, icon, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------- Wind ----------

@Composable
fun WindCard(current: Current, daily: Daily, dayIndex: Int, month: Int) {
    val english = LocalEnglish.current
    val direction = current.wind_direction_10m
    InfoCard(tr("Άνεμος", "Wind"), "💨") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center) {
                // Meteorological direction is where the wind comes FROM; the arrow shows where it blows to.
                if (direction != null) Text("↑", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate((direction + 180).toFloat())
                        .semantics { contentDescription = "${compassPoint(direction, english)} ${direction.roundToInt()}°" })
                else Text("—")
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${current.wind_speed_10m.roundToInt()} km/h · ${beaufort(current.wind_speed_10m)} ${tr("Μποφόρ", "Bft")}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (direction != null) Text("${tr("Από", "From")} ${compassPoint(direction, english)} (${direction.roundToInt()}°)")
                current.wind_gusts_10m?.let { Text("${tr("Ριπές", "Gusts")}: ${it.roundToInt()} km/h") }
            }
        }
        daily.wind_gusts_10m_max.getOrNull(dayIndex)?.let { gust ->
            Text("${tr("Σήμερα", "Today")}: ${tr("μέγιστος", "max")} ${(daily.wind_speed_10m_max.getOrNull(dayIndex) ?: 0.0).roundToInt()} km/h, " +
                    "${tr("ριπές έως", "gusts up to")} ${gust.roundToInt()} km/h", style = MaterialTheme.typography.bodySmall)
        }
        if (isMeltemi(direction, current.wind_speed_10m, month)) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                Text("🌬️ " + tr("Φυσάει μελτέμι", "Meltemi is blowing"), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------- Sea ----------

@Composable
fun SeaCard(marine: MarineResponse, current: Current, todayMax: Double?, fahrenheit: Boolean) {
    val m = marine.current
    val advice = seaAdvice(m, todayMax ?: current.temperature_2m, current.wind_speed_10m, LocalEnglish.current)
    InfoCard(tr("Θάλασσα", "Sea"), "🌊") {
        Row(Modifier.fillMaxWidth()) {
            LabeledValue(tr("Νερό", "Water"), m.sea_surface_temperature?.let { temperature(it, fahrenheit) } ?: "—", Modifier.weight(1f))
            LabeledValue(tr("Κύμα", "Waves"), m.wave_height?.let { "%.1f m".format(it) } ?: "—", Modifier.weight(1f))
            LabeledValue(tr("Περίοδος", "Period"), m.wave_period?.let { "${it.roundToInt()} s" } ?: "—", Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(16.dp),
            color = (if (advice.good) Color(0xFF26A69A) else Color(0xFFFB8C00)).copy(alpha = 0.16f)) {
            Text((if (advice.good) "✅ " else "⚠️ ") + tr("Καλή μέρα για μπάνιο; ", "Good day for a swim? ") + advice.text,
                Modifier.fillMaxWidth().padding(12.dp))
        }
        Text(tr("Ανοιχτά των ακτών του Ηρακλείου", "Off the Heraklion coast"), style = MaterialTheme.typography.labelSmall)
    }
}

// ---------- Air quality & dust ----------

private val levelColors = listOf(Color(0xFF43A047), Color(0xFF9CCC65), Color(0xFFFFC107),
    Color(0xFFFB8C00), Color(0xFFE53935), Color(0xFF8E24AA))

@Composable
fun AirQualityCard(air: AirQualityResponse, now: String) {
    val english = LocalEnglish.current
    val c = air.current
    InfoCard(tr("Ποιότητα αέρα & σκόνη", "Air quality & dust"), "🟤") {
        c.european_aqi?.let { aqi ->
            val level = aqiLevel(aqi, english)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(14.dp).background(levelColors[level.rank], CircleShape))
                Text("AQI ${aqi.roundToInt()} · ${level.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            // Simple 0–100 scale bar.
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth((aqi / 100).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight()
                    .background(levelColors[level.rank], RoundedCornerShape(50)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            LabeledValue("PM10", c.pm10?.let { "${it.roundToInt()} µg/m³" } ?: "—", Modifier.weight(1f))
            LabeledValue("PM2.5", c.pm2_5?.let { "${it.roundToInt()} µg/m³" } ?: "—", Modifier.weight(1f))
            LabeledValue(tr("Σκόνη", "Dust"), c.dust?.let { "${it.roundToInt()} µg/m³" } ?: "—", Modifier.weight(1f))
        }
        val peak = maxDustNext24h(air, now)
        if (peak != null) {
            val level = dustLevel(peak, english)
            Text("${tr("Αφρικανική σκόνη επόμενο 24ωρο", "Saharan dust next 24h")}: ${level.label} (${tr("έως", "up to")} ${peak.roundToInt()} µg/m³)",
                color = if (level.rank >= 3) levelColors[level.rank] else MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------- Compared with yesterday ----------

@Composable
fun ComparisonCard(comparison: YesterdayComparison, fahrenheit: Boolean) {
    val english = LocalEnglish.current
    InfoCard(tr("Σε σχέση με χθες", "Compared with yesterday"), "📊") {
        comparison.nowDiff?.let {
            Text("${diffArrow(it)} ${tr("Τώρα", "Right now")}: ${temperatureDifference(it, fahrenheit, english)} " +
                    if (isSame(it, fahrenheit)) tr("με χθες αυτή την ώρα", "as this time yesterday")
                    else tr("από χθες αυτή την ώρα", "than this time yesterday"))
        }
        comparison.maxDiff?.let {
            Text("${diffArrow(it)} ${tr("Μέγιστη σήμερα", "Today's high")}: ${temperatureDifference(it, fahrenheit, english)} " +
                    if (isSame(it, fahrenheit)) tr("με τη χθεσινή", "as yesterday's")
                    else tr("από τη χθεσινή", "than yesterday's"))
        }
    }
}

private fun isSame(diffCelsius: Double, fahrenheit: Boolean) =
    abs(if (fahrenheit) diffCelsius * 9 / 5 else diffCelsius).roundToInt() == 0

private fun diffArrow(diff: Double) = when {
    abs(diff) < 0.5 -> "➖"
    diff > 0 -> "🔺"
    else -> "🔻"
}

// ---------- Activities ----------

@Composable
fun ActivitiesCard(activities: List<Activity>) {
    InfoCard(tr("Τι να κάνω σήμερα;", "What to do today?"), "🎯") {
        activities.forEach { a ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(a.icon, fontSize = 24.sp)
                Column(Modifier.weight(1f)) {
                    Text(a.title, fontWeight = FontWeight.Bold)
                    Text(a.detail, style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (a.good) "👍" else "✋", fontSize = 20.sp)
            }
        }
    }
}

// ---------- Sun & moon ----------

@Composable
fun SunMoonCard(daily: Daily, dayIndex: Int, daylightDiffSeconds: Double?) {
    val english = LocalEnglish.current
    val moon = remember(english) { moonPhase(System.currentTimeMillis(), english) }
    val sunrise = daily.sunrise.getOrNull(dayIndex)
    val sunset = daily.sunset.getOrNull(dayIndex)
    val daylight = daily.daylight_duration.getOrNull(dayIndex) ?: run {
        val a = sunrise?.let(::forecastTimestamp); val b = sunset?.let(::forecastTimestamp)
        if (a != null && b != null) (b - a) / 1000.0 else null
    }
    InfoCard(tr("Ήλιος & σελήνη", "Sun & moon"), "🌅") {
        Row(Modifier.fillMaxWidth()) {
            LabeledValue(tr("Ανατολή", "Sunrise"), sunrise?.substringAfter('T') ?: "—", Modifier.weight(1f))
            LabeledValue(tr("Δύση", "Sunset"), sunset?.substringAfter('T') ?: "—", Modifier.weight(1f))
            LabeledValue(tr("Φως ημέρας", "Daylight"), daylight?.let { formatDuration(it, english) } ?: "—", Modifier.weight(1f))
        }
        daylightDiffSeconds?.let {
            val minutes = (it / 60).roundToInt()
            Text(when {
                minutes > 0 -> tr("☀️ +$minutes λεπτά φως από χθες", "☀️ +$minutes min of daylight vs yesterday")
                minutes < 0 -> tr("🌘 ${-minutes} λεπτά λιγότερο φως από χθες", "🌘 ${-minutes} min less daylight than yesterday")
                else -> tr("Ίδια διάρκεια ημέρας με χθες", "Same day length as yesterday")
            }, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(moon.emoji, fontSize = 36.sp)
            Column {
                Text(moon.name, fontWeight = FontWeight.Bold)
                Text("${tr("Φωτισμένη", "Illuminated")} ${moon.illumination}% · ${tr("ηλικία", "age")} ${moon.ageDays.roundToInt()} ${tr("ημερών", "days")}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- This day last year ----------

@Composable
fun HistoryCard(history: HistoryResponse, todayMax: Double?, fahrenheit: Boolean) {
    val english = LocalEnglish.current
    val d = history.daily
    val date = d.time.firstOrNull() ?: return
    val max = d.temperature_2m_max.firstOrNull()
    val min = d.temperature_2m_min.firstOrNull()
    val rain = d.precipitation_sum.firstOrNull()
    val code = d.weather_code.firstOrNull()
    InfoCard(tr("Σαν σήμερα πέρσι", "This day last year"), "📅") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(code?.let { getWeatherIcon(it) } ?: "📅", fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(date.split('-').reversed().joinToString("/"), style = MaterialTheme.typography.labelMedium)
                Text(listOfNotNull(max?.let { "↑ ${temperature(it, fahrenheit)}" }, min?.let { "↓ ${temperature(it, fahrenheit)}" }).joinToString("  "),
                    fontWeight = FontWeight.Bold)
                code?.let { Text(forecastCondition(it, english), style = MaterialTheme.typography.bodySmall) }
            }
            rain?.let { LabeledValue(tr("Βροχή", "Rain"), "%.1f mm".format(it)) }
        }
        if (max != null && todayMax != null) {
            Text("${tr("Φέτος", "This year")}: ${temperatureDifference(todayMax - max, fahrenheit, english)}",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------- Dynamic sky ----------

enum class SkyKind { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, RAIN, STORM, SNOW, FOG }

fun skyKind(code: Int, isDay: Boolean): SkyKind = when (code) {
    0, 1 -> if (isDay) SkyKind.CLEAR_DAY else SkyKind.CLEAR_NIGHT
    2, 3 -> SkyKind.CLOUDY
    45, 48 -> SkyKind.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> SkyKind.RAIN
    71, 73, 75, 77, 85, 86 -> SkyKind.SNOW
    95, 96, 99 -> SkyKind.STORM
    else -> if (isDay) SkyKind.CLEAR_DAY else SkyKind.CLEAR_NIGHT
}

/** Top and bottom colour of the hero card (also tints the screen background). */
fun skyColors(kind: SkyKind, isDay: Boolean): Pair<Color, Color> = when (kind) {
    SkyKind.CLEAR_DAY -> Color(0xFF0A84C1) to Color(0xFF0E4D70)
    SkyKind.CLEAR_NIGHT -> Color(0xFF202C61) to Color(0xFF101831)
    SkyKind.CLOUDY -> if (isDay) Color(0xFF4F7894) to Color(0xFF2B4A60) else Color(0xFF2E3A55) to Color(0xFF151C2E)
    SkyKind.FOG -> Color(0xFF6B7F8E) to Color(0xFF38485A)
    SkyKind.RAIN -> Color(0xFF3F5B73) to Color(0xFF1F2E3D)
    SkyKind.STORM -> Color(0xFF393C58) to Color(0xFF16172A)
    SkyKind.SNOW -> Color(0xFF7D96AB) to Color(0xFF3E5366)
}

private data class Particle(val x: Float, val offset: Float, val speed: Int, val size: Float)

@Composable
fun WeatherParticles(kind: SkyKind, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sky")
    // Integer speeds keep every loop seamless when the 0→1 progress wraps around.
    val fast by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "fast")
    val slow by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(45_000, easing = LinearEasing)), label = "slow")
    val particles = remember(kind) {
        val random = Random(kind.ordinal)
        List(if (kind == SkyKind.CLEAR_NIGHT) 28 else 42) {
            Particle(random.nextFloat(), random.nextFloat(), 3 + random.nextInt(4), 0.5f + random.nextFloat())
        }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (kind) {
            SkyKind.RAIN, SkyKind.STORM -> {
                particles.forEach { p ->
                    val y = ((p.offset + fast * p.speed) % 1f) * (h + 40f) - 20f
                    val x = p.x * w
                    drawLine(Color.White.copy(alpha = 0.28f), Offset(x, y), Offset(x - 5f, y + 18f * p.size + 10f),
                        strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                }
                if (kind == SkyKind.STORM && (fast in 0.30f..0.315f || fast in 0.34f..0.35f)) {
                    drawRect(Color.White.copy(alpha = 0.22f))
                }
            }
            SkyKind.SNOW -> particles.forEach { p ->
                val y = ((p.offset + fast * (p.speed - 2)) % 1f) * h
                val x = p.x * w + sin((fast * 2 + p.offset) * 2 * PI).toFloat() * 10f
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 2.dp.toPx() * p.size, center = Offset(x, y))
            }
            SkyKind.CLEAR_NIGHT -> particles.forEach { p ->
                val twinkle = abs(sin((fast * p.speed + p.offset) * PI)).toFloat()
                drawCircle(Color.White.copy(alpha = 0.15f + 0.6f * twinkle), radius = 1.dp.toPx() * p.size,
                    center = Offset(p.x * w, p.offset * h * 0.8f))
            }
            SkyKind.CLEAR_DAY -> {
                val center = Offset(w * 0.86f, h * 0.12f)
                drawCircle(Color(0xFFFFE082).copy(alpha = 0.18f), radius = 90.dp.toPx(), center = center)
                drawCircle(Color(0xFFFFE082).copy(alpha = 0.16f), radius = 52.dp.toPx(), center = center)
                rotate(degrees = slow * 360f * 3, pivot = center) {
                    for (i in 0 until 12) {
                        rotate(i * 30f, center) {
                            drawLine(Color(0xFFFFF3C4).copy(alpha = 0.18f), Offset(center.x, center.y - 62.dp.toPx()),
                                Offset(center.x, center.y - 110.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
            }
            SkyKind.CLOUDY, SkyKind.FOG -> {
                val alpha = if (kind == SkyKind.FOG) 0.14f else 0.10f
                // Each cloud is one path (a union of shapes), so overlapping puffs don't stack their alpha.
                listOf(Triple(0.10f, 1, 0.55f), Triple(0.60f, 2, 0.85f), Triple(0.85f, 1, 0.38f)).forEach { (base, speed, depth) ->
                    val r = 26.dp.toPx() * (0.7f + depth * 0.5f)
                    val x = ((base + slow * speed) % 1f) * (w + 6 * r) - 3 * r
                    val y = h * depth
                    val cloud = Path().apply {
                        addRoundRect(RoundRect(Rect(x - r * 1.6f, y - r * 0.15f, x + r * 1.9f, y + r * 0.75f), CornerRadius(r * 0.45f)))
                        addOval(Rect(Offset(x - r * 0.8f, y + r * 0.05f), r * 0.75f))
                        addOval(Rect(Offset(x + r * 0.05f, y - r * 0.25f), r * 1.05f))
                        addOval(Rect(Offset(x + r * 1.05f, y + r * 0.1f), r * 0.7f))
                    }
                    drawPath(cloud, Color.White.copy(alpha = alpha))
                }
            }
        }
    }
}
