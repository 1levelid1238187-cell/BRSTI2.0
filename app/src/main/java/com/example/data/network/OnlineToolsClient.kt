package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WeatherReport(
    val locationName: String,
    val temperatureCelsius: Double,
    val apparentCelsius: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val conditionSummary: String
)

data class SearchSnippet(
    val title: String,
    val summary: String,
    val sourceUrl: String
)

class OnlineToolsClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(cityQuery: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedCity = URLEncoder.encode(cityQuery.ifBlank { "Dhaka" }, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json")
                .build()
            val geoBody = httpClient.newCall(geoReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Geocoding failed (${resp.code})")
                resp.body?.string().orEmpty()
            }
            val geoJson = JSONObject(geoBody)
            val results = geoJson.optJSONArray("results")
                ?: error("Could not find location '$cityQuery'")
            if (results.length() == 0) error("Location '$cityQuery' not found")

            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            val resolvedName = buildString {
                append(first.optString("name", cityQuery))
                val country = first.optString("country", "")
                if (country.isNotBlank()) append(", $country")
            }

            val weatherReq = Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m")
                .build()
            val weatherBody = httpClient.newCall(weatherReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Weather service failed (${resp.code})")
                resp.body?.string().orEmpty()
            }
            val current = JSONObject(weatherBody).getJSONObject("current")
            val code = current.optInt("weather_code", 0)
            WeatherReport(
                locationName = resolvedName,
                temperatureCelsius = current.getDouble("temperature_2m"),
                apparentCelsius = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
                humidityPercent = current.optInt("relative_humidity_2m", 0),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
                conditionSummary = weatherCodeToLabel(code)
            )
        }
    }

    suspend fun searchInternet(query: String): Result<SearchSnippet> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val ddgReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "BrstiAI-Android/1.0")
                .build()
            val ddgBody = httpClient.newCall(ddgReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (ddgBody.isNotBlank()) {
                val json = JSONObject(ddgBody)
                val abstractText = json.optString("AbstractText", "")
                val heading = json.optString("Heading", query)
                val abstractUrl = json.optString("AbstractURL", "")
                if (abstractText.isNotBlank()) {
                    return@runCatching SearchSnippet(
                        title = heading.ifBlank { query },
                        summary = abstractText,
                        sourceUrl = abstractUrl.ifBlank { "https://duckduckgo.com/?q=$encoded" }
                    )
                }
            }

            // Fallback to Wikipedia REST summary
            val wikiSlug = URLEncoder.encode(query.trim().replace(" ", "_"), "UTF-8")
            val wikiReq = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/$wikiSlug")
                .header("User-Agent", "BrstiAI-Android/1.0")
                .build()
            val wikiBody = httpClient.newCall(wikiReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (wikiBody.isNotBlank()) {
                val wikiJson = JSONObject(wikiBody)
                val extract = wikiJson.optString("extract", "")
                if (extract.isNotBlank()) {
                    val pageUrl = wikiJson.optJSONObject("content_urls")
                        ?.optJSONObject("desktop")
                        ?.optString("page", "")
                        .orEmpty()
                    return@runCatching SearchSnippet(
                        title = wikiJson.optString("title", query),
                        summary = extract,
                        sourceUrl = pageUrl.ifBlank { "https://www.google.com/search?q=$encoded" }
                    )
                }
            }

            SearchSnippet(
                title = query,
                summary = "No instant summary returned; ready to open web search results in browser for '$query'.",
                sourceUrl = "https://www.google.com/search?q=$encoded"
            )
        }
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Light drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable weather"
    }
}
