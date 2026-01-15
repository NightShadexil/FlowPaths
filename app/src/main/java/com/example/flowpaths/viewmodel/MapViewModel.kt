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
import com.example.flowpaths.data.location.CurrentLocation
import com.example.flowpaths.data.location.FlowPathsLocationManager
import com.example.flowpaths.data.models.DesafioBemEstar
import com.example.flowpaths.data.models.PercursoRecomendado
import com.example.flowpaths.data.models.PontoMapa
import com.example.flowpaths.data.models.TipoDesafio
import com.example.flowpaths.data.remote.WeatherService
import com.example.flowpaths.data.repository.RouteRepository
import com.example.flowpaths.data.states.RouteTrackingState
import com.google.android.gms.maps.model.LatLng
import com.spotify.android.appremote.api.SpotifyAppRemote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// ================== UI DATA ==================
data class WeatherInfo(val temp: Int, val description: String, val iconUrl: String)
data class SpotifyState(val isPlaying: Boolean, val trackName: String)

// ================== DIRECTIONS DTOs ==================
@Serializable
data class DirectionsResponseLocal(
    @SerialName("routes") val routes: List<RouteLocal> = emptyList()
)

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

// ================== VIEW MODEL ==================
class MapViewModel(
    application: Application,
    private val routeRepository: RouteRepository,
    private val locationManager: FlowPathsLocationManager,
    private val weatherService: WeatherService,
    private val httpClient: HttpClient
) : AndroidViewModel(application) {

    private val TAG = "MapViewModel"

    // ---------------- ROUTE STATE ----------------
    private val _currentRoute = MutableStateFlow<PercursoRecomendado?>(null)
    val currentRoute = _currentRoute.asStateFlow()

    private val _routeChallenges = MutableStateFlow<List<DesafioBemEstar>>(emptyList())
    private var currentPercursoId: String? = null

    private val _trackingState = MutableStateFlow<RouteTrackingState>(RouteTrackingState.Idle)
    val trackingState = _trackingState.asStateFlow()

    private var pendingDirectionsToStart = false
    private var lastDirectionsOrigin: Location? = null

    // ---------------- NAVIGATION ----------------
    private var navigationSteps: List<StepLocal> = emptyList()
    private val _currentStepIndex = MutableStateFlow(0)
    private var isApproachingStart = false

    private val _directionsPolyline = MutableStateFlow<List<LatLng>?>(null)
    val directionsPolyline = _directionsPolyline.asStateFlow()

    private val _approachPolyline = MutableStateFlow<List<LatLng>?>(null)
    val approachPolyline = _approachPolyline.asStateFlow()

    private val _navigationInstruction = MutableStateFlow<String?>(null)
    val navigationInstruction = _navigationInstruction.asStateFlow()

    // ---------------- LOCATION ----------------
    private val _nextWaypointIndex = MutableStateFlow(0)
    val nextWaypointIndex = _nextWaypointIndex.asStateFlow()

    private val _userCurrentLocation = MutableStateFlow<Location?>(null)
    val userCurrentLocation: StateFlow<Location?> = _userCurrentLocation.asStateFlow()

    init {
        viewModelScope.launch {
            locationManager.userLocation.collect { cl ->
                if (cl == null) return@collect

                val loc = Location("fused").apply {
                    latitude = cl.latitude
                    longitude = cl.longitude
                    bearing = cl.bearing
                    speed = cl.speed
                }

                _userCurrentLocation.value = loc

                // ✅ AQUI é que a navegação deve correr SEMPRE
                onLocationUpdate(loc)
            }
        }
    }

    private var isFetchingApproachDirections = false
    private var lastApproachRequestAt: Long = 0L
    private val APPROACH_RETRY_MS = 10_000L

    // ---------------- CHALLENGES ----------------
    private val _activeChallenge = MutableStateFlow<DesafioBemEstar?>(null)
    val activeChallenge = _activeChallenge.asStateFlow()

    private val _isChallengeInProgress = MutableStateFlow(false)
    private val _waitingForMedia = MutableStateFlow(false)

    // Timer interno (usado pelo MainScreen)
    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds = _timerSeconds.asStateFlow()

    private val _totalChallengeSeconds = MutableStateFlow(1)
    val totalChallengeSeconds = _totalChallengeSeconds.asStateFlow()

    private var timerJob: Job? = null

    // ---------------- WEATHER + SPOTIFY ----------------
    private val _weatherInfo = MutableStateFlow<WeatherInfo?>(null)
    val weatherInfo = _weatherInfo.asStateFlow()

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var pendingSpotifyUrl: String? = null

    private val _spotifyState =
        MutableStateFlow(SpotifyState(isPlaying = false, trackName = "A aguardar..."))
    val spotifyState = _spotifyState.asStateFlow()

    // ---------------- INTERNAL AUDIO RECORDING ----------------
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val _isInternalRecordingActive = MutableStateFlow(false)
    val isInternalRecordingActive = _isInternalRecordingActive.asStateFlow()

    private val _isStartingRecording = MutableStateFlow(false)
    val isStartingRecording = _isStartingRecording.asStateFlow()

    private var isRecordingStarted = false

    // ---------------- MAP / GEOFENCE ----------------
    var hasCenteredRoute = false
    private var currentGeofenceRadius = 30.0

    // =====================================================================
    // LOCATION UPDATES (polyline dynamic + triggers)
    // =====================================================================
    fun onLocationUpdate(userLocation: Location) {
        Log.d(TAG, "📍 onLocationUpdate: ${userLocation.latitude},${userLocation.longitude}")

        if (_weatherInfo.value == null) updateWeather(userLocation)

        // ✅ se ainda não há rota, não faz sentido pedir directions nem fazer progress
        val route = _currentRoute.value ?: return

        // ✅ só pede approach directions quando tracking está ativo
        if (_trackingState.value is RouteTrackingState.TrackingActive) {
            requestApproachDirectionsIfNeeded(userLocation)

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


    // =====================================================================
    // NAVIGATION LOGIC
    // =====================================================================
    private fun updateNavigationLogic(userLocation: Location) {
        val route = _currentRoute.value ?: return

        if (isApproachingStart) {
            val startPoint = route.pontosParagem.firstOrNull() ?: return

            if (navigationSteps.isNotEmpty() && _currentStepIndex.value < navigationSteps.size) {
                val step = navigationSteps[_currentStepIndex.value]
                val cleanInstruction = android.text.Html.fromHtml(step.htmlInstructions, 0).toString()

                val stepEnd = Location("").apply {
                    latitude = step.endLocation.lat
                    longitude = step.endLocation.lng
                }
                val distToTurn = userLocation.distanceTo(stepEnd).toInt()

                _navigationInstruction.value = "$cleanInstruction\n($distToTurn m)"
                if (distToTurn < 15) _currentStepIndex.value++
            } else {
                val dist = distanceBetweenPoints(
                    userLocation.latitude, userLocation.longitude,
                    startPoint.latitude, startPoint.longitude
                ).toInt()
                _navigationInstruction.value = "Siga em frente até ao início ($dist m)"
            }
        } else {
            if (navigationSteps.isNotEmpty()) {
                val idx = _currentStepIndex.value
                if (idx < navigationSteps.size) {
                    val step = navigationSteps[idx]
                    val stepEnd = Location("").apply {
                        latitude = step.endLocation.lat
                        longitude = step.endLocation.lng
                    }
                    val distToTurn = userLocation.distanceTo(stepEnd).toInt()
                    val cleanInstruction = android.text.Html.fromHtml(step.htmlInstructions, 0).toString()

                    _navigationInstruction.value = "$cleanInstruction\n($distToTurn m)"
                    if (distToTurn < 15) _currentStepIndex.value++
                } else {
                    _navigationInstruction.value = "Chegou ao destino!"
                }
            }
        }
    }

    // =====================================================================
    // CHALLENGE ACCEPT / TIMER
    // =====================================================================
    fun onChallengeAccepted(desafio: DesafioBemEstar) {
        if (desafio.tipo == TipoDesafio.AUDIO) {
            spotifyAppRemote?.playerApi?.pause()
        }
    }

    /**
     * ✅ Timer não auto-conclui.
     * Quando chega a 0, apenas informa que acabou e deixa o UI concluir.
     */
    fun startChallengeTimer(desafio: DesafioBemEstar) {
        timerJob?.cancel()

        val total = maxOf(1, desafio.duracaoSegundos)
        _totalChallengeSeconds.value = total
        _timerSeconds.value = total

        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (_timerSeconds.value > 0) {
                delay(1000L)
                _timerSeconds.value -= 1
            }

            withContext(Dispatchers.Main) {
                Log.d(TAG, "⏱️ Timer terminou para desafio ${desafio.id} (${desafio.tipo})")
                _navigationInstruction.value = "Tempo terminou. Agora podes concluir."
            }
        }
    }

    // =====================================================================
    // ✅ SINGLE ENTRY POINT: finish NON-MEDIA challenge (texto/reflexão/outros)
    // =====================================================================
    fun finishNonMediaChallenge(
        desafioId: String,
        tipo: TipoDesafio,
        feedback: String,
        reflection: String
    ) {
        val percursoId = currentPercursoId
        if (percursoId.isNullOrBlank()) {
            Log.e(TAG, "❌ finishNonMediaChallenge: currentPercursoId=null/blank")
            return
        }

        val fb = feedback.trim()
        val ref = reflection.trim()

        Log.d(
            TAG,
            "✅ finishNonMediaChallenge: percurso=$percursoId desafio=$desafioId tipo=$tipo fbLen=${fb.length} refLen=${ref.length}"
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1) Guardar feedback (universal)
                if (fb.isNotEmpty()) {
                    val r1 = routeRepository.insertChallengeFeedback(
                        percursoId = percursoId,
                        desafioId = desafioId,
                        feedbackTexto = fb
                    )

                    if (r1.isSuccess) {
                        Log.d(TAG, "✅ insertChallengeFeedback OK (feedback)")
                    } else {
                        Log.e(TAG, "❌ insertChallengeFeedback FALHOU (feedback)", r1.exceptionOrNull())
                        // não aborta, mas fica registado
                    }
                }

                // 2) Guardar reflexão (se aplicável)
                if ((tipo == TipoDesafio.TEXTO || tipo == TipoDesafio.REFLEXAO) && ref.isNotEmpty()) {
                    val payload = "REFLEXAO: $ref"

                    val r2 = routeRepository.insertChallengeFeedback(
                        percursoId = percursoId,
                        desafioId = desafioId,
                        feedbackTexto = payload
                    )

                    if (r2.isSuccess) {
                        Log.d(TAG, "✅ insertChallengeFeedback OK (reflexão)")
                    } else {
                        Log.e(TAG, "❌ insertChallengeFeedback FALHOU (reflexão)", r2.exceptionOrNull())
                    }
                }

                // 3) Marcar desafio concluído na BD
                val done = routeRepository.markChallengeCompleted(percursoId, desafioId)
                if (done.isSuccess) {
                    Log.d(TAG, "✅ markChallengeCompleted OK")

                    withContext(Dispatchers.Main) {
                        // importante: isto é o que faz avançar para o próximo
                        markChallengeAsCompleted(desafioId)
                        clearActiveChallenge()

                        // ✅ refresh imediato da navegação
                        userCurrentLocation.value?.let { loc ->
                            updateNavigationLogic(loc)
                        }

                        // ✅ (muito útil) recalcular steps desde a posição atual
                        _currentRoute.value?.let { route ->
                            fetchMainRouteStepsAgain(route)
                        }
                    }
                } else {
                    Log.e(TAG, "❌ markChallengeCompleted FALHOU", done.exceptionOrNull())
                    withContext(Dispatchers.Main) {
                        _navigationInstruction.value = "Erro ao concluir desafio na BD."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ finishNonMediaChallenge crashou", e)
                withContext(Dispatchers.Main) {
                    _navigationInstruction.value = "Erro inesperado: ${e.message}"
                }
            }
        }
    }


    // =====================================================================
    // PHOTO
    // =====================================================================
    fun completeChallengeWithPhoto(desafio: DesafioBemEstar, photo: Bitmap) {
        val loc = userCurrentLocation.value ?: run {
            Log.e(TAG, "❌ completeChallengeWithPhoto: userLocation=null")
            return
        }
        val percursoIdRaw = currentPercursoId ?: run {
            Log.e(TAG, "❌ completeChallengeWithPhoto: currentPercursoId=null")
            return
        }

        val percursoId = uuidStringOrNull(percursoIdRaw, "percursoId") ?: return

        _waitingForMedia.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = ByteArrayOutputStream()
                photo.compress(Bitmap.CompressFormat.JPEG, 80, stream)

                val result = routeRepository.uploadChallengeMedia(
                    bytes = stream.toByteArray(),
                    percursoId = percursoId,
                    desafioId = desafio.id,
                    tipo = TipoDesafio.FOTO,
                    location = loc
                )

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Log.d(TAG, "✅ FOTO upload OK")
                        markChallengeAsCompleted(desafio.id)
                        clearActiveChallenge()
                    } else {
                        Log.e(TAG, "❌ FOTO upload falhou", result.exceptionOrNull())
                        _waitingForMedia.value = false
                        _navigationInstruction.value = "Erro no envio da foto."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ completeChallengeWithPhoto crash", e)
                withContext(Dispatchers.Main) { _waitingForMedia.value = false }
            }
        }
    }

    // =====================================================================
    // AUDIO - INTERNAL RECORDING
    // =====================================================================
    private var startRecordJob: Job? = null

    fun startInternalRecording() {
        if (_isStartingRecording.value || _isInternalRecordingActive.value) {
            Log.w(TAG, "🎙️ [AUDIO] Ignorado: já a iniciar=${_isStartingRecording.value} já a gravar=${_isInternalRecordingActive.value}")
            return
        }

        // cancela qualquer tentativa anterior presa
        startRecordJob?.cancel()

        _isStartingRecording.value = true
        Log.d(TAG, "🎙️ [AUDIO] Botão premido. A iniciar...")

        // watchdog: se ficar preso, faz reset para não bloquear UI
        val watchdog = viewModelScope.launch {
            delay(5000)
            if (_isStartingRecording.value && !_isInternalRecordingActive.value) {
                Log.e(TAG, "⛔🎙️ [AUDIO] Watchdog: preso em 'A iniciar...' > 5s. A cancelar e resetar.")
                cancelInternalRecording() // limpa recorder/file + estados
            }
        }

        startRecordJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1) Spotify pause (só loga a realidade)
                val remote = spotifyAppRemote
                Log.d(TAG, "🎧 Spotify remote: null=${remote == null} connected=${remote?.isConnected}")

                try {
                    remote?.playerApi?.pause()
                        ?.setResultCallback { Log.d(TAG, "🎧 Spotify pause OK") }
                        ?.setErrorCallback { err -> Log.e(TAG, "🎧 Spotify pause ERRO: ${err.message}") }
                } catch (e: Exception) {
                    Log.e(TAG, "🎧 Spotify pause exception", e)
                }

                // 2) folga pequena
                delay(200)

                // 3) prepara ficheiro
                val outputDir = getApplication<Application>().cacheDir
                audioFile = File.createTempFile("rec_", ".m4a", outputDir)
                Log.d(TAG, "🎙️ [AUDIO] Ficheiro: ${audioFile?.absolutePath}")

                // 4) cria recorder
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(getApplication())
                } else {
                    @Suppress("DEPRECATION") MediaRecorder()
                }

                // 5) config
                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(audioFile?.absolutePath)
                } ?: throw IllegalStateException("MediaRecorder null")

                // 6) prepare/start com “proteção”
                Log.d(TAG, "🎙️ [AUDIO] prepare()...")
                try {
                    mediaRecorder?.prepare()
                } catch (e: Exception) {
                    Log.e(TAG, "❌🎙️ [AUDIO] prepare() falhou", e)
                    throw e
                }

                Log.d(TAG, "🎙️ [AUDIO] start()...")
                try {
                    mediaRecorder?.start()
                } catch (e: Exception) {
                    Log.e(TAG, "❌🎙️ [AUDIO] start() falhou", e)
                    throw e
                }

                // 7) marca estados (em Main)
                withContext(Dispatchers.Main) {
                    isRecordingStarted = true
                    _isInternalRecordingActive.value = true
                    _isStartingRecording.value = false
                    Log.d(TAG, "✅🎙️ [AUDIO] GRAVAÇÃO ATIVA.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌🎙️ [AUDIO] Falha ao iniciar gravação", e)

                withContext(Dispatchers.Main) {
                    // reset estados
                    _isInternalRecordingActive.value = false
                    _isStartingRecording.value = false
                    isRecordingStarted = false
                }

                // limpa recursos
                try { mediaRecorder?.release() } catch (_: Exception) {}
                mediaRecorder = null

                try { audioFile?.delete() } catch (_: Exception) {}
                audioFile = null

            } finally {
                // watchdog já não é preciso
                watchdog.cancel()
            }
        }
    }

    fun stopInternalRecordingAndUpload() {
        Log.d(TAG, "🎙️ [AUDIO] A parar e enviar...")

        // só faz sentido em desafio AUDIO
        val desafio = _activeChallenge.value
        if (desafio?.tipo != TipoDesafio.AUDIO) {
            Log.w(TAG, "🎙️ [AUDIO] stop ignorado: activeChallenge não é AUDIO (é ${desafio?.tipo})")
            return
        }

        // Se ainda está a iniciar, não dá para parar "bem"
        // -> faz cleanup e sai (evita ficar preso no estado "A iniciar...")
        if (_isStartingRecording.value && !_isInternalRecordingActive.value) {
            Log.w(TAG, "🎙️ [AUDIO] stop durante STARTING. A cancelar e limpar (sem upload).")
            cancelInternalRecording()
            _navigationInstruction.value = "Falha ao iniciar gravação. Tenta novamente."
            return
        }

        if (!isRecordingStarted || !_isInternalRecordingActive.value) {
            Log.e(TAG, "⚠️ [AUDIO] stop: gravador não está ativo.")
            return
        }

        val loc = userCurrentLocation.value ?: run {
            Log.e(TAG, "❌ [AUDIO] stop: userLocation=null")
            return
        }

        val percursoIdRaw = currentPercursoId ?: run {
            Log.e(TAG, "❌ [AUDIO] stop: currentPercursoId=null")
            return
        }

        val percursoId = uuidStringOrNull(percursoIdRaw, "percursoId") ?: return
        val desafioId = uuidStringOrNull(desafio.id, "desafioId") ?: return

        viewModelScope.launch {
            var stopOk = false

            try {
                // ⚠️ stop/release podem ser chatos → faz em IO
                withContext(Dispatchers.IO) {
                    try {
                        mediaRecorder?.stop()
                        stopOk = true
                    } catch (e: Exception) {
                        // acontece se a gravação foi curta / não iniciou bem
                        Log.w(TAG, "🎙️ [AUDIO] stop() falhou (normal em alguns casos): ${e.message}")
                    } finally {
                        try { mediaRecorder?.release() } catch (_: Exception) {}
                        mediaRecorder = null
                    }
                }

                // marca estados imediatamente (UI reage já)
                _isInternalRecordingActive.value = false
                _isStartingRecording.value = false
                isRecordingStarted = false

                _waitingForMedia.value = true

                // Ler bytes do ficheiro em IO
                val bytes = withContext(Dispatchers.IO) {
                    audioFile?.readBytes()
                }

                if (!stopOk || bytes == null || bytes.isEmpty()) {
                    Log.e(TAG, "❌ [UPLOAD] áudio inválido/empty (stopOk=$stopOk bytes=${bytes?.size ?: 0})")
                    _waitingForMedia.value = false
                    _navigationInstruction.value = "Áudio inválido. Tenta gravar novamente."
                    // cleanup file
                    withContext(Dispatchers.IO) { try { audioFile?.delete() } catch (_: Exception) {} }
                    audioFile = null
                    return@launch
                }

                Log.d(TAG, "☁️ [UPLOAD] bytes=${bytes.size} percurso=$percursoId desafio=$desafioId")

                val result = withContext(Dispatchers.IO) {
                    routeRepository.uploadChallengeMedia(
                        bytes = bytes,
                        percursoId = percursoId,
                        desafioId = desafioId,
                        tipo = TipoDesafio.AUDIO,
                        location = loc
                    )
                }

                if (result.isSuccess) {
                    Log.d(TAG, "✅ [UPLOAD] OK")
                    markChallengeAsCompleted(desafioId)
                    clearActiveChallenge()
                    spotifyAppRemote?.playerApi?.resume()
                } else {
                    Log.e(TAG, "❌ [UPLOAD] Falhou", result.exceptionOrNull())
                    _waitingForMedia.value = false
                    _navigationInstruction.value = "Erro no envio do áudio."
                }

                // cleanup file
                withContext(Dispatchers.IO) { try { audioFile?.delete() } catch (_: Exception) {} }
                audioFile = null

            } catch (e: Exception) {
                Log.e(TAG, "❌ [AUDIO] ERRO AO PARAR/ENVIAR", e)
                _waitingForMedia.value = false
                _navigationInstruction.value = "Erro ao processar áudio."
            } finally {
                // 🔒 garante que nunca ficas preso em "A iniciar..."
                withContext(NonCancellable) {
                    _isStartingRecording.value = false
                    if (_isInternalRecordingActive.value) _isInternalRecordingActive.value = false
                }
            }
        }
    }

    fun cancelInternalRecording() {
        Log.d(TAG, "🎙️ [AUDIO] Cancelar gravação (sem upload)")

        try {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
        } finally {
            mediaRecorder = null

            // Só apagar ficheiro se não estivermos a fazer upload
            if (!_waitingForMedia.value) {
                try { audioFile?.delete() } catch (_: Exception) {}
                audioFile = null
            }

            isRecordingStarted = false
            _isInternalRecordingActive.value = false
            _isStartingRecording.value = false
            _waitingForMedia.value = false
        }
    }

    // =====================================================================
    // CHALLENGE FLOW HELPERS
    // =====================================================================
    private fun markChallengeAsCompleted(desafioId: String) {
        _routeChallenges.value = _routeChallenges.value.map {
            if (it.id == desafioId) it.copy(statusConclusao = "CONCLUIDO") else it
        }

        _nextWaypointIndex.value = _nextWaypointIndex.value + 1
        _navigationInstruction.value = "Desafio concluído! Siga para o próximo local."

        Log.d(TAG, "✅ Desafio $desafioId marcado CONCLUIDO (local). next=${_nextWaypointIndex.value}")

        userCurrentLocation.value?.let { updateNavigationLogic(it) }
    }

    private fun clearActiveChallenge() {
        _activeChallenge.value = null
        _isChallengeInProgress.value = false
        _waitingForMedia.value = false
        _timerSeconds.value = 0
        timerJob?.cancel()
        timerJob = null
    }

    fun dismissChallenge(ignore: Boolean = true) {
        val desafio = _activeChallenge.value ?: return
        timerJob?.cancel()
        timerJob = null

        if (ignore) {
            _routeChallenges.value = _routeChallenges.value.map {
                if (it.id == desafio.id) it.copy(statusConclusao = "IGNORADO") else it
            }
            _nextWaypointIndex.value = _nextWaypointIndex.value + 1
            _navigationInstruction.value = "Desafio ignorado. Siga para o próximo local."
        } else {
            markChallengeAsCompleted(desafio.id)
        }

        if (desafio.tipo == TipoDesafio.AUDIO) spotifyAppRemote?.playerApi?.resume()
        clearActiveChallenge()
    }

    // =====================================================================
    // ROUTE TRACKING + DIRECTIONS
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

        val isStatic = route.pontosParagem.size > 1 &&
                distanceBetween(route.pontosParagem.first(), route.pontosParagem.last()) < 50

        currentGeofenceRadius = if (isStatic) 12.0 else 30.0

        if (!route.polylineDetalhada.isNullOrEmpty()) {
            _directionsPolyline.value =
                if (route.polylineDetalhada.contains(",")) parseCsvPolyline(route.polylineDetalhada)
                else decodePolyline(route.polylineDetalhada)
        } else {
            fetchRoutePolyline(route.pontosParagem)
        }
    }

    fun startRouteTracking() {
        Log.d(TAG, "🚦 startRouteTracking() chamado")

        val route = _currentRoute.value ?: return
        val userLoc = userCurrentLocation.value ?: run {
            _navigationInstruction.value = "A obter GPS..."
            return
        }

        _trackingState.value = RouteTrackingState.TrackingActive
        isApproachingStart = true
        _navigationInstruction.value = "A traçar direções..."
        playPlaylistSeamlessly(route.playlistSpotifyUrl)

        navigationSteps = emptyList()
        _currentStepIndex.value = 0
        _approachPolyline.value = null

        // ✅ NÃO chamar startLocationUpdates() aqui
        requestApproachDirectionsIfNeeded(userLoc)
    }

    private fun requestApproachDirectionsIfNeeded(userLoc: Location) {
        val route = _currentRoute.value ?: run {
            Log.e(TAG, "❌ requestApproachDirectionsIfNeeded: currentRoute=null")
            return
        }

        // Só interessa quando estamos a seguir rota e ainda a aproximar do início
        if (_trackingState.value !is RouteTrackingState.TrackingActive) return
        if (!isApproachingStart) return

        // Já temos algo? então não pede outra vez
        val alreadyHaveSteps = navigationSteps.isNotEmpty()
        val alreadyHavePolyline = _approachPolyline.value?.isNotEmpty() == true
        if (alreadyHaveSteps || alreadyHavePolyline) return

        // Anti-spam
        val now = System.currentTimeMillis()
        if (isFetchingApproachDirections) return
        if (now - lastApproachRequestAt < APPROACH_RETRY_MS) return

        val startPoint = route.pontosParagem.firstOrNull() ?: run {
            Log.e(TAG, "❌ requestApproachDirectionsIfNeeded: sem pontosParagem")
            _navigationInstruction.value = "Erro: rota sem pontos."
            return
        }

        isFetchingApproachDirections = true
        lastApproachRequestAt = now

        Log.d(
            TAG,
            "🧭 A pedir DIRECTIONS (approach) origin=${userLoc.latitude},${userLoc.longitude} dest=${startPoint.latitude},${startPoint.longitude}"
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = fetchDirectionsFullResponse(
                    origin = "${userLoc.latitude},${userLoc.longitude}",
                    dest = "${startPoint.latitude},${startPoint.longitude}"
                )

                val r = resp.routes.firstOrNull()
                val overview = r?.overviewPolyline?.points
                val legs = r?.legs

                withContext(Dispatchers.Main) {
                    if (!overview.isNullOrEmpty()) {
                        _approachPolyline.value = decodePolyline(overview)
                    }

                    if (!legs.isNullOrEmpty()) {
                        navigationSteps = legs.flatMap { it.steps }
                        _currentStepIndex.value = 0
                        updateNavigationLogic(userLoc)
                        Log.d(TAG, "✅ Directions OK (approach). steps=${navigationSteps.size}")
                    } else {
                        _navigationInstruction.value = "Siga em frente até ao início."
                        Log.w(TAG, "⚠️ Directions: legs vazios (approach)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Directions approach falhou", e)
                withContext(Dispatchers.Main) {
                    _navigationInstruction.value = "Siga em frente até ao início."
                    updateNavigationLogic(userLoc)
                }
            } finally {
                isFetchingApproachDirections = false
            }
        }
    }


    private suspend fun fetchDirectionsFullResponse(
        origin: String,
        dest: String,
        waypoints: String = ""
    ): DirectionsResponseLocal {
        return httpClient.get("https://maps.googleapis.com/maps/api/directions/json") {
            parameter("origin", origin)
            parameter("destination", dest)
            if (waypoints.isNotEmpty()) parameter("waypoints", waypoints)
            parameter("mode", "walking")
            parameter("key", BuildConfig.CLOUD_API_KEY)
            parameter("language", "pt-PT")
        }.body()
    }

    private fun fetchMainRouteStepsAgain(route: PercursoRecomendado) {
        viewModelScope.launch(Dispatchers.IO) {
            val userLoc = userCurrentLocation.value ?: return@launch
            val dest = "${route.pontosParagem.last().latitude},${route.pontosParagem.last().longitude}"
            val waypoints =
                if (route.pontosParagem.size > 2)
                    route.pontosParagem.subList(1, route.pontosParagem.size - 1)
                        .joinToString("|") { "${it.latitude},${it.longitude}" }
                else ""

            try {
                val resp = fetchDirectionsFullResponse(
                    origin = "${userLoc.latitude},${userLoc.longitude}",
                    dest = dest,
                    waypoints = waypoints
                )

                resp.routes.firstOrNull()?.legs?.let { legs ->
                    withContext(Dispatchers.Main) {
                        navigationSteps = legs.flatMap { it.steps }
                        _currentStepIndex.value = 0
                        updateNavigationLogic(userLoc)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro steps percurso", e)
            }
        }
    }

    private fun checkRouteProgress(userLocation: Location) {
        val route = _currentRoute.value ?: return

        if (isApproachingStart) {
            val startPoint = route.pontosParagem.firstOrNull() ?: return
            val startLoc = Location("").apply {
                latitude = startPoint.latitude
                longitude = startPoint.longitude
            }

            if (userLocation.distanceTo(startLoc) < 18.0) {
                isApproachingStart = false
                _approachPolyline.value = null
                _navigationInstruction.value = "No ponto de partida!"

                fetchMainRouteStepsAgain(route)

                // ✅ fallback imediato caso fetch demore / falhe
                updateNavigationLogic(userLocation)
            }
        }

        updateNavigationLogic(userLocation)

        if (_activeChallenge.value == null && !_isChallengeInProgress.value) {
            _routeChallenges.value
                .filter { it.statusConclusao != "CONCLUIDO" && it.statusConclusao != "IGNORADO" }
                .forEach { desafio ->
                    val target = Location("").apply {
                        latitude = desafio.latitude ?: 0.0
                        longitude = desafio.longitude ?: 0.0
                    }
                    if (userLocation.distanceTo(target) < currentGeofenceRadius) {
                        _activeChallenge.value = desafio
                        _isChallengeInProgress.value = true
                        return
                    }
                }
        }
    }

    // =====================================================================
    // SPOTIFY
    // =====================================================================
    fun attachSpotify(remote: SpotifyAppRemote) {
        spotifyAppRemote = remote
        remote.playerApi.subscribeToPlayerState().setEventCallback { state ->
            state.track?.let {
                _spotifyState.value = SpotifyState(!state.isPaused, it.name)
            }
        }
        pendingSpotifyUrl?.let { playPlaylistSeamlessly(it); pendingSpotifyUrl = null }
    }

    private fun playPlaylistSeamlessly(url: String?) {
        if (url.isNullOrEmpty()) return
        if (spotifyAppRemote == null || spotifyAppRemote?.isConnected != true) {
            pendingSpotifyUrl = url
            return
        }
        val uri = getSpotifyUriFromUrl(url)
        spotifyAppRemote?.playerApi?.setShuffle(true)
            ?.setResultCallback { attemptPlay(uri) }
            ?.setErrorCallback { attemptPlay(uri) }
    }

    private fun attemptPlay(uri: String) {
        spotifyAppRemote?.playerApi?.play(uri)
            ?.setErrorCallback { spotifyAppRemote?.playerApi?.resume() }
    }

    fun togglePlayPause() {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback {
            if (it.isPaused) spotifyAppRemote?.playerApi?.resume()
            else spotifyAppRemote?.playerApi?.pause()
        }
    }

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
                    _weatherInfo.value = WeatherInfo(
                        it.main.temp.roundToInt(),
                        desc,
                        "https://openweathermap.org/img/wn/${weather?.icon}@2x.png"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro clima", e)
            }
        }
    }

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    fun completeRoute() {
        locationManager.stopLocationUpdates()
        _trackingState.value = RouteTrackingState.TrackingCompleted
        spotifyAppRemote?.playerApi?.pause()
    }

    fun clearRoute() {
        cancelInternalRecording()

        _currentRoute.value = null
        _directionsPolyline.value = null
        _approachPolyline.value = null
        _trackingState.value = RouteTrackingState.Idle
        _nextWaypointIndex.value = 0

        clearActiveChallenge()
        spotifyAppRemote?.playerApi?.pause()
    }

    fun startLocationUpdates() = locationManager.startLocationUpdates()

    // =====================================================================
    // HELPERS
    // =====================================================================
    private fun uuidStringOrNull(id: String, label: String): String? {
        return try {
            UUID.fromString(id).toString()
        } catch (e: Exception) {
            Log.e(TAG, "❌ $label inválido: '$id' (${e.message})")
            null
        }
    }

    private fun distanceBetween(p1: PontoMapa, p2: PontoMapa): Float {
        val res = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
        return res[0]
    }

    private fun distanceBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }

    private fun fetchRoutePolyline(points: List<PontoMapa>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (points.size < 2) return@launch

            val origin = "${points.first().latitude},${points.first().longitude}"
            val dest = "${points.last().latitude},${points.last().longitude}"
            val waypoints =
                if (points.size > 2)
                    points.subList(1, points.size - 1)
                        .joinToString("|") { "${it.latitude},${it.longitude}" }
                else ""

            try {
                val resp = fetchDirectionsFullResponse(origin, dest, waypoints)
                resp.routes.firstOrNull()?.overviewPolyline?.points?.let { encoded ->
                    withContext(Dispatchers.Main) { _directionsPolyline.value = decodePolyline(encoded) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro polyline", e)
            }
        }
    }

    private fun parseCsvPolyline(csv: String): List<LatLng> {
        val list = mutableListOf<LatLng>()
        val parts = csv.split(",")
        for (i in parts.indices step 2) {
            try {
                list.add(LatLng(parts[i].toDouble(), parts[i + 1].toDouble()))
            } catch (_: Exception) {}
        }
        return list
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    private fun getSpotifyUriFromUrl(url: String): String {
        if (url.startsWith("spotify:")) return url
        return try {
            "spotify:playlist:${url.substringAfterLast("/").substringBefore("?")}"
        } catch (_: Exception) {
            "spotify:playlist:37i9dQZF1DX3rxVfibe1L0"
        }
    }

    private fun CurrentLocation.toAndroidLocation(): Location = Location("fused").apply {
        latitude = this@toAndroidLocation.latitude
        longitude = this@toAndroidLocation.longitude
        bearing = this@toAndroidLocation.bearing
    }
}