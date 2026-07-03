package com.example.network

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository {
    private val weatherApi = RetrofitClient.apiService
    private val geminiApi = GeminiRetrofitClient.apiService

    suspend fun getWeatherData(city: String): WeatherResult {
        return withContext(Dispatchers.IO) {
            try {
                val geoResponse = weatherApi.searchCity(city)
                val results = geoResponse.results
                if (results.isNullOrEmpty()) {
                    return@withContext WeatherResult.Error("City not found")
                }
                
                val location = results[0]
                val weather = weatherApi.getWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // Get recommendation from Gemini
                val currentTemp = weather.current?.temperature ?: 0.0
                val weatherDesc = getWeatherDescription(weather.current?.weatherCode ?: 0)
                
                val prompt = "The current weather in ${location.name} is $currentTemp°C and $weatherDesc. Keep it under 2 sentences. Give a simple, practical planning recommendation for the day (e.g. 'Take an umbrella' or 'Great day for a walk')."
                
                val recommendation = try {
                    val req = GenerateContentRequest(listOf(Content(listOf(Part(prompt)))))
                    val res = geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, req)
                    res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Enjoy your day!"
                } catch (e: Exception) {
                    "Enjoy your day! (AI suggestion unavailable)"
                }

                WeatherResult.Success(location.name, weather, recommendation)
            } catch (e: Exception) {
                WeatherResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun getCitySuggestions(query: String): List<GeocodingResult> {
        return withContext(Dispatchers.IO) {
            try {
                val geoResponse = weatherApi.searchCity(query, count = 5)
                geoResponse.results ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear, partly cloudy, and overcast"
            45, 48 -> "Fog and depositing rime fog"
            51, 53, 55 -> "Drizzle: Light, moderate, and dense intensity"
            56, 57 -> "Freezing Drizzle: Light and dense intensity"
            61, 63, 65 -> "Rain: Slight, moderate and heavy intensity"
            66, 67 -> "Freezing Rain: Light and heavy intensity"
            71, 73, 75 -> "Snow fall: Slight, moderate, and heavy intensity"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers: Slight, moderate, and violent"
            85, 86 -> "Snow showers slight and heavy"
            95 -> "Thunderstorm: Slight or moderate"
            96, 99 -> "Thunderstorm with slight and heavy hail"
            else -> "Unknown"
        }
    }
}

sealed class WeatherResult {
    data class Success(
        val cityName: String,
        val weather: WeatherResponse,
        val recommendation: String
    ) : WeatherResult()
    data class Error(val message: String) : WeatherResult()
}
