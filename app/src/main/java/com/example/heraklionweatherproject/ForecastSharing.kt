package com.example.heraklionweatherproject

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class ForecastFileProvider : FileProvider()

/** Creates a share card in the app cache. No storage permission is needed. */
suspend fun shareForecastImage(
    context: Context,
    data: WeatherResponse,
    updatedAt: Long,
    cached: Boolean,
    fahrenheit: Boolean,
    english: Boolean,
    date: String
) {
    val appContext = context.applicationContext
    val summary = dailySummary(data, fahrenheit, english, date)
    val uri = withContext(Dispatchers.IO) {
        val folder = File(appContext.cacheDir, "shared_forecasts").apply {
            check(isDirectory || mkdirs()) { "Unable to create sharing directory" }
        }
        // Retain recent cards so already-open receiving apps can still read them.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        folder.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        val current = data.current
        fun label(el: String, en: String) = if (english) en else el
        data class Block(val text: String, val size: Float, val bold: Boolean = false)
        val blocks = buildList {
            add(Block(label("ΗΡΑΚΛΕΙΟ", "HERAKLION"), 48f, true))
            add(Block(label("Κρήτη · Ελλάδα", "Crete · Greece"), 30f))
            if (cached) add(Block(label("Αποθηκευμένα δεδομένα", "Saved data"), 30f, true))
            if (updatedAt > 0L) add(Block(
                label("Ενημέρωση: ", "Updated: ") + athensTime("dd/MM/yyyy HH:mm", Date(updatedAt)) +
                        label(" · Ώρα Ελλάδας", " · Greek local time"), 28f))
            add(Block(temperature(current.temperature_2m, fahrenheit), 108f, true))
            add(Block(forecastCondition(current.weather_code, english), 40f))
            add(Block(label("Αίσθηση: ", "Feels like: ") + temperature(current.apparent_temperature, fahrenheit), 32f))
            add(Block(label("Υγρασία: ", "Humidity: ") + "${current.relative_humidity_2m}% · " +
                    label("Άνεμος: ", "Wind: ") + "${current.wind_speed_10m} km/h", 30f))
            add(Block(label("Η μέρα με μια ματιά", "Your day at a glance"), 36f, true))
            add(Block(summary, 34f))
            add(Block(label("Δεδομένα: Open-Meteo · Πρόγνωση, όχι επίσημες προειδοποιήσεις.",
                "Data: Open-Meteo · Forecast, not official weather warnings."), 24f))
        }
        val layouts = blocks.map { block ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = block.size
                typeface = Typeface.create("sans-serif", if (block.bold) Typeface.BOLD else Typeface.NORMAL)
            }
            StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, 920)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(4f, 1.1f).build()
        }
        val height = 160 + layouts.sumOf { it.height + 28 }
        val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("forecast-", ".png", folder)
        try {
            val canvas = Canvas(bitmap)
            val background = Paint().apply {
                shader = LinearGradient(0f, 0f, 1080f, height.toFloat(),
                    Color.rgb(8, 109, 147), Color.rgb(16, 24, 49), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 1080f, height.toFloat(), background)
            var y = 80f
            for (layout in layouts) {
                canvas.save()
                canvas.translate(80f, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + 28f
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.forecastprovider", file)
    }
    withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, (if (english) "Heraklion — " else "Ηράκλειο — ") + summary)
            clipData = ClipData.newUri(context.contentResolver, "forecast", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, if (english) "Share forecast" else "Κοινοποίηση πρόβλεψης").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
