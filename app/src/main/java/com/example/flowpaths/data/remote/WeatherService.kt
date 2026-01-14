package com.example.flowpaths.data.remote

import android.util.Log
import com.example.flowpaths.BuildConfig
import com.example.flowpaths.data.models.PontoMapa
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelos movidos para o local correto
@Serializable
data class OpenWeatherResponse(
    val main: MainData,
    val weather: List<WeatherItem>
)

@Serializable
data class MainData(val temp: Double)

@Serializable
data class WeatherItem(val description: String, val icon: String)

class WeatherService(private val httpClient: HttpClient) {

    private val API_KEY = BuildConfig.OPENWEATHER_API_KEY
    private val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"

    /**
     * Obtém os dados meteorológicos completos da API.
     */
    suspend fun getWeatherData(location: PontoMapa): OpenWeatherResponse? {
        return try {
            httpClient.get(BASE_URL) {
                parameter("lat", location.latitude)
                parameter("lon", location.longitude)
                parameter("appid", API_KEY)
                parameter("units", "metric")
                parameter("lang", "pt")
            }.body<OpenWeatherResponse>()
        } catch (e: Exception) {
            Log.e("WeatherService", "Erro ao obter dados meteorológicos: ${e.message}")
            null
        }
    }

    /**
     * Mantido para compatibilidade com o MoodViewModel, se necessário.
     */
    suspend fun getCurrentWeatherSummary(location: PontoMapa): String {
        val data = getWeatherData(location) ?: return "Tempo indisponível"
        val temp = data.main.temp.toInt()
        val desc = data.weather.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: ""
        return "$temp°C, $desc"
    }
}