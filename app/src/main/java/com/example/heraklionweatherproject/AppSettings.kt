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
import java.text.SimpleDateFormat
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
fun localDayName(date: String): String {
    if (!LocalEnglish.current) return getDayName(date)
    if (date == athensTime("yyyy-MM-dd")) return "Today"
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

@Composable
fun SettingsDialog(
    english: Boolean,
    dark: Boolean,
    fahrenheit: Boolean,
    onLanguage: (Boolean) -> Unit,
    onDark: (Boolean) -> Unit,
    onFahrenheit: (Boolean) -> Unit,
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
                SettingsChoice(tr("Φωτεινό θέμα", "Light theme"), selected = !dark, onClick = { onDark(false) })
                SettingsChoice(tr("Σκούρο θέμα", "Dark theme"), selected = dark, onClick = { onDark(true) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(tr("Θερμοκρασία", "Temperature"), style = MaterialTheme.typography.titleMedium)
                SettingsChoice(tr("Κελσίου (°C)", "Celsius (°C)"), selected = !fahrenheit, onClick = { onFahrenheit(false) })
                SettingsChoice(tr("Φαρενάιτ (°F)", "Fahrenheit (°F)"), selected = fahrenheit, onClick = { onFahrenheit(true) })
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
