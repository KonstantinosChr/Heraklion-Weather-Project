package com.example.heraklionweatherproject

import java.util.Date
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

// Pure, Android-free helpers so they can be unit tested. Temperatures stay in Celsius.

private fun t(english: Boolean, el: String, en: String) = if (english) en else el

// ---------- Wind ----------

fun beaufort(kmh: Double): Int {
    val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
    return limits.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
}

fun compassPoint(degrees: Double, english: Boolean): String {
    val el = listOf("Β", "ΒΑ", "Α", "ΝΑ", "Ν", "ΝΔ", "Δ", "ΒΔ")
    val en = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((degrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8
    return if (english) en[index] else el[index]
}

/** Meltemi: the summer north/north-west wind of the Aegean, from force 5 and above. */
fun isMeltemi(directionDegrees: Double?, speedKmh: Double?, month: Int): Boolean {
    if (directionDegrees == null || speedKmh == null || month !in 5..9) return false
    val d = (directionDegrees % 360 + 360) % 360
    return (d >= 290 || d <= 45) && beaufort(speedKmh) >= 5
}

// ---------- Alerts ----------

enum class Severity { INFO, WARNING, SEVERE }
data class WeatherAlert(val icon: String, val title: String, val message: String, val severity: Severity)

fun weatherAlerts(data: WeatherResponse, extras: WeatherExtras, english: Boolean,
                  today: String, month: Int, dayName: (String) -> String): List<WeatherAlert> = buildList {
    val daily = data.daily
    val start = daily.time.indexOf(today).coerceAtLeast(0)
    val window = (start until minOf(start + 3, daily.count))

    // Heatwave over the next three days.
    window.maxByOrNull { daily.temperature_2m_max[it] }?.let { i ->
        val max = daily.temperature_2m_max[i]
        if (max >= 35) {
            val severe = max >= 38
            add(WeatherAlert("🔥", t(english, if (severe) "Καύσωνας" else "Πολύ ζεστή μέρα", if (severe) "Heatwave" else "Very hot day"),
                t(english, "${dayName(daily.time[i])}: έως ${max.roundToInt()}°C. Πίνε νερό, απόφυγε τον ήλιο 12:00–17:00 και φρόντισε ηλικιωμένους και κατοικίδια.",
                    "${dayName(daily.time[i])}: up to ${max.roundToInt()}°C. Drink water, avoid the sun 12:00–17:00 and check on the elderly and pets."),
                if (severe) Severity.SEVERE else Severity.WARNING))
        }
    }

    // Strong wind today or tomorrow.
    (start until minOf(start + 2, daily.count)).firstOrNull { (daily.wind_gusts_10m_max.getOrNull(it) ?: 0.0) >= 50 }?.let { i ->
        val gust = daily.wind_gusts_10m_max[i] ?: 0.0
        add(WeatherAlert("💨", t(english, "Δυνατός άνεμος", "Strong wind"),
            t(english, "${dayName(daily.time[i])}: ριπές έως ${gust.roundToInt()} km/h (${beaufort(gust)} Μποφόρ). Προσοχή στη θάλασσα και σε αντικείμενα στα μπαλκόνια.",
                "${dayName(daily.time[i])}: gusts up to ${gust.roundToInt()} km/h (force ${beaufort(gust)}). Take care at sea and secure balcony items."),
            if (gust >= 75) Severity.SEVERE else Severity.WARNING))
    }

    if (isMeltemi(data.current.wind_direction_10m, data.current.wind_speed_10m, month)) {
        add(WeatherAlert("🌬️", t(english, "Μελτέμι", "Meltemi"),
            t(english, "Βόρειος άνεμος ${beaufort(data.current.wind_speed_10m)} Μποφόρ. Οι βόρειες παραλίες θα έχουν κύμα — προτίμησε τη νότια ακτή.",
                "Northerly wind, force ${beaufort(data.current.wind_speed_10m)}. North-facing beaches will be wavy — the south coast is calmer."),
            Severity.INFO))
    }

    // African dust.
    val dustNow = extras.air?.current?.dust
    val dustPeak = maxDustNext24h(extras.air, data.current.time)
    val dust = listOfNotNull(dustNow, dustPeak).maxOrNull()
    if (dust != null && dust >= 100) {
        add(WeatherAlert("🟤", t(english, "Αφρικανική σκόνη", "Saharan dust"),
            t(english, "Έως ${dust.roundToInt()} µg/m³ σκόνης. Άτομα με άσθμα ή καρδιοαναπνευστικά προβλήματα καλό είναι να περιορίσουν την έντονη άσκηση έξω.",
                "Up to ${dust.roundToInt()} µg/m³ of dust. People with asthma or heart and lung conditions should limit strenuous outdoor activity."),
            if (dust >= 300) Severity.SEVERE else Severity.WARNING))
    }

    // UV today.
    daily.uv_index_max.getOrNull(start)?.takeIf { it >= 8 }?.let { uv ->
        add(WeatherAlert("🕶️", t(english, "Πολύ υψηλός δείκτης UV", "Very high UV"),
            t(english, "UV ${uv.roundToInt()} σήμερα. Αντηλιακό SPF 50, καπέλο και σκιά τις μεσημεριανές ώρες.",
                "UV ${uv.roundToInt()} today. Use SPF 50, a hat and shade around midday."),
            if (uv >= 11) Severity.SEVERE else Severity.WARNING))
    }
}

fun maxDustNext24h(air: AirQualityResponse?, now: String): Double? {
    val hourly = air?.hourly ?: return null
    val startMillis = forecastTimestamp(now.take(13) + ":00") ?: return null
    return hourly.time.indices.mapNotNull { i ->
        val ts = forecastTimestamp(hourly.time[i]) ?: return@mapNotNull null
        if (ts in startMillis until startMillis + 24 * 3_600_000L) hourly.dust.getOrNull(i) else null
    }.maxOrNull()
}

// ---------- Air quality ----------

data class Level(val label: String, val rank: Int)

fun aqiLevel(aqi: Double, english: Boolean): Level = when {
    aqi < 20 -> Level(t(english, "Καλή", "Good"), 0)
    aqi < 40 -> Level(t(english, "Ικανοποιητική", "Fair"), 1)
    aqi < 60 -> Level(t(english, "Μέτρια", "Moderate"), 2)
    aqi < 80 -> Level(t(english, "Κακή", "Poor"), 3)
    aqi < 100 -> Level(t(english, "Πολύ κακή", "Very poor"), 4)
    else -> Level(t(english, "Εξαιρετικά κακή", "Extremely poor"), 5)
}

fun dustLevel(dust: Double, english: Boolean): Level = when {
    dust < 25 -> Level(t(english, "Ελάχιστη", "Minimal"), 0)
    dust < 50 -> Level(t(english, "Χαμηλή", "Low"), 1)
    dust < 100 -> Level(t(english, "Μέτρια", "Moderate"), 2)
    dust < 300 -> Level(t(english, "Υψηλή", "High"), 3)
    else -> Level(t(english, "Πολύ υψηλή", "Very high"), 5)
}

// ---------- Sea ----------

data class SeaAdvice(val good: Boolean, val text: String)

fun seaAdvice(marine: MarineCurrent, airTemp: Double, windKmh: Double, english: Boolean): SeaAdvice {
    val wave = marine.wave_height
    val sea = marine.sea_surface_temperature
    return when {
        wave != null && wave > 1.2 -> SeaAdvice(false, t(english, "Όχι σήμερα — μεγάλα κύματα (${"%.1f".format(wave)} m).", "Not today — big waves (${"%.1f".format(wave)} m)."))
        windKmh >= 39 -> SeaAdvice(false, t(english, "Όχι σήμερα — πολύς αέρας.", "Not today — too windy."))
        sea != null && sea < 19 -> SeaAdvice(false, t(english, "Το νερό είναι κρύο (${sea.roundToInt()}°C) — μόνο για τολμηρούς!", "The water is cold (${sea.roundToInt()}°C) — only for the brave!"))
        airTemp < 22 -> SeaAdvice(false, t(english, "Μάλλον δροσερά σήμερα.", "Probably too cool today."))
        wave != null && wave > 0.6 -> SeaAdvice(true, t(english, "Μπάνιο ναι, αλλά με κυματάκι — προτίμησε προστατευμένους κόλπους.", "Swimming is OK but a bit wavy — prefer sheltered bays."))
        else -> SeaAdvice(true, t(english, "Ναι! Ήρεμη θάλασσα, καλή μέρα για μπάνιο.", "Yes! Calm sea, a good day for a swim."))
    }
}

// ---------- Sun & moon ----------

data class MoonPhase(val emoji: String, val name: String, val illumination: Int, val ageDays: Double)

fun moonPhase(timeMillis: Long, english: Boolean): MoonPhase {
    val synodic = 29.530588853
    val knownNewMoon = 947_182_440_000L // 2000-01-06 18:14 UTC
    val days = (timeMillis - knownNewMoon) / 86_400_000.0
    val age = ((days % synodic) + synodic) % synodic
    val illumination = ((1 - cos(2 * Math.PI * age / synodic)) / 2 * 100).roundToInt()
    val phases = listOf(
        "🌑" to ("Νέα σελήνη" to "New moon"), "🌒" to ("Αύξων μηνίσκος" to "Waxing crescent"),
        "🌓" to ("Πρώτο τέταρτο" to "First quarter"), "🌔" to ("Αύξων αμφίκυρτος" to "Waxing gibbous"),
        "🌕" to ("Πανσέληνος" to "Full moon"), "🌖" to ("Φθίνων αμφίκυρτος" to "Waning gibbous"),
        "🌗" to ("Τελευταίο τέταρτο" to "Last quarter"), "🌘" to ("Φθίνων μηνίσκος" to "Waning crescent")
    )
    val index = floor(age / synodic * 8 + 0.5).toInt() % 8
    val (emoji, names) = phases[index]
    return MoonPhase(emoji, if (english) names.second else names.first, illumination, age)
}

fun formatDuration(seconds: Double, english: Boolean): String {
    val minutes = (seconds / 60).roundToInt()
    return t(english, "${minutes / 60} ώ ${minutes % 60} λ", "${minutes / 60} h ${minutes % 60} min")
}

// ---------- Compared with yesterday ----------

data class YesterdayComparison(val nowDiff: Double?, val maxDiff: Double?, val daylightDiffSeconds: Double?)

fun compareWithYesterday(comparison: ComparisonResponse?, currentTemp: Double, nowHour: String,
                         todayMax: Double?, today: String, todayDaylight: Double?): YesterdayComparison? {
    comparison ?: return null
    val ts = forecastTimestamp(nowHour.take(13) + ":00")
    val yesterdayHour = ts?.let { athensTime("yyyy-MM-dd'T'HH:00", Date(it - 24 * 3_600_000L)) }
    val hourIndex = yesterdayHour?.let { comparison.hourly.time.indexOf(it) } ?: -1
    val nowDiff = comparison.hourly.temperature_2m.getOrNull(hourIndex)?.let { currentTemp - it }
    val todayIndex = comparison.daily.time.indexOf(today)
    val y = if (todayIndex > 0) todayIndex - 1 else if (todayIndex < 0 && comparison.daily.time.isNotEmpty()) 0 else -1
    val maxDiff = todayMax?.let { max -> comparison.daily.temperature_2m_max.getOrNull(y)?.let { max - it } }
    val daylightDiff = todayDaylight?.let { d -> comparison.daily.daylight_duration.getOrNull(y)?.let { d - it } }
    if (nowDiff == null && maxDiff == null && daylightDiff == null) return null
    return YesterdayComparison(nowDiff, maxDiff, daylightDiff)
}

/** e.g. "3°C πιο ζεστά" / "2°F cooler" / "ίδια θερμοκρασία". */
fun temperatureDifference(diffCelsius: Double, fahrenheit: Boolean, english: Boolean): String {
    val value = if (fahrenheit) diffCelsius * 9 / 5 else diffCelsius
    val rounded = abs(value).roundToInt()
    val unit = if (fahrenheit) "°F" else "°C"
    return when {
        rounded == 0 -> t(english, "ίδια θερμοκρασία", "about the same")
        value > 0 -> t(english, "$rounded$unit πιο ζεστά", "$rounded$unit warmer")
        else -> t(english, "$rounded$unit πιο δροσερά", "$rounded$unit cooler")
    }
}

// ---------- Activity suggestions ----------

data class Activity(val icon: String, val title: String, val detail: String, val good: Boolean)

fun bestRunningHour(hourly: Hourly, upcoming: List<Int>): Int? = upcoming.filter { i ->
    val hour = hourly.time[i].substringAfter('T').take(2).toIntOrNull() ?: return@filter false
    hour in 6..21
}.minByOrNull { i ->
    val temp = hourly.temperature_2m[i] ?: 99.0
    val rain = hourly.precipitation_probability.getOrNull(i) ?: 0
    val wind = hourly.wind_speed_10m.getOrNull(i) ?: 0.0
    val uv = hourly.uv_index.getOrNull(i) ?: 0.0
    abs(temp - 15) + rain / 8.0 + maxOf(0.0, wind - 20) / 3 + maxOf(0.0, uv - 5) * 1.5
}

fun activitySuggestions(data: WeatherResponse, extras: WeatherExtras, upcoming: List<Int>, today: String,
                        fahrenheit: Boolean, english: Boolean, dayName: (String) -> String): List<Activity> = buildList {
    val hourly = data.hourly
    bestRunningHour(hourly, upcoming)?.let { i ->
        val temp = hourly.temperature_2m[i] ?: return@let
        val rain = hourly.precipitation_probability.getOrNull(i)
        val tooHot = temp >= 30
        add(Activity("🏃", t(english, "Τρέξιμο", "Running"),
            if (tooHot) t(english, "Πολύ ζέστη όλη μέρα — αν τρέξεις, η πιο δροσερή ώρα είναι ${hourly.time[i].substringAfter('T')} (${temperature(temp, fahrenheit)}).",
                "Hot all day — if you run, the coolest slot is ${hourly.time[i].substringAfter('T')} (${temperature(temp, fahrenheit)}).")
            else t(english, "Καλύτερη ώρα: ${hourly.time[i].substringAfter('T')} · ${temperature(temp, fahrenheit)}${rain?.let { " · 💧 $it%" } ?: ""}",
                "Best time: ${hourly.time[i].substringAfter('T')} · ${temperature(temp, fahrenheit)}${rain?.let { " · 💧 $it%" } ?: ""}"),
            !tooHot))
    }

    val daily = data.daily
    val d = daily.time.indexOf(today)
    if (d >= 0) {
        val max = daily.temperature_2m_max[d]
        val rain = daily.precipitation_probability_max.getOrNull(d) ?: 0
        val wind = daily.wind_speed_10m_max.getOrNull(d) ?: data.current.wind_speed_10m
        val waves = extras.marine?.current?.wave_height
        val uv = daily.uv_index_max.getOrNull(d) ?: 0.0
        val reason = when {
            max < 24 -> t(english, "μάλλον δροσερά (${temperature(max, fahrenheit)})", "rather cool (${temperature(max, fahrenheit)})")
            rain > 40 -> t(english, "πιθανή βροχή ($rain%)", "rain is likely ($rain%)")
            wind > 30 -> t(english, "πολύς αέρας", "too windy")
            waves != null && waves > 1.0 -> t(english, "κύμα ${"%.1f".format(waves)} m", "waves ${"%.1f".format(waves)} m")
            else -> null
        }
        add(Activity("🏖️", t(english, "Παραλία", "Beach"),
            if (reason == null) t(english, "Καλή μέρα για παραλία! ${if (uv >= 6) "Πήγαινε πριν τις 11:00 ή μετά τις 17:00 λόγω UV." else ""}".trim(),
                "Good beach day! ${if (uv >= 6) "Go before 11:00 or after 17:00 because of UV." else ""}".trim())
            else t(english, "Όχι ιδανική μέρα: $reason.", "Not ideal: $reason."),
            reason == null))
    }

    // Car wash: no rain and no dust in the next three days.
    val start = d.coerceAtLeast(0)
    val rainyDay = (start until minOf(start + 3, daily.count)).firstOrNull { (daily.precipitation_probability_max.getOrNull(it) ?: 0) >= 40 }
    val dust = maxDustNext24h(extras.air, data.current.time) ?: extras.air?.current?.dust
    add(when {
        rainyDay != null -> Activity("🚗", t(english, "Πλύσιμο αυτοκινήτου", "Car wash"),
            t(english, "Περίμενε — βροχή ${dayName(daily.time[rainyDay])}.", "Wait — rain expected ${dayName(daily.time[rainyDay])}."), false)
        dust != null && dust >= 100 -> Activity("🚗", t(english, "Πλύσιμο αυτοκινήτου", "Car wash"),
            t(english, "Περίμενε — έρχεται αφρικανική σκόνη.", "Wait — Saharan dust is coming."), false)
        else -> Activity("🚗", t(english, "Πλύσιμο αυτοκινήτου", "Car wash"),
            t(english, "Καλή στιγμή — δεν αναμένεται βροχή τις επόμενες 3 μέρες.", "Good time — no rain expected for 3 days."), true)
    })
}
