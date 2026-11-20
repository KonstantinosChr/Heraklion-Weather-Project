<p align="center">
  <img src="docs/design/banner.png" alt="Heraklion Weather – real-time weather for Heraklion, Crete" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white" alt="Android API 24+" />
  <img src="https://img.shields.io/badge/Data-Open--Meteo-0A84C1" alt="Open-Meteo" />
</p>

A modern and elegant Android application that displays real-time weather data for **Heraklion, Crete**. Beyond the forecast, it shows sea conditions, wind and meltemi, Saharan dust and air quality, weather alerts and simple tips for the day. Built from scratch with Jetpack Compose and MVVM.

## ✨ Highlights

<p align="center">
  <img src="docs/design/showcase-forecast.png" alt="Forecast at a glance: live conditions, smart insights and a 14-day forecast" />
</p>

<p align="center">
  <img src="docs/design/showcase-crete.png" alt="Made for Crete: activities, wind and sea, air quality, sun and moon" />
</p>

<p align="center">
  <img src="docs/design/showcase-app.png" alt="Your weather, your way: home screen widget, settings and light theme" />
</p>

## 📱 Screenshots

| Overview | Insights | What to do today? |
|:---:|:---:|:---:|
| <img src="docs/screenshots/overview.png" width="240" alt="Overview with weather alert and animated sky" /> | <img src="docs/screenshots/insights.png" width="240" alt="Comparison with yesterday and temperature and rain chart" /> | <img src="docs/screenshots/activities.png" width="240" alt="Activity suggestions and weather metrics" /> |
| **Wind & sea** | **Air, sun & moon** | **14-day forecast** |
| <img src="docs/screenshots/wind-and-sea.png" width="240" alt="Wind with Beaufort scale and sea conditions" /> | <img src="docs/screenshots/air-sun-moon.png" width="240" alt="Air quality, Saharan dust, sun and moon, this day last year" /> | <img src="docs/screenshots/forecast-14-days.png" width="240" alt="14-day forecast with expanded day details" /> |
| **Home screen widget** | **Settings** | **Light theme** |
| <img src="docs/screenshots/widget.png" width="240" alt="Home screen widget" /> | <img src="docs/screenshots/settings.png" width="240" alt="Settings: language, theme, units and notifications" /> | <img src="docs/screenshots/light-theme.png" width="240" alt="Light theme" /> |
| **Ελληνικά** | **Offline / error state** | |
| <img src="docs/screenshots/greek.png" width="240" alt="Greek language" /> | <img src="docs/screenshots/error-state.png" width="240" alt="Error state with Try again button" /> | |

## 🌟 Features
### Forecast
* Current temperature, "feels like", humidity, wind, **pressure, visibility, cloud cover and dew point**.
* Hourly forecast for the next 24 hours and a **14-day** forecast with expandable day details.
* Interactive temperature chart with **hourly chance of rain** bars.
* Daily summary card and **comparison with yesterday** (now vs. the same hour yesterday, today's high vs. yesterday's).
* **This day last year** – historical weather for today's date one year ago.

### Heraklion & Crete
* **Sea conditions** – water temperature, wave height and period, plus a "good day for a swim?" verdict.
* **Wind in detail** – direction arrow, compass point, Beaufort scale, gusts and a **meltemi** indicator.
* **Air quality & Saharan dust** – European AQI, PM10, PM2.5 and the dust peak for the next 24 hours.
* **Weather alerts** for heatwaves, strong wind, meltemi, Saharan dust and very high UV.
* **Activity suggestions** – best hour for running, beach day check and whether to wash the car.
* **Sun & moon** – sunrise, sunset, day length (and change from yesterday), moon phase and illumination.

### App
* **Home screen widget** with the current weather and today's range.
* **Notifications** (opt-in): morning summary, rain tomorrow, high UV, strong wind and heatwave – each at most once per day.
* Background refresh with WorkManager (only while the widget or notifications are in use).
* **Pull-to-refresh** and automatic refresh when data is older than 30 minutes.
* **Dynamic sky** – animated rain, snow, storm flashes, stars, sun rays or drifting clouds, and a background tinted by the weather.
* Offline cache, share the forecast as an image.
* Settings: Greek/English, light/dark/system theme, °C/°F, notifications.
* Robust error handling with a "Try Again" option.

## 🛠️ Tech Stack
* **Programming Language:** [Kotlin](https://kotlinlang.org/) (100%)
* **User Interface:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Asynchronous Programming:** Coroutines & StateFlow
* **Networking:** [Retrofit](https://square.github.io/retrofit/) & OkHttp
* **JSON Parsing:** [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
* **Background work:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
* **Widget:** AppWidgetProvider with RemoteViews
* **Tests:** JUnit unit tests for the forecast insights (alerts, wind, sea, moon, comparisons, activities)

## 📡 Data & API
The app fetches its data via the free [Open-Meteo](https://open-meteo.com/) APIs (Forecast, Marine, Air Quality and Historical), which do not require an API Key. The coordinates are configured for the center of Heraklion (Latitude: 35.3387, Longitude: 25.1442).

## 🚀 Local Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/KonstantinosChr/Heraklion-Weather-Project.git
   ```
2. Open the project in Android Studio or IntelliJ IDEA.
3. Allow Gradle to finish the synchronization (Sync).
4. Press the **Run** button, selecting an Emulator or a physical Android device (API 24+).

To run the unit tests:
```bash
./gradlew :app:testDebugUnitTest
```

---

<p align="center">Crafted with passion as my first Android project! 🇬🇷</p>
