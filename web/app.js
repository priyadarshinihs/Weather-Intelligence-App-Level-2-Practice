document.addEventListener('DOMContentLoaded', () => {
    // UI Elements
    const timeEl = document.getElementById('current-time');
    const searchInput = document.getElementById('search-input');
    const searchBtn = document.getElementById('search-btn');
    const suggestionsBox = document.getElementById('suggestions');
    
    const weatherContent = document.getElementById('weather-content');
    const loadingState = document.getElementById('loading-state');
    const errorState = document.getElementById('error-state');
    const errorText = document.getElementById('error-text');
    
    // API Key Modal
    const apiKeyModal = document.getElementById('api-key-modal');
    const apiKeyInput = document.getElementById('api-key-input');
    const btnSaveKey = document.getElementById('btn-save-key');
    const btnSkipKey = document.getElementById('btn-skip-key');
    const btnEditKey = document.getElementById('btn-edit-key');
    
    // Tabs
    const tabHourly = document.getElementById('tab-hourly');
    const tabWeekly = document.getElementById('tab-weekly');
    const hourlyList = document.getElementById('hourly-list');
    const weeklyList = document.getElementById('weekly-list');

    // Data elements
    const cityNameEl = document.getElementById('city-name');
    const currentTempEl = document.getElementById('current-temp');
    const weatherDescEl = document.getElementById('weather-desc');
    const maxTempEl = document.getElementById('max-temp');
    const minTempEl = document.getElementById('min-temp');
    const recTextEl = document.getElementById('recommendation-text');

    // State
    let geminiApiKey = localStorage.getItem('gemini_api_key');
    let debounceTimer;
    let currentCity = 'Montreal'; // Default city

    // Start clock
    function updateClock() {
        const now = new Date();
        timeEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    setInterval(updateClock, 1000);
    updateClock();

    // Check API Key
    if (!geminiApiKey && !sessionStorage.getItem('skip_api_key')) {
        apiKeyModal.classList.remove('hidden');
    }

    btnSaveKey.addEventListener('click', () => {
        const key = apiKeyInput.value.trim();
        if (key) {
            geminiApiKey = key;
            localStorage.setItem('gemini_api_key', key);
            apiKeyModal.classList.add('hidden');
            if (weatherContent.classList.contains('hidden') === false) {
                 fetchRecommendation(currentCity, currentTempEl.textContent, weatherDescEl.textContent);
            }
        }
    });

    btnSkipKey.addEventListener('click', () => {
        sessionStorage.setItem('skip_api_key', 'true');
        apiKeyModal.classList.add('hidden');
        if (weatherContent.classList.contains('hidden') === false) {
             fetchRecommendation(currentCity, currentTempEl.textContent, weatherDescEl.textContent);
        }
    });

    btnEditKey.addEventListener('click', () => {
        apiKeyInput.value = geminiApiKey || '';
        apiKeyModal.classList.remove('hidden');
    });

    // API Helpers
    function getWeatherCondition(code) {
        if (code === 0) return "Clear sky";
        if (code >= 1 && code <= 3) return "Mostly Clear";
        if (code === 45 || code === 48) return "Foggy";
        if ([51,53,55,56,57].includes(code)) return "Drizzle";
        if ([61,63,65,66,67].includes(code)) return "Rainy";
        if ([71,73,75,77,85,86].includes(code)) return "Snowy";
        if ([80,81,82].includes(code)) return "Showers";
        if ([95,96,99].includes(code)) return "Thunderstorm";
        return "Cloudy";
    }

    function getWeatherIcon(code) {
        if (code === 0) return "wb_sunny";
        if (code === 1 || code === 2) return "cloud_queue";
        if (code === 3 || code === 45 || code === 48) return "cloud";
        if ([51,53,55,61,63,65,80,81,82].includes(code)) return "water_drop";
        if ([71,73,75,77,85,86].includes(code)) return "ac_unit";
        if ([95,96,99].includes(code)) return "thunderstorm";
        return "wb_cloudy";
    }

    // Search and Autocomplete
    searchInput.addEventListener('input', (e) => {
        clearTimeout(debounceTimer);
        const query = e.target.value.trim();
        
        if (query.length < 2) {
            suggestionsBox.classList.add('hidden');
            return;
        }

        debounceTimer = setTimeout(async () => {
            try {
                const res = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(query)}&count=5`);
                const data = await res.json();
                
                if (data.results && data.results.length > 0) {
                    suggestionsBox.innerHTML = '';
                    data.results.forEach(city => {
                        const div = document.createElement('div');
                        div.className = 'px-4 py-3 hover:bg-white/10 cursor-pointer text-white border-b border-white/5 last:border-0 transition-colors';
                        const locationParts = [city.name, city.admin1, city.country].filter(Boolean);
                        div.textContent = locationParts.join(", ");
                        div.addEventListener('click', () => {
                            searchInput.value = city.name;
                            suggestionsBox.classList.add('hidden');
                            fetchWeather(city.name, city.latitude, city.longitude);
                        });
                        suggestionsBox.appendChild(div);
                    });
                    suggestionsBox.classList.remove('hidden');
                } else {
                    suggestionsBox.classList.add('hidden');
                }
            } catch (err) {
                console.error("Error fetching suggestions", err);
            }
        }, 300);
    });

    document.addEventListener('click', (e) => {
        if (!searchInput.contains(e.target) && !suggestionsBox.contains(e.target)) {
            suggestionsBox.classList.add('hidden');
        }
    });

    searchBtn.addEventListener('click', () => {
        const query = searchInput.value.trim();
        if (query) fetchCityData(query);
    });

    searchInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const query = searchInput.value.trim();
            if (query) fetchCityData(query);
            suggestionsBox.classList.add('hidden');
        }
    });

    async function fetchCityData(query) {
        showLoading();
        try {
            const res = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(query)}&count=1`);
            const data = await res.json();
            if (data.results && data.results.length > 0) {
                const city = data.results[0];
                fetchWeather(city.name, city.latitude, city.longitude);
            } else {
                showError("City not found");
            }
        } catch (err) {
            showError("Network error");
        }
    }

    async function fetchWeather(name, lat, lon) {
        showLoading();
        currentCity = name;
        try {
            const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto`;
            const res = await fetch(url);
            const data = await res.json();
            
            updateUI(name, data);
        } catch (err) {
            showError("Failed to fetch weather data");
        }
    }

    function updateUI(name, data) {
        loadingState.classList.add('hidden');
        errorState.classList.add('hidden');
        weatherContent.classList.remove('hidden');

        const current = data.current;
        const daily = data.daily;
        const hourly = data.hourly;

        cityNameEl.textContent = name;
        currentTempEl.textContent = Math.round(current.temperature_2m);
        weatherDescEl.textContent = getWeatherCondition(current.weather_code);
        maxTempEl.textContent = Math.round(daily.temperature_2m_max[0]);
        minTempEl.textContent = Math.round(daily.temperature_2m_min[0]);

        // Render Hourly
        hourlyList.innerHTML = '';
        const nowIso = new Date().toISOString().substring(0, 14) + '00';
        let currentIndex = hourly.time.findIndex(t => t > nowIso) - 1;
        if (currentIndex < 0) currentIndex = 0;

        for (let i = currentIndex; i < currentIndex + 24 && i < hourly.time.length; i++) {
            const isNow = i === currentIndex;
            const timeStr = isNow ? 'NOW' : formatHour(hourly.time[i]);
            const temp = Math.round(hourly.temperature_2m[i]);
            const code = hourly.weather_code[i];
            const precip = hourly.precipitation_probability ? hourly.precipitation_probability[i] : 0;

            const bgColor = isNow ? 'bg-[#5A499C]' : 'bg-[#433471]/50';
            
            const div = document.createElement('div');
            div.className = `flex flex-col items-center justify-between min-w-[70px] h-[130px] rounded-[35px] ${bgColor} py-4 shrink-0 fade-in`;
            div.style.animationDelay = `${(i - currentIndex) * 0.05}s`;
            
            let precipHtml = precip > 0 ? `<div class="text-[#4FC3F7] text-xs">${precip}%</div>` : '';
            
            div.innerHTML = `
                <div class="text-white text-sm">${timeStr}</div>
                <span class="material-icons-round text-white text-[32px]">${getWeatherIcon(code)}</span>
                ${precipHtml}
                <div class="text-white text-lg font-bold">${temp}&deg;</div>
            `;
            hourlyList.appendChild(div);
        }

        // Render Weekly
        weeklyList.innerHTML = '';
        for (let i = 0; i < daily.time.length; i++) {
            const dayStr = formatDay(daily.time[i]);
            const max = Math.round(daily.temperature_2m_max[i]);
            const min = Math.round(daily.temperature_2m_min[i]);
            const code = daily.weather_code[i];

            const div = document.createElement('div');
            div.className = `flex flex-col items-center justify-between min-w-[80px] h-[130px] rounded-[20px] bg-[#433471]/50 py-4 shrink-0 fade-in`;
            div.style.animationDelay = `${i * 0.05}s`;
            
            div.innerHTML = `
                <div class="text-white text-base font-medium">${dayStr}</div>
                <span class="material-icons-round text-white text-[32px]">${getWeatherIcon(code)}</span>
                <div class="flex items-center">
                    <span class="text-white text-sm font-bold">${max}&deg;</span>
                    <span class="text-white/60 text-sm ml-1">${min}&deg;</span>
                </div>
            `;
            weeklyList.appendChild(div);
        }

        fetchRecommendation(name, Math.round(current.temperature_2m), getWeatherCondition(current.weather_code));
    }

    async function fetchRecommendation(name, temp, desc) {
        recTextEl.textContent = "Loading AI recommendation...";
        
        if (!geminiApiKey) {
            recTextEl.textContent = "Enjoy your day! (AI suggestion unavailable, configure key in settings)";
            return;
        }

        try {
            const prompt = `The current weather in ${name} is ${temp}°C and ${desc}. Keep it under 2 sentences. Give a simple, practical planning recommendation for the day (e.g. 'Take an umbrella' or 'Great day for a walk').`;
            
            const reqBody = {
                contents: [{ parts: [{ text: prompt }] }]
            };

            const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiApiKey}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(reqBody)
            });

            const data = await res.json();
            
            if (data.candidates && data.candidates.length > 0) {
                recTextEl.textContent = data.candidates[0].content.parts[0].text;
            } else {
                recTextEl.textContent = "Enjoy your day!";
                if (data.error) {
                     console.error(data.error);
                     if (data.error.code === 400 || data.error.code === 403) {
                         recTextEl.textContent = "Enjoy your day! (Invalid API Key)";
                     }
                }
            }
        } catch (err) {
            console.error(err);
            recTextEl.textContent = "Enjoy your day!";
        }
    }

    function formatHour(isoString) {
        const date = new Date(isoString);
        return date.toLocaleTimeString([], { hour: 'numeric', hour12: true }).replace(' ', '');
    }

    function formatDay(isoString) {
        const date = new Date(isoString);
        // Add timezone offset to fix UTC day boundary issues
        date.setMinutes(date.getMinutes() + date.getTimezoneOffset());
        return date.toLocaleDateString([], { weekday: 'short' });
    }

    function showLoading() {
        loadingState.classList.remove('hidden');
        errorState.classList.add('hidden');
        weatherContent.classList.add('hidden');
    }

    function showError(msg) {
        loadingState.classList.add('hidden');
        errorState.classList.remove('hidden');
        weatherContent.classList.add('hidden');
        errorText.textContent = msg;
    }

    // Tabs logic
    tabHourly.addEventListener('click', () => {
        tabHourly.classList.replace('text-white/50', 'text-white');
        tabWeekly.classList.replace('text-white', 'text-white/50');
        hourlyList.classList.remove('hidden');
        weeklyList.classList.add('hidden');
    });

    tabWeekly.addEventListener('click', () => {
        tabWeekly.classList.replace('text-white/50', 'text-white');
        tabHourly.classList.replace('text-white', 'text-white/50');
        weeklyList.classList.remove('hidden');
        hourlyList.classList.add('hidden');
    });

    // Init default city
    fetchCityData('Montreal');
});
