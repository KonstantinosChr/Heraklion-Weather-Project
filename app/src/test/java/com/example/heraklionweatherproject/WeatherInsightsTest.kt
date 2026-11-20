package com.example.heraklionweatherproject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherInsightsTest {

    private fun forecast(
        max: List<Double> = listOf(30.0, 31.0, 29.0),
        rain: List<Int?> = listOf(0, 0, 0),
        gusts: List<Double?> = listOf(20.0, 20.0, 20.0),
        uv: List<Double?> = listOf(5.0, 5.0, 5.0),
        windDir: Double? = 0.0, wind: Double = 10.0,
        hourly: Hourly = Hourly()
    ) = WeatherResponse(
        current = Current(25.0, 50, 25.0, 0, wind, "2026-07-10T09:30", 1, wind_direction_10m = windDir),
        daily = Daily(
            time = listOf("2026-07-10", "2026-07-11", "2026-07-12"), weather_code = listOf(0, 0, 0),
            temperature_2m_max = max, temperature_2m_min = max.map { it - 8 },
            uv_index_max = uv, precipitation_probability_max = rain, wind_gusts_10m_max = gusts
        ),
        hourly = hourly
    )

    private val day: (String) -> String = { it }

    @Test fun beaufortScale() {
        assertEquals(0, beaufort(0.5))
        assertEquals(3, beaufort(15.0))
        assertEquals(5, beaufort(35.0))
        assertEquals(12, beaufort(130.0))
    }

    @Test fun compassPoints() {
        assertEquals("N", compassPoint(0.0, true))
        assertEquals("N", compassPoint(350.0, true))
        assertEquals("ΒΔ", compassPoint(315.0, false))
        assertEquals("S", compassPoint(180.0, true))
    }

    @Test fun meltemiOnlyInSummerFromTheNorth() {
        assertTrue(isMeltemi(330.0, 35.0, 7))
        assertFalse(isMeltemi(180.0, 35.0, 7))   // southerly
        assertFalse(isMeltemi(330.0, 35.0, 12))  // winter
        assertFalse(isMeltemi(330.0, 10.0, 7))   // too weak
    }

    @Test fun heatwaveAlert() {
        val alerts = weatherAlerts(forecast(max = listOf(33.0, 39.0, 34.0)), WeatherExtras(), true, "2026-07-10", 7, day)
        val heat = alerts.single { it.icon == "🔥" }
        assertEquals(Severity.SEVERE, heat.severity)
        assertTrue(heat.message.contains("2026-07-11"))
    }

    @Test fun noAlertsOnAPleasantDay() {
        assertTrue(weatherAlerts(forecast(), WeatherExtras(), true, "2026-07-10", 7, day).isEmpty())
    }

    @Test fun windUvAndDustAlerts() {
        val air = AirQualityResponse(current = AirCurrent(dust = 180.0))
        val alerts = weatherAlerts(forecast(gusts = listOf(80.0, 20.0, 20.0), uv = listOf(9.0, 5.0, 5.0)),
            WeatherExtras(air = air), true, "2026-07-10", 7, day)
        assertEquals(setOf("💨", "🟤", "🕶️"), alerts.map { it.icon }.toSet())
        assertEquals(Severity.SEVERE, alerts.single { it.icon == "💨" }.severity)
    }

    @Test fun airAndDustLevels() {
        assertEquals(0, aqiLevel(10.0, true).rank)
        assertEquals(3, aqiLevel(65.0, true).rank)
        assertEquals(5, dustLevel(600.0, true).rank)
    }

    @Test fun seaAdvice() {
        assertTrue(seaAdvice(MarineCurrent(wave_height = 0.3, sea_surface_temperature = 25.0), 30.0, 10.0, true).good)
        assertFalse(seaAdvice(MarineCurrent(wave_height = 2.0, sea_surface_temperature = 25.0), 30.0, 10.0, true).good)
        assertFalse(seaAdvice(MarineCurrent(wave_height = 0.3, sea_surface_temperature = 16.0), 30.0, 10.0, true).good)
    }

    @Test fun moonPhaseKnownDates() {
        // 2024-04-08 18:21 UTC was a new moon (total solar eclipse).
        val newMoon = moonPhase(1_712_600_460_000L, true)
        assertEquals("🌑", newMoon.emoji)
        assertTrue(newMoon.illumination <= 2)
        // About 14.8 days later it is full.
        val full = moonPhase(1_712_600_460_000L + (14.8 * 86_400_000).toLong(), true)
        assertEquals("🌕", full.emoji)
        assertTrue(full.illumination >= 98)
    }

    @Test fun durationFormatting() {
        assertEquals("10 h 11 min", formatDuration(36_676.8, true))
        assertEquals("10 ώ 11 λ", formatDuration(36_676.8, false))
    }

    @Test fun temperatureDifferences() {
        assertEquals("3°C warmer", temperatureDifference(2.6, false, true))
        assertEquals("2°C πιο δροσερά", temperatureDifference(-2.0, false, false))
        assertEquals("about the same", temperatureDifference(0.2, false, true))
        assertEquals("9°F warmer", temperatureDifference(5.0, true, true))
    }

    @Test fun yesterdayComparison() {
        val comparison = ComparisonResponse(
            hourly = ComparisonHourly(listOf("2026-11-19T06:00", "2026-11-20T06:00"), listOf(17.0, 19.0)),
            daily = ComparisonDaily(listOf("2026-11-19", "2026-11-20"), listOf(26.8, 26.1), listOf(18.1, 18.0), listOf(36_764.0, 36_677.0))
        )
        val result = compareWithYesterday(comparison, 19.3, "2026-11-20T06:30", 26.1, "2026-11-20", 36_677.0)
        assertNotNull(result)
        assertEquals(2.3, result!!.nowDiff!!, 0.01)
        assertEquals(-0.7, result.maxDiff!!, 0.01)
        assertEquals(-87.0, result.daylightDiffSeconds!!, 0.01)
        assertNull(compareWithYesterday(null, 19.0, "2026-11-20T06:00", 20.0, "2026-11-20", null))
    }

    @Test fun runningPrefersMildDryHours() {
        val hourly = Hourly(
            time = listOf("2026-07-10T03:00", "2026-07-10T07:00", "2026-07-10T14:00", "2026-07-10T20:00"),
            temperature_2m = listOf(14.0, 18.0, 33.0, 22.0), weather_code = listOf(0, 0, 0, 0),
            precipitation_probability = listOf(0, 0, 0, 80)
        )
        // 03:00 is the coolest but outside 06–21, 20:00 is likely rainy, so 07:00 wins.
        assertEquals(1, bestRunningHour(hourly, hourly.time.indices.toList()))
    }

    @Test fun carWashWaitsForRain() {
        val wash = activitySuggestions(forecast(rain = listOf(0, 70, 0)), WeatherExtras(), emptyList(),
            "2026-07-10", false, true, day).single { it.icon == "🚗" }
        assertFalse(wash.good)
        assertTrue(wash.detail.contains("2026-07-11"))
    }

    @Test fun lastYearHandlesLeapDay() {
        // 2028-02-29 12:00 Athens → 2027-02-28
        assertEquals("2027-02-28", sameDayLastYear(1_835_431_200_000L))
    }

    @Test fun chartSamplesCarryRain() {
        val hourly = Hourly(time = listOf("2026-07-10T10:00", "2026-07-10T11:00"),
            temperature_2m = listOf(25.0, 26.0), precipitation_probability = listOf(10, 40))
        assertEquals(listOf(10, 40), temperatureSamples(hourly, "2026-07-10T10:00").map { it.rain })
    }
}
