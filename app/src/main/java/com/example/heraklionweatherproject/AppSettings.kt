package com.example.heraklionweatherproject

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// The selected language is scoped to the application UI, not the device.
val LocalEnglish = staticCompositionLocalOf { false }

@Composable
fun tr(greek: String, english: String): String = if (LocalEnglish.current) english else greek

@Composable
fun localWeatherDescription(code: Int): String {
    if (!LocalEnglish.current) return weatherDescription(code)
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

@Composable
fun localDayName(date: String): String = dayLabel(date, LocalEnglish.current)

/** "Σήμερα" / "Αύριο" / weekday name, in Greek time. Not composable so workers and alerts can use it. */
fun dayLabel(date: String, english: Boolean): String {
    if (date == athensTime("yyyy-MM-dd")) return if (english) "Today" else "Σήμερα"
    if (date == athensTime("yyyy-MM-dd", Date(System.currentTimeMillis() + 86_400_000L))) return if (english) "Tomorrow" else "Αύριο"
    if (!english) return getDayName(date)
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Europe/Athens")
            isLenient = false
        }
        SimpleDateFormat("EEEE", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Europe/Athens")
        }.format(requireNotNull(parser.parse(date)))
    }.getOrDefault(date)
}

/** Preferences shared by the screen, the widget and the background worker. */
object AppPrefs {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("appearance", 0)
    fun english(context: Context) = prefs(context).getString("language", "el") == "en"
    fun fahrenheit(context: Context) = prefs(context).getBoolean("fahrenheit", false)
    fun notificationsEnabled(context: Context) = prefs(context).getBoolean("notifications", false)
    fun setNotificationsEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("notifications", value).apply()
    /** "system", "light" or "dark". Migrates the older boolean "dark" preference. */
    fun themeMode(context: Context): String {
        val p = prefs(context)
        return p.getString("theme", null) ?: if (p.contains("dark")) (if (p.getBoolean("dark", false)) "dark" else "light") else "system"
    }
    fun setThemeMode(context: Context, value: String) = prefs(context).edit().putString("theme", value).apply()
}

@Composable
fun SettingsDialog(
    english: Boolean,
    themeMode: String,
    fahrenheit: Boolean,
    notifications: Boolean,
    onLanguage: (Boolean) -> Unit,
    onTheme: (String) -> Unit,
    onFahrenheit: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Ρυθμίσεις", "Settings")) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(tr("Γλώσσα", "Language"), style = MaterialTheme.typography.titleMedium)
                SettingsChoice("Ελληνικά", selected = !english, onClick = { onLanguage(false) })
                SettingsChoice("English", selected = english, onClick = { onLanguage(true) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(tr("Εμφάνιση", "Appearance"), style = MaterialTheme.typography.titleMedium)
                SettingsChoice(tr("Όπως η συσκευή", "Follow system"), selected = themeMode == "system", onClick = { onTheme("system") })
                SettingsChoice(tr("Φωτεινό θέμα", "Light theme"), selected = themeMode == "light", onClick = { onTheme("light") })
                SettingsChoice(tr("Σκούρο θέμα", "Dark theme"), selected = themeMode == "dark", onClick = { onTheme("dark") })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(tr("Θερμοκρασία", "Temperature"), style = MaterialTheme.typography.titleMedium)
                SettingsChoice(tr("Κελσίου (°C)", "Celsius (°C)"), selected = !fahrenheit, onClick = { onFahrenheit(false) })
                SettingsChoice(tr("Φαρενάιτ (°F)", "Fahrenheit (°F)"), selected = fahrenheit, onClick = { onFahrenheit(true) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Ειδοποιήσεις", "Notifications"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Πρωινή σύνοψη, βροχή αύριο, υψηλό UV, δυνατός αέρας, καύσωνας.",
                            "Morning summary, rain tomorrow, high UV, strong wind, heatwave."), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = notifications, onCheckedChange = onNotifications)
                }
                Text(tr("Οι αλλαγές εφαρμόζονται και αποθηκεύονται αυτόματα.",
                    "Changes are applied and saved automatically."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Κλείσιμο", "Close")) } }
    )
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.weight(1f))
    }
}
