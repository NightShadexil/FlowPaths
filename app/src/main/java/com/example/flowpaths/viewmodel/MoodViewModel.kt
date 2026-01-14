package com.example.flowpaths.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.data.location.FlowPathsLocationManager
import com.example.flowpaths.data.models.PontoMapa
import com.example.flowpaths.data.remote.DirectionsService
import com.example.flowpaths.data.remote.GeminiMoodAnalyzer
import com.example.flowpaths.data.remote.SpotifyService
import com.example.flowpaths.data.remote.WeatherService
import com.example.flowpaths.data.repository.RouteRepository
import com.example.flowpaths.data.states.MoodUiState
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

class MoodViewModel(
    private val geminiAnalyzer: GeminiMoodAnalyzer,
    private val directionsService: DirectionsService,
    private val weatherService: WeatherService,
    private val spotifyService: SpotifyService,
    val routeRepository: RouteRepository,
    private val locationManager: FlowPathsLocationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MoodUiState>(MoodUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun setUiState(newState: MoodUiState) {
        _uiState.value = newState
    }

    fun analyzeVibe(moodText: String, moodIcon: String) {
        viewModelScope.launch {
            _uiState.value = MoodUiState.Loading

            if (moodText.isBlank() && moodIcon.isBlank()) {
                _uiState.value = MoodUiState.Error("Descreve como te sentes 🙂")
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    // 1️⃣ Localização (Retry logic)
                    var location = locationManager.userLocation.value
                    var attempts = 0
                    while (location == null && attempts < 6) {
                        delay(500)
                        location = locationManager.userLocation.value
                        attempts++
                    }

                    val lat = location?.latitude ?: 41.2612
                    val lon = location?.longitude ?: -8.6210
                    val pontoAtual = PontoMapa(lat, lon)

                    // 2️⃣ Weather
                    val dadosMeteorologicos = try {
                        weatherService.getCurrentWeatherSummary(pontoAtual) ?: "20°C, Céu Limpo"
                    } catch (e: Exception) { "20°C, Céu Limpo" }

                    // 3️⃣ IA
                    val iaResult = geminiAnalyzer.getVibeRecommendation(
                        moodText = moodText,
                        moodIcon = moodIcon,
                        dadosMeteorologicos = dadosMeteorologicos,
                        userLocation = "$lat,$lon"
                    )

                    iaResult.onSuccess { percursoIA ->

                        // 4️⃣ Spotify (Fallbacks Robustos)
                        val termo = percursoIA.termoPesquisaSpotify?.ifBlank { null } ?: "$moodText vibe"

                        val playlistReal = try {
                            spotifyService.getPlaylistForVibe(termo)
                        } catch (e: Exception) { null }

                        val finalUrl = playlistReal?.urls?.get("spotify")
                            ?: percursoIA.playlistSpotifyUrl?.ifBlank { null }
                            ?: "https://open.spotify.com/search/${URLEncoder.encode(termo, "UTF-8")}"

                        val finalName = playlistReal?.name
                            ?: percursoIA.playlistSpotifyNome?.ifBlank { null }
                            ?: "Mix: $termo"

                        // 5️⃣ Polyline
                        val polyline = getDetailedPolyline(percursoIA.pontosParagem)

                        // 6️⃣ DESAFIOS (CORREÇÃO "SEM GPS")
                        // O .zip garante que só processamos desafios que tenham um ponto correspondente.
                        // Se a IA gerar 4 desafios e 3 pontos, o 4º é descartado aqui.
                        val desafiosCorrigidos = percursoIA.desafios.zip(percursoIA.pontosParagem) { desafio, ponto ->
                            desafio.copy(
                                latitude = ponto.latitude,
                                longitude = ponto.longitude,
                                pontoReferencia = ponto
                            )
                        }

                        // 7️⃣ Objeto Final
                        val percursoFinal = percursoIA.copy(
                            desafios = desafiosCorrigidos,
                            polylineDetalhada = polyline.encodeToPolylineString(),
                            dadosMeteorologicos = dadosMeteorologicos,
                            playlistSpotifyNome = finalName,
                            playlistSpotifyUrl = finalUrl
                        )

                        _uiState.value = MoodUiState.Success(percursoFinal)

                    }.onFailure {
                        _uiState.value = MoodUiState.Error("Falha na IA: ${it.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = MoodUiState.Error(e.message ?: "Erro inesperado")
            }
        }
    }

    private suspend fun getDetailedPolyline(points: List<PontoMapa>): List<LatLng> {
        if (points.size < 2) return emptyList()
        return try {
            val origin = "${points.first().latitude},${points.first().longitude}"
            val dest = "${points.last().latitude},${points.last().longitude}"
            val waypoints = points.drop(1).dropLast(1)
                .joinToString("|") { "${it.latitude},${it.longitude}" }

            directionsService.getDirections(origin, dest, waypoints)
                ?.decodePolyline() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun List<LatLng>.encodeToPolylineString(): String =
        if (isEmpty()) "" else joinToString(",") { "${it.latitude},${it.longitude}" }
}