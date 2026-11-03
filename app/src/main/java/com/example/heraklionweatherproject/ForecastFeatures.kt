package com.example.heraklionweatherproject

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DailySummaryCard(data: WeatherResponse, fahrenheit: Boolean, date: String) {
    val summary = dailySummary(data, fahrenheit, LocalEnglish.current, date)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Η μέρα με μια ματιά", "Your day at a glance"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(summary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun TemperatureChart(hourly: Hourly, start: String, fahrenheit: Boolean) {
    val samples = remember(hourly, start) { temperatureSamples(hourly, start) }
    var selected by remember(samples) { mutableStateOf(0f) }
    var entered by remember(samples) { mutableStateOf(false) }
    LaunchedEffect(samples) { entered = true }
    val reveal by animateFloatAsState(if (entered) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "chartReveal")
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Θερμοκρασία · επόμενο 24ωρο", "Temperature · next 24 hours"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (samples.isEmpty()) {
                Text(tr("Δεν υπάρχουν διαθέσιμα δεδομένα. Δοκίμασε ανανέωση.",
                    "No data available. Try refreshing."))
            } else {
                val min = samples.minOf { it.celsius }
                val max = samples.maxOf { it.celsius }
                val bottom = if (min == max) min - 1.0 else min
                val top = if (min == max) max + 1.0 else max
                val index = selected.roundToInt().coerceIn(0, samples.lastIndex)
                val sample = samples[index]
                val selectedText = "${sample.time.substring(5, 10).replace('-', '/')} · ${sample.time.substringAfter('T')} · ${temperature(sample.celsius, fahrenheit)}"
                val chartDescription = tr("Γράφημα θερμοκρασίας. Ελάχιστη", "Temperature chart. Minimum") +
                        " ${temperature(min, fahrenheit)}, " + tr("μέγιστη", "maximum") + " ${temperature(max, fahrenheit)}."
                Text(selectedText, color = lineColor, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(temperature(top, fahrenheit), style = MaterialTheme.typography.labelSmall)
                        Text(temperature((top + bottom) / 2, fahrenheit), style = MaterialTheme.typography.labelSmall)
                        Text(temperature(bottom, fahrenheit), style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)
                        .semantics { contentDescription = chartDescription }) {
                        val inset = 6.dp.toPx()
                        val width = (size.width - inset * 2).coerceAtLeast(1f)
                        val height = (size.height - inset * 2).coerceAtLeast(1f)
                        val span = (samples.last().timestamp - samples.first().timestamp).coerceAtLeast(1L)
                        fun point(i: Int): Offset {
                            val x = if (samples.size == 1) 0.5f else
                                ((samples[i].timestamp - samples.first().timestamp).toDouble() / span).toFloat()
                            val y = ((top - samples[i].celsius) / (top - bottom)).toFloat()
                            return Offset(inset + x * width, inset + y * height)
                        }
                        for (fraction in listOf(0f, 0.5f, 1f)) {
                            val y = inset + fraction * height
                            drawLine(gridColor, Offset(inset, y), Offset(inset + width, y), strokeWidth = 1.dp.toPx())
                        }
                        clipRect(right = inset + width * reveal + inset) {
                            for (i in 0 until samples.lastIndex) {
                                drawLine(lineColor, point(i), point(i + 1), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            val marker = point(index)
                            drawLine(gridColor, Offset(marker.x, inset), Offset(marker.x, inset + height), strokeWidth = 1.dp.toPx())
                            drawCircle(lineColor.copy(alpha = 0.18f), radius = 10.dp.toPx(), center = marker)
                            drawCircle(lineColor, radius = 5.dp.toPx(), center = marker)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(samples.first().time.substringAfter('T'), style = MaterialTheme.typography.labelSmall)
                    Text(samples.last().time.substringAfter('T'), style = MaterialTheme.typography.labelSmall)
                }
                if (samples.size > 1) {
                    val sliderLabel = tr("Επιλογή ώρας γραφήματος", "Select chart hour")
                    Slider(value = selected, onValueChange = { selected = it },
                        valueRange = 0f..samples.lastIndex.toFloat(), steps = (samples.size - 2).coerceAtLeast(0),
                        modifier = Modifier.semantics { contentDescription = sliderLabel })
                }
                Text(tr("Ώρες Ελλάδας · Μετακίνησε τον δείκτη για λεπτομέρειες.",
                    "Greek local time · Move the slider for details."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ShareForecastButton(data: WeatherResponse, state: WeatherUiState, fahrenheit: Boolean, date: String) {
    val context = LocalContext.current
    val english = LocalEnglish.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                if (!sharing) {
                    sharing = true
                    failed = false
                    scope.launch {
                        try {
                            shareForecastImage(context, data, state.updatedAt, state.cached, fahrenheit, english, date)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally { sharing = false }
                    }
                }
            },
            enabled = !sharing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sharing) tr("Δημιουργία εικόνας…", "Creating image…") else
                tr("Κοινοποίηση πρόβλεψης", "Share forecast"))
        }
        if (failed) Text(tr("Δεν ήταν δυνατή η κοινοποίηση. Δοκίμασε ξανά.",
            "Unable to share. Please try again."), color = MaterialTheme.colorScheme.error)
    }
}
