package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.network.WeatherResult
import com.example.viewmodel.WeatherUiState
import com.example.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherAppScreen(viewModel: WeatherViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Hourly, 1 = Weekly

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.weather_house_bg),
            contentDescription = "Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay to ensure text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color(0xFF2C244B).copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 24.dp)
        ) {
            // Top Bar: Time and Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(contentAlignment = Alignment.TopEnd) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it 
                            viewModel.onSearchQueryChanged(it)
                            isDropdownExpanded = true
                        },
                        placeholder = { Text("Search city...", color = Color.White.copy(alpha = 0.7f)) },
                        modifier = Modifier
                            .width(200.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.White,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.fetchWeather(searchQuery)
                            isDropdownExpanded = false
                        }),
                        trailingIcon = {
                            IconButton(onClick = { 
                                viewModel.fetchWeather(searchQuery)
                                isDropdownExpanded = false
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
                            }
                        }
                    )
                    
                    DropdownMenu(
                        expanded = isDropdownExpanded && suggestions.isNotEmpty(),
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier
                            .width(200.dp)
                            .background(Color(0xFF3B2F5D))
                    ) {
                        suggestions.forEach { city ->
                            val locationParts = listOfNotNull(city.name, city.admin1, city.country).filter { it.isNotBlank() }
                            val locationText = locationParts.joinToString(", ")
                            DropdownMenuItem(
                                text = { Text(locationText, color = Color.White) },
                                onClick = {
                                    searchQuery = city.name
                                    isDropdownExpanded = false
                                    viewModel.clearSuggestions()
                                    viewModel.fetchWeather(city.name)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (uiState) {
                is WeatherUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is WeatherUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = (uiState as WeatherUiState.Error).message,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                }
                is WeatherUiState.Success -> {
                    val data = (uiState as WeatherUiState.Success).data
                    val currentTemp = data.weather.current?.temperature?.toInt() ?: 0
                    val minTemp = data.weather.daily?.temperatureMin?.firstOrNull()?.toInt() ?: 0
                    val maxTemp = data.weather.daily?.temperatureMax?.firstOrNull()?.toInt() ?: 0
                    val code = data.weather.current?.weatherCode ?: 0

                    // Main Weather Info
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = data.cityName,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$currentTemp°",
                            color = Color.White,
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.offset(y = (-10).dp)
                        )
                        Text(
                            text = getWeatherCondition(code),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "H:$maxTemp°  L:$minTemp°",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Recommendation Card
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = "Tip",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = data.recommendation,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Sheet (Glassmorphism)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                            .background(Color(0xFF3B2F5D).copy(alpha = 0.85f))
                            .padding(24.dp)
                    ) {
                        Column {
                            // Tabs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Hourly Forecast",
                                    color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selectedTab = 0 }
                                )
                                Text(
                                    text = "Weekly Forecast",
                                    color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selectedTab = 1 }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (selectedTab == 0) {
                                // Hourly Forecast
                                val hourlyTimes = data.weather.hourly?.time ?: emptyList()
                                val hourlyTemps = data.weather.hourly?.temperature ?: emptyList()
                                val hourlyCodes = data.weather.hourly?.weatherCode ?: emptyList()
                                val hourlyPrecip = data.weather.hourly?.precipitationProbability ?: emptyList()
                                
                                // Find current hour index
                                val currentHourIndex = hourlyTimes.indexOfFirst { it > getCurrentTimeIso() }.takeIf { it != -1 }?.minus(1)?.coerceAtLeast(0) ?: 0
                                
                                val displayTimes = hourlyTimes.drop(currentHourIndex).take(24)
                                val displayTemps = hourlyTemps.drop(currentHourIndex).take(24)
                                val displayCodes = hourlyCodes.drop(currentHourIndex).take(24)
                                val displayPrecip = hourlyPrecip.drop(currentHourIndex).take(24)

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(displayTimes) { index, timeIso ->
                                        val isNow = index == 0
                                        HourlyItem(
                                            time = if (isNow) "NOW" else parseHour(timeIso),
                                            temp = displayTemps.getOrNull(index)?.toInt() ?: 0,
                                            code = displayCodes.getOrNull(index) ?: 0,
                                            precip = displayPrecip.getOrNull(index) ?: 0,
                                            isNow = isNow
                                        )
                                    }
                                }
                            } else {
                                // Weekly Forecast
                                val dailyTimes = data.weather.daily?.time ?: emptyList()
                                val dailyMinTemps = data.weather.daily?.temperatureMin ?: emptyList()
                                val dailyMaxTemps = data.weather.daily?.temperatureMax ?: emptyList()
                                val dailyCodes = data.weather.daily?.weatherCode ?: emptyList()

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    itemsIndexed(dailyTimes) { index, dateIso ->
                                        DailyItem(
                                            date = parseDay(dateIso),
                                            minTemp = dailyMinTemps.getOrNull(index)?.toInt() ?: 0,
                                            maxTemp = dailyMaxTemps.getOrNull(index)?.toInt() ?: 0,
                                            code = dailyCodes.getOrNull(index) ?: 0
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun HourlyItem(time: String, temp: Int, code: Int, precip: Int, isNow: Boolean) {
    val bgColor = if (isNow) Color(0xFF5A499C) else Color(0xFF433471).copy(alpha = 0.5f)
    
    Column(
        modifier = Modifier
            .width(70.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(35.dp))
            .background(bgColor)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = time, color = Color.White, fontSize = 14.sp)
        Icon(
            imageVector = getWeatherIcon(code),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        if (precip > 0) {
            Text(text = "$precip%", color = Color(0xFF4FC3F7), fontSize = 12.sp)
        }
        Text(text = "$temp°", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DailyItem(date: String, minTemp: Int, maxTemp: Int, code: Int) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF433471).copy(alpha = 0.5f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = date, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Icon(
            imageVector = getWeatherIcon(code),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Row {
            Text(text = "$maxTemp°", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$minTemp°", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

fun getWeatherCondition(code: Int): String {
    return when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Mostly Clear"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rainy"
        71, 73, 75 -> "Snowy"
        80, 81, 82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}

fun getWeatherIcon(code: Int): ImageVector {
    return when (code) {
        0 -> Icons.Rounded.WbSunny
        1, 2 -> Icons.Rounded.CloudQueue
        3 -> Icons.Rounded.Cloud
        45, 48 -> Icons.Rounded.Cloud
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm
        else -> Icons.Rounded.WbCloudy
    }
}

fun getCurrentTimeIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.getDefault())
    return sdf.format(Date())
}

fun parseHour(iso: String): String {
    return try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val outFormat = SimpleDateFormat("h a", Locale.getDefault())
        val date = inFormat.parse(iso)
        date?.let { outFormat.format(it) } ?: iso
    } catch (e: Exception) {
        iso.takeLast(5)
    }
}

fun parseDay(iso: String): String {
    return try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val date = inFormat.parse(iso)
        date?.let { outFormat.format(it) } ?: iso
    } catch (e: Exception) {
        iso
    }
}
