package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GeocodingResponse
import com.example.network.WeatherRepository
import com.example.network.WeatherResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class WeatherViewModel : ViewModel() {
    private val repository = WeatherRepository()

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Initial)
    val uiState: StateFlow<WeatherUiState> = _uiState

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime

    private val _suggestions = MutableStateFlow<List<GeocodingResponse>>(emptyList())
    val suggestions: StateFlow<List<GeocodingResponse>> = _suggestions

    private var searchJob: Job? = null

    init {
        startClock()
        // Default city
        fetchWeather("Montreal")
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _suggestions.value = repository.getCitySuggestions(query)
        }
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                _currentTime.value = sdf.format(Date())
                delay(1000) // Update every minute is enough, but 1s is fine for UI
            }
        }
    }

    fun fetchWeather(city: String) {
        if (city.isBlank()) return
        _uiState.value = WeatherUiState.Loading
        viewModelScope.launch {
            val result = repository.getWeatherData(city)
            _uiState.value = when (result) {
                is WeatherResult.Success -> WeatherUiState.Success(result)
                is WeatherResult.Error -> WeatherUiState.Error(result.message)
            }
        }
    }
}

sealed class WeatherUiState {
    object Initial : WeatherUiState()
    object Loading : WeatherUiState()
    data class Success(val data: WeatherResult.Success) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}
