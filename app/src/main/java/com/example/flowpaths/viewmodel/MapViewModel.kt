package com.example.flowpaths.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.BuildConfig
import com.example.flowpaths.data.location.FlowPathsLocationManager
import com.example.flowpaths.data.location.CurrentLocation
import com.example.flowpaths.data.models.DesafioBemEstar
import com.example.flowpaths.data.models.PercursoRecomendado
import com.example.flowpaths.data.models.PontoMapa
import com.example.flowpaths.data.models.TipoDesafio
import com.example.flowpaths.data.remote.WeatherService
import com.example.flowpaths.data.states.RouteTrackingState
import com.example.flowpaths.data.repository.RouteRepository
import com.google.android.gms.maps.model.LatLng
import com.spotify.android.appremote.api.SpotifyAppRemote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

// ================== DATA CLASSES ==================
data class WeatherInfo(val temp: Int, val description: String, val iconUrl: String)
data class SpotifyState(val isPlaying: Boolean, val trackName: String)

@Serializable
data class DirectionsResponseLocal(@SerialName("routes") val routes: List<RouteLocal> = emptyList())

@Serializable
data class RouteLocal(
    @SerialName("overview_polyline") val overviewPolyline: OverviewPolylineLocal,
    @SerialName("legs") val legs: List<LegLocal> = emptyList()
)

@Serializable
data class OverviewPolylineLocal(@SerialName("points") val points: String)

@Serializable
data class LegLocal(@SerialName("steps") val steps: List<StepLocal> = emptyList())

@Serializable
data class StepLocal(
    @SerialName("html_instructions") val htmlInstructions: String,
    @SerialName("start_location") val startLocation: LatLngLocal,
    @SerialName("end_location") val endLocation: LatLngLocal,
    @SerialName("distance") val distance: DistanceLocal,
    @SerialName("polyline") val polyline: OverviewPolylineLocal? = null
)

@Serializable
data class LatLngLocal(val lat: Double, val lng: Double)

@Serializable
data class DistanceLocal(val value: Int, val text: String)

@Serializable
data class OpenWeatherResponse(val main: MainData, val weather: List<WeatherItem>)

@Serializable
data class MainData(val temp: Double)

@Serializable
data class WeatherItem(val description: String, val icon: String)

// ================== VIEW MODEL ==================
class MapViewModel(
    application: Application,
    private val routeRepository: RouteRepository,
    private val locationManager: FlowPathsLocationManager,
    private val weatherService: WeatherService,
    private val httpClient: HttpClient
) : AndroidViewModel(application) {

    private val TAG = "MapViewModel"

    // ========== ESTADOS DE ROTA ==========
    private val _currentRoute = MutableStateFlow<PercursoRecomendado?>(null)
    val currentRoute = _currentRoute.asStateFlow()

    private val _routeChallenges = MutableStateFlow<List<DesafioBemEstar>>(emptyList())
    private var currentPercursoId: String? = null

    // ========== NAVEGAÇÃO ==========
    private var navigationSteps: List<StepLocal> = emptyList()
    private val _currentStepIndex = MutableStateFlow(0)
    private var isApproachingStart = false

    private val _directionsPolyline = MutableStateFlow<List<LatLng>?>(null)
    val directionsPolyline = _directionsPolyline.asStateFlow()

    private val _approachPolyline = MutableStateFlow<List<LatLng>?>(null)
    val approachPolyline = _approachPolyline.asStateFlow()

    private val _navigationInstruction = MutableStateFlow<String?>(null)
    val navigationInstruction = _navigationInstruction.asStateFlow()

    private val _trackingState = MutableStateFlow<RouteTrackingState>(RouteTrackingState.Idle)
    val trackingState = _trackingState.asStateFlow()

    // ========== LOCALIZAÇÃO ==========
    private val _nextWaypointIndex = MutableStateFlow(0)
    val nextWaypointIndex = _nextWaypointIndex.asStateFlow()

    val userCurrentLocation: StateFlow<Location?> = locationManager.userLocation
        .map { it?.toAndroidLocation() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ========== DESAFIOS ==========
    private val _activeChallenge = MutableStateFlow<DesafioBemEstar?>(null)
    val activeChallenge = _activeChallenge.asStateFlow()

    private val _isChallengeInProgress = MutableStateFlow(false)
    private val _waitingForMedia = MutableStateFlow(false)

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds = _timerSeconds.asStateFlow()

    private val _totalChallengeSeconds = MutableStateFlow(1)
    val totalChallengeSeconds = _totalChallengeSeconds.asStateFlow()

    // ========== CLIMA E SPOTIFY ==========
    private val _weatherInfo = MutableStateFlow<WeatherInfo?>(null)
    val weatherInfo = _weatherInfo.asStateFlow()

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var pendingSpotifyUrl: String? = null

    private val _spotifyState = MutableStateFlow(SpotifyState(isPlaying = false, trackName = "A aguardar..."))
    val spotifyState = _spotifyState.asStateFlow()

    // ========== GRAVAÇÃO INTERNA ==========
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private var timerJob: kotlinx.coroutines.Job? = null
    var hasCenteredRoute = false
    private var currentGeofenceRadius = 30.0

    // =====================================================================
    // NAVEGAÇÃO E LOCALIZAÇÃO (LINHA DINÂMICA)
    // =====================================================================
    fun onLocationUpdate(userLocation: Location) {
        if (_weatherInfo.value == null) updateWeather(userLocation)

        if (_trackingState.value is RouteTrackingState.TrackingActive) {
            val userLatLng = LatLng(userLocation.latitude, userLocation.longitude)

            if (isApproachingStart) {
                _approachPolyline.value = _approachPolyline.value?.let { points ->
                    if (points.isNotEmpty()) listOf(userLatLng) + points.drop(1) else listOf(userLatLng)
                }
            } else {
                _directionsPolyline.value = _directionsPolyline.value?.let { points ->
                    if (points.isNotEmpty()) listOf(userLatLng) + points.drop(1) else listOf(userLatLng)
                }
            }
            checkRouteProgress(userLocation)
        }
    }

    private fun updateNavigationLogic(userLocation: Location) {
        val route = _currentRoute.value ?: return

        if (isApproachingStart) {
            val startPoint = route.pontosParagem.firstOrNull() ?: return

            if (navigationSteps.isNotEmpty() && _currentStepIndex.value < navigationSteps.size) {
                val step = navigationSteps[_currentStepIndex.value]
                val cleanInstruction = android.text.Html.fromHtml(step.htmlInstructions, 0).toString()

                val stepEnd = Location("").apply { latitude = step.endLocation.lat; longitude = step.endLocation.lng }
                val distToTurn = userLocation.distanceTo(stepEnd).toInt()

                _navigationInstruction.value = "$cleanInstruction\n($distToTurn m)"

                if (distToTurn < 15) {
                    _currentStepIndex.value++
                }
            } else {
                val dist = distanceBetweenPoints(userLocation.latitude, userLocation.longitude, startPoint.latitude, startPoint.longitude).toInt()
                _navigationInstruction.value = "Siga em frente até ao início ($dist m)"
            }
        } else {
            if (navigationSteps.isNotEmpty()) {
                val currentIndex = _currentStepIndex.value
                if (currentIndex < navigationSteps.size) {
                    val step = navigationSteps[currentIndex]
                    val stepEnd = Location("").apply { latitude = step.endLocation.lat; longitude = step.endLocation.lng }
                    val distToTurn = userLocation.distanceTo(stepEnd).toInt()
                    val cleanInstruction = android.text.Html.fromHtml(step.htmlInstructions, 0).toString()

                    _navigationInstruction.value = "$cleanInstruction\n($distToTurn m)"

                    if (distToTurn < 15) { _currentStepIndex.value++ }
                } else {
                    _navigationInstruction.value = "Chegou ao destino!"
                }
            }
        }
    }

    // =====================================================================
    // LÓGICA DE DESAFIOS (ÁUDIO INTERNO + EXTERNO + FOTO)
    // =====================================================================

    // Mantida para compatibilidade com gravação externa
    fun onChallengeAccepted(desafio: DesafioBemEstar) {
        if (desafio.tipo == TipoDesafio.AUDIO) {
            spotifyAppRemote?.playerApi?.pause()
        }
    }

    // 🎙️ GRAVAÇÃO INTERNA (MediaRecorder)
    fun startInternalRecording() {
        try {
            spotifyAppRemote?.playerApi?.pause()
            val outputDir = getApplication<Application>().cacheDir
            audioFile = File.createTempFile("audio_challenge", ".mp3", outputDir)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(getApplication())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar gravação: ${e.message}")
        }
    }

    fun stopInternalRecordingAndUpload() {
        val desafio = _activeChallenge.value ?: return
        val loc = userCurrentLocation.value ?: return
        val pId = currentPercursoId ?: return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _waitingForMedia.value = true

            viewModelScope.launch(Dispatchers.IO) {
                val bytes = audioFile?.readBytes() ?: return@launch
                val result = routeRepository.uploadChallengeMedia(bytes, pId, desafio.id, TipoDesafio.AUDIO, loc)

                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        markChallengeAsCompleted(desafio.id)
                        clearActiveChallenge()
                        spotifyAppRemote?.playerApi?.resume()
                    } else {
                        _waitingForMedia.value = false
                    }
                    audioFile?.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar gravação: ${e.message}")
            _waitingForMedia.value = false
        }
    }

    // 🔊 GRAVAÇÃO EXTERNA (URI do sistema)
    fun completeChallengeWithAudio(desafio: DesafioBemEstar, audioUri: Uri) {
        val loc = userCurrentLocation.value ?: return
        val pId = currentPercursoId ?: return
        _waitingForMedia.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(audioUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Falha ao ler áudio")
                inputStream.close()

                val result = routeRepository.uploadChallengeMedia(bytes, pId, desafio.id, TipoDesafio.AUDIO, loc)

                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        markChallengeAsCompleted(desafio.id)
                        clearActiveChallenge()
                        spotifyAppRemote?.playerApi?.resume()
                    } else {
                        _waitingForMedia.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro upload áudio: ${e.message}")
                launch(Dispatchers.Main) { _waitingForMedia.value = false }
            }
        }
    }

    // 📷 FOTO
    fun completeChallengeWithPhoto(desafio: DesafioBemEstar, photo: Bitmap) {
        val loc = userCurrentLocation.value ?: return
        val pId = currentPercursoId ?: return
        _waitingForMedia.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                photo.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val result = routeRepository.uploadChallengeMedia(stream.toByteArray(), pId, desafio.id, TipoDesafio.FOTO, loc)
                launch(Dispatchers.Main) {
                    if (result.isSuccess) { markChallengeAsCompleted(desafio.id); clearActiveChallenge() }
                    else _waitingForMedia.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro foto: ${e.message}")
                launch(Dispatchers.Main) { _waitingForMedia.value = false }
            }
        }
    }

    fun completeActiveChallengeNoMedia() {
        val current = _activeChallenge.value ?: return
        timerJob?.cancel()
        markChallengeAsCompleted(current.id)
        clearActiveChallenge()
    }

    private fun markChallengeAsCompleted(id: String) {
        _routeChallenges.value = _routeChallenges.value.map { if (it.id == id) it.copy(statusConclusao = "CONCLUIDO") else it }
        _nextWaypointIndex.value++
    }

    private fun clearActiveChallenge() {
        _activeChallenge.value = null
        _isChallengeInProgress.value = false
        _waitingForMedia.value = false
        _timerSeconds.value = 0
    }

    fun dismissChallenge() {
        timerJob?.cancel()
        if (_activeChallenge.value?.tipo == TipoDesafio.AUDIO) spotifyAppRemote?.playerApi?.resume()
        clearActiveChallenge()
    }

    fun startChallengeTimer(desafio: DesafioBemEstar) {
        timerJob?.cancel()
        _timerSeconds.value = desafio.duracaoSegundos
        _totalChallengeSeconds.value = desafio.duracaoSegundos
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (_timerSeconds.value > 0) { delay(1000L); _timerSeconds.value -= 1 }
            launch(Dispatchers.Main) { completeActiveChallengeNoMedia() }
        }
    }

    // =====================================================================
    // CORE: TRACKING E DIRECTIONS API
    // =====================================================================

    fun setRecommendedRoute(route: PercursoRecomendado, percursoIdGeradoNaBD: String) {
        currentPercursoId = percursoIdGeradoNaBD
        _routeChallenges.value = route.desafios
        _currentRoute.value = route
        hasCenteredRoute = false
        _trackingState.value = RouteTrackingState.RouteGenerated
        _nextWaypointIndex.value = 0
        isApproachingStart = false
        _navigationInstruction.value = "Pronto para iniciar."

        val isStatic = route.pontosParagem.size > 1 && distanceBetween(route.pontosParagem.first(), route.pontosParagem.last()) < 50
        currentGeofenceRadius = if (isStatic) 12.0 else 30.0

        if (!route.polylineDetalhada.isNullOrEmpty()) {
            _directionsPolyline.value = if (route.polylineDetalhada.contains(",")) parseCsvPolyline(route.polylineDetalhada) else decodePolyline(route.polylineDetalhada)
        } else { fetchRoutePolyline(route.pontosParagem) }
    }

    fun startRouteTracking() {
        val route = _currentRoute.value ?: return
        val userLoc = userCurrentLocation.value ?: return
        val startPoint = route.pontosParagem.first()

        _trackingState.value = RouteTrackingState.TrackingActive
        isApproachingStart = true
        _navigationInstruction.value = "A traçar direções..."
        playPlaylistSeamlessly(route.playlistSpotifyUrl)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = fetchDirectionsFullResponse("${userLoc.latitude},${userLoc.longitude}", "${startPoint.latitude},${startPoint.longitude}")
                val firstRoute = result.routes.firstOrNull()
                val legs = firstRoute?.legs
                val overview = firstRoute?.overviewPolyline?.points

                launch(Dispatchers.Main) {
                    if (overview != null) _approachPolyline.value = decodePolyline(overview)
                    if (!legs.isNullOrEmpty()) {
                        navigationSteps = legs.flatMap { it.steps }
                        _currentStepIndex.value = 0
                        updateNavigationLogic(userLoc)
                    }
                    startLocationUpdates()
                }
            } catch (e: Exception) { Log.e(TAG, "Erro API Directions"); launch(Dispatchers.Main) { startLocationUpdates() } }
        }
    }

    private suspend fun fetchDirectionsFullResponse(origin: String, dest: String, waypoints: String = ""): DirectionsResponseLocal {
        return httpClient.get("https://maps.googleapis.com/maps/api/directions/json") {
            parameter("origin", origin); parameter("destination", dest)
            if (waypoints.isNotEmpty()) parameter("waypoints", waypoints)
            parameter("mode", "walking"); parameter("key", BuildConfig.CLOUD_API_KEY); parameter("language", "pt-PT")
        }.body()
    }

    private fun fetchMainRouteStepsAgain(route: PercursoRecomendado) {
        viewModelScope.launch(Dispatchers.IO) {
            val userLoc = userCurrentLocation.value ?: return@launch
            val dest = "${route.pontosParagem.last().latitude},${route.pontosParagem.last().longitude}"
            val waypoints = if (route.pontosParagem.size > 2) route.pontosParagem.subList(1, route.pontosParagem.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" } else ""
            try {
                val resp = fetchDirectionsFullResponse("${userLoc.latitude},${userLoc.longitude}", dest, waypoints)
                resp.routes.firstOrNull()?.legs?.let { legs ->
                    launch(Dispatchers.Main) {
                        navigationSteps = legs.flatMap { it.steps }
                        _currentStepIndex.value = 0
                        updateNavigationLogic(userLoc)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Erro steps percurso") }
        }
    }

    private fun checkRouteProgress(userLocation: Location) {
        val route = _currentRoute.value ?: return
        if (isApproachingStart) {
            val startPoint = route.pontosParagem.firstOrNull() ?: return
            val startLoc = Location("").apply { latitude = startPoint.latitude; longitude = startPoint.longitude }
            if (userLocation.distanceTo(startLoc) < 18.0) {
                isApproachingStart = false
                _approachPolyline.value = null
                _navigationInstruction.value = "No ponto de partida!"
                fetchMainRouteStepsAgain(route)
            }
        }
        updateNavigationLogic(userLocation)

        if (_activeChallenge.value == null && !_isChallengeInProgress.value) {
            _routeChallenges.value.filter { it.statusConclusao != "CONCLUIDO" }.forEach { desafio ->
                val target = Location("").apply { latitude = desafio.latitude ?: 0.0; longitude = desafio.longitude ?: 0.0 }
                if (userLocation.distanceTo(target) < currentGeofenceRadius) {
                    _activeChallenge.value = desafio; _isChallengeInProgress.value = true
                    return
                }
            }
        }
    }

    // =====================================================================
    // SPOTIFY CONTROLS
    // =====================================================================
    fun attachSpotify(remote: SpotifyAppRemote) {
        spotifyAppRemote = remote
        remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
            state.track?.let { _spotifyState.value = SpotifyState(!state.isPaused, it.name) }
        }
        pendingSpotifyUrl?.let { playPlaylistSeamlessly(it); pendingSpotifyUrl = null }
    }

    private fun playPlaylistSeamlessly(url: String?) {
        if (url.isNullOrEmpty()) return
        if (spotifyAppRemote == null || !spotifyAppRemote!!.isConnected) { pendingSpotifyUrl = url; return }
        val uri = getSpotifyUriFromUrl(url)
        spotifyAppRemote?.playerApi?.setShuffle(true)?.setResultCallback { attemptPlay(uri) }?.setErrorCallback { attemptPlay(uri) }
    }

    private fun attemptPlay(uri: String) = spotifyAppRemote?.playerApi?.play(uri)?.setErrorCallback { spotifyAppRemote?.playerApi?.resume() }
    fun togglePlayPause() = spotifyAppRemote?.playerApi?.playerState?.setResultCallback { if (it.isPaused) spotifyAppRemote?.playerApi?.resume() else spotifyAppRemote?.playerApi?.pause() }
    fun nextTrack() = spotifyAppRemote?.playerApi?.skipNext()
    fun previousTrack() = spotifyAppRemote?.playerApi?.skipPrevious()

    // =====================================================================
    // WEATHER
    // =====================================================================
    private fun updateWeather(location: Location) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = weatherService.getWeatherData(PontoMapa(location.latitude, location.longitude))
                response?.let {
                    val weather = it.weather.firstOrNull()
                    val desc = weather?.description ?: "Desconhecido"
                    _weatherInfo.value = WeatherInfo(it.main.temp.roundToInt(), desc, "https://openweathermap.org/img/wn/${weather?.icon}@2x.png")
                }
            } catch (e: Exception) { Log.e(TAG, "Erro clima") }
        }
    }

    // =====================================================================
    // LIFECYCLE & CLEANUP
    // =====================================================================
    fun completeRoute() {
        locationManager.stopLocationUpdates()
        _trackingState.value = RouteTrackingState.TrackingCompleted
        spotifyAppRemote?.playerApi?.pause()
    }

    fun clearRoute() {
        _currentRoute.value = null; _directionsPolyline.value = null; _approachPolyline.value = null
        _trackingState.value = RouteTrackingState.Idle; _nextWaypointIndex.value = 0
        clearActiveChallenge(); timerJob?.cancel(); spotifyAppRemote?.playerApi?.pause()
    }

    fun startLocationUpdates() = locationManager.startLocationUpdates()

    // =====================================================================
    // HELPERS E UTILITÁRIOS
    // =====================================================================
    private fun distanceBetween(p1: PontoMapa, p2: PontoMapa): Float {
        val res = FloatArray(1); Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res); return res[0]
    }

    private fun distanceBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1); Location.distanceBetween(lat1, lon1, lat2, lon2, res); return res[0]
    }

    private fun fetchRoutePolyline(points: List<PontoMapa>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (points.size < 2) return@launch
            val origin = "${points.first().latitude},${points.first().longitude}"
            val dest = "${points.last().latitude},${points.last().longitude}"
            val waypoints = if (points.size > 2) points.subList(1, points.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" } else ""
            try {
                val resp = fetchDirectionsFullResponse(origin, dest, waypoints)
                resp.routes.firstOrNull()?.overviewPolyline?.points?.let { encoded ->
                    launch(Dispatchers.Main) { _directionsPolyline.value = decodePolyline(encoded) }
                }
            } catch (e: Exception) { Log.e(TAG, "Erro polyline") }
        }
    }

    private fun parseCsvPolyline(csv: String): List<LatLng> {
        val list = mutableListOf<LatLng>()
        val parts = csv.split(",")
        for (i in parts.indices step 2) {
            try { list.add(LatLng(parts[i].toDouble(), parts[i + 1].toDouble())) } catch (e: Exception) {}
        }
        return list
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0; val len = encoded.length; var lat = 0; var lng = 0
        while (index < len) {
            var b: Int; var shift = 0; var result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    private fun getSpotifyUriFromUrl(url: String): String {
        if (url.startsWith("spotify:")) return url
        return try { "spotify:playlist:${url.substringAfterLast("/").substringBefore("?")}" }
        catch (e: Exception) { "spotify:playlist:37i9dQZF1DX3rxVfibe1L0" }
    }

    private fun CurrentLocation.toAndroidLocation(): Location = Location("fused").apply {
        latitude = this@toAndroidLocation.latitude
        longitude = this@toAndroidLocation.longitude
        bearing = this@toAndroidLocation.bearing
    }
}