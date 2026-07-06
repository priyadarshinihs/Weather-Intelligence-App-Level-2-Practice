package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherRepository {
    private val weatherApi = RetrofitClient.apiService

    suspend fun getWeatherData(city: String): WeatherResult {
        return withContext(Dispatchers.IO) {
            try {
                val geoResponse = weatherApi.searchCity(name = city, count = 1)
                val results = geoResponse.results
                if (results.isNullOrEmpty()) {
                    return@withContext WeatherResult.Error("City not found")
                }
                
                val location = results[0]
                val cityName = location.name
                val weather = weatherApi.getWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // Get recommendation from Gemini
                val currentTemp = weather.current?.temperature ?: 0.0
                val weatherDesc = getWeatherDescription(weather.current?.weatherCode ?: 0)
                
                val recommendation = getLocalRecommendation(currentTemp, weatherDesc)

                WeatherResult.Success(cityName, weather, recommendation)
            } catch (e: Exception) {
                WeatherResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun getLocalRecommendation(temp: Double, desc: String): String {
        val lowerDesc = desc.lowercase()
        return when {
            lowerDesc.contains("rain") || lowerDesc.contains("drizzle") || lowerDesc.contains("showers") -> "Don't forget your umbrella today."
            lowerDesc.contains("snow") -> "Bundle up, it's snowy out there."
            lowerDesc.contains("thunderstorm") -> "Thunderstorms expected, stay indoors if possible."
            temp > 25 -> "It's quite warm today, stay hydrated."
            temp < 10 -> "It's a bit chilly, wear a warm jacket."
            lowerDesc.contains("clear") || lowerDesc.contains("sunny") -> "Great weather for a walk outdoors!"
            lowerDesc.contains("cloudy") || lowerDesc.contains("overcast") -> "A cloudy day, maybe a good time to read a book."
            else -> "Enjoy your day!"
        }
    }

    suspend fun getCitySuggestions(query: String): List<GeocodingResult> {
        return withContext(Dispatchers.IO) {
            try {
                val geoResponse = weatherApi.searchCity(name = query, count = 5)
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
