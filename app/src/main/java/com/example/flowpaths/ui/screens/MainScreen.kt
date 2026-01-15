package com.example.flowpaths.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.flowpaths.R
import com.example.flowpaths.data.models.DesafioBemEstar
import com.example.flowpaths.data.models.TipoDesafio
import com.example.flowpaths.data.states.RouteTrackingState
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.LightGrayBackground
import com.example.flowpaths.ui.theme.WhiteBackground
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.TimerViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private enum class ChallengePhase { PENDING, RUNNING, COMPLETED }

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val backStackEntry = remember(navController) {
        navController.getBackStackEntry(Routes.PRIVATE_DASHBOARD) // usa a tua route real do Main
    }

    val mapViewModel: MapViewModel = koinViewModel(viewModelStoreOwner = backStackEntry)
    val timerVM: TimerViewModel = koinViewModel(viewModelStoreOwner = backStackEntry)

    // ====== Estados do VM ======
    val currentRoute by mapViewModel.currentRoute.collectAsState()
    val trackingState by mapViewModel.trackingState.collectAsState()
    val userLocation by mapViewModel.userCurrentLocation.collectAsState()
    val directionsPolyline by mapViewModel.directionsPolyline.collectAsState()
    val approachPolyline by mapViewModel.approachPolyline.collectAsState()
    val activeChallenge by mapViewModel.activeChallenge.collectAsState()
    val navInstruction by mapViewModel.navigationInstruction.collectAsState()
    val timerSeconds by timerVM.seconds.collectAsState()
    val nextWaypointIndex by mapViewModel.nextWaypointIndex.collectAsState()
    val weatherInfo by mapViewModel.weatherInfo.collectAsState()
    val spotifyState by mapViewModel.spotifyState.collectAsState()

    // áudio vindo do VM
    val isRecording by mapViewModel.isInternalRecordingActive.collectAsState()
    val isStarting by mapViewModel.isStartingRecording.collectAsState()

    // ====== Estado do popup (UI local) ======
    var shownChallenge by remember { mutableStateOf<DesafioBemEstar?>(null) }
    var phase by rememberSaveable  { mutableStateOf(ChallengePhase.PENDING) }
    var allowAccept by remember { mutableStateOf(false) }

    var reflectionText by rememberSaveable  { mutableStateOf("") }
    var feedbackText by rememberSaveable  { mutableStateOf("") }

    val totalTime = shownChallenge?.duracaoSegundos ?: 1

    LaunchedEffect(timerSeconds, shownChallenge?.id) {
        val sc = shownChallenge ?: return@LaunchedEffect
        if (timerSeconds == 0 && sc.duracaoSegundos > 0) {
            Log.d("MainScreen", "⏱️ Timer chegou a 0 para desafio ${sc.id}")
            phase = ChallengePhase.COMPLETED
            // opcional: podes mostrar uma mensagem local no popup (não no VM)
        }
    }

    val isRouteActive = trackingState is RouteTrackingState.RouteGenerated ||
            trackingState is RouteTrackingState.TrackingActive

    // ====== Permissões ======
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    var hasLocPerm by remember { mutableStateOf(hasLocationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val loc = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocPerm = loc
        if (loc) mapViewModel.startLocationUpdates()
    }

    LaunchedEffect(Unit) {
        val needLoc = !hasLocationPermission()
        val needAudio = !hasAudioPermission()
        if (needLoc || needAudio) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        } else {
            mapViewModel.startLocationUpdates()
        }
    }

    // ====== Camera launcher (Foto) ======
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val c = shownChallenge
        if (bitmap != null && c != null) {
            mapViewModel.completeChallengeWithPhoto(c, bitmap)
            // ⚠️ não forçamos fechar já; o VM limpa activeChallenge no sucesso.
            // Mas para UX, podemos fechar no imediato:
            shownChallenge = null
        }
    }

    // ====== Congelar activeChallenge -> shownChallenge ======
    LaunchedEffect(activeChallenge?.id) {
        Log.d("MainScreen", "📍 activeChallenge mudou: ${activeChallenge?.id}")

        // Se VM limpou o desafio (concluído/ignorado), fecha popup
        if (activeChallenge == null) {
            shownChallenge = null
            return@LaunchedEffect
        }

        // Captura apenas se não houver um popup já aberto
        if (shownChallenge?.id == null) {
            shownChallenge = activeChallenge
        }
    }

    // ====== Quando popup muda/fecha ======
    LaunchedEffect(shownChallenge?.id) {
        val sc = shownChallenge
        if (sc == null) {
            Log.d("MainScreen", "🧊 shownChallenge = null (popup fechado/limpo)")
        } else {
            Log.d("MainScreen", "🧊 Popup a mostrar: id=${sc.id} tipo=${sc.tipo} dur=${sc.duracaoSegundos}")
        }

        // reset UI local
        phase = ChallengePhase.PENDING
        allowAccept = false
        reflectionText = ""
        feedbackText = ""
        timerVM.reset()

        if (sc != null && sc.duracaoSegundos > 0) {
            timerVM.start(sc.duracaoSegundos)
            phase = ChallengePhase.RUNNING
        }

        // cancelar gravação se popup mudou/fechou
        if (sc?.tipo == TipoDesafio.AUDIO && isRecording) {
            Log.w("MainScreen", "🎙️ Popup mudou/fechou durante AUDIO -> cancelar gravação")
            mapViewModel.cancelInternalRecording()
        }

        // anti-click fantasma: só permite aceitar depois de um curto delay
        if (sc != null) {
            delay(800)
            allowAccept = true
        }
    }

    // ====== MAPA ======
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.26, -8.62), 15f)
    }

    var isFollowingUser by remember { mutableStateOf(false) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // ✅ ADD: flag para distinguir gesto vs animação tua
    var programmaticMove by remember { mutableStateOf(false) }

    // ✅ ADD: este LaunchedEffect entra AQUI
    LaunchedEffect(trackingState, userLocation, isMapLoaded) {
        if (isMapLoaded && trackingState is RouteTrackingState.TrackingActive) {
            userLocation?.let { loc ->
                programmaticMove = true
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18f)
                )
            }
        }
    }

    // ✅ ALTERA o teu listener que desligava follow
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            programmaticMove = false
        } else {
            if (isFollowingUser && !programmaticMove) {
                Log.d("MainScreen", "🖐️ Gesto do utilizador -> follow GPS OFF")
                isFollowingUser = false
            }
        }
    }

    LaunchedEffect(trackingState) {
        if (trackingState is RouteTrackingState.TrackingActive) {
            Log.d("MainScreen", "📌 TrackingActive -> follow GPS ON")
            isFollowingUser = true
        }
    }

    LaunchedEffect(directionsPolyline, isMapLoaded) {
        if (isMapLoaded && !mapViewModel.hasCenteredRoute && !directionsPolyline.isNullOrEmpty()) {
            try {
                isFollowingUser = false
                delay(100)
                val builder = LatLngBounds.builder()
                directionsPolyline!!.forEach { builder.include(it) }
                programmaticMove = true
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 200),
                    1500
                )
                mapViewModel.hasCenteredRoute = true
                hasCenteredOnUser = true
            } catch (e: Exception) {
                Log.e("MainScreen", "Erro zoom: ${e.message}")
            }
        }
    }

    LaunchedEffect(userLocation) {
        Log.d("MainScreen", "📍 UI recebeu userLocation=$userLocation")
        userLocation?.let { loc ->
            Log.d("MainScreen", "📍 UI recebeu userLocation=${loc.latitude},${loc.longitude}")

            mapViewModel.onLocationUpdate(loc)

            if (isMapLoaded && !hasCenteredOnUser && directionsPolyline.isNullOrEmpty()) {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f)
                )
                hasCenteredOnUser = true
            }

            if (isFollowingUser) {
                programmaticMove = true
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18f),
                    500
                )
            }
        }
    }

    // Back enquanto rota ativa
    BackHandler(enabled = isRouteActive) {
        // se popup aberto e estiver a gravar, cancela
        if (shownChallenge != null && (isRecording || isStarting)) {
            Log.w("MainScreen", "⬅️ Back com gravação ativa -> cancelar gravação")
            mapViewModel.cancelInternalRecording()
        }
        mapViewModel.clearRoute()
        if (trackingState is RouteTrackingState.TrackingActive) {
            navController.popBackStack()
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(LightGrayBackground, WhiteBackground))),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ===== Header =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRouteActive) {
                        IconButton(
                            onClick = {
                                if (shownChallenge != null && (isRecording || isStarting)) {
                                    mapViewModel.cancelInternalRecording()
                                }
                                mapViewModel.clearRoute()
                                navController.popBackStack()
                            },
                            modifier = Modifier.background(Color.White, CircleShape)
                        ) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                    } else Spacer(Modifier.size(48.dp))

                    Image(
                        painter = painterResource(R.drawable.flowpaths_logo),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )

                    IconButton(
                        onClick = { navController.navigate(Routes.PROFILE) },
                        modifier = Modifier.background(Color.White, CircleShape)
                    ) { Icon(Icons.Default.Person, null, tint = Color(0xFF00ACC1)) }
                }

                // ===== Dashboard =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(75.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weatherInfo?.let {
                        Card(modifier = Modifier.weight(0.35f).fillMaxHeight()) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(it.iconUrl, null, Modifier.size(28.dp))
                                Text("${it.temp}°C", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isRouteActive) {
                        Card(
                            modifier = Modifier.weight(0.65f).fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1DB954))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        spotifyState.trackName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        if (spotifyState.isPlaying) "A tocar" else "Pausado",
                                        color = Color.White.copy(0.8f),
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(onClick = { mapViewModel.togglePlayPause() }) {
                                    Icon(
                                        if (spotifyState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ===== Mapa =====
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(
                                isMyLocationEnabled = hasLocPerm,
                                mapType = MapType.HYBRID
                            ),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                compassEnabled = true
                            ),
                            onMapLoaded = { isMapLoaded = true }
                        ) {
                            approachPolyline?.let {
                                Polyline(
                                    points = it,
                                    color = Color.Gray,
                                    width = 15f,
                                    pattern = listOf(Dash(20f), Gap(10f))
                                )
                            }
                            directionsPolyline?.let {
                                Polyline(points = it, color = Color(0xFF00E5FF), width = 20f)
                            }

                            currentRoute?.pontosParagem?.forEachIndexed { index, ponto ->
                                val icon = if (index == nextWaypointIndex)
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                                else
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)

                                Marker(
                                    state = MarkerState(position = LatLng(ponto.latitude, ponto.longitude)),
                                    title = ponto.nome,
                                    icon = icon
                                )
                            }
                        }

                        // Navegação (top card)
                        if (trackingState is RouteTrackingState.TrackingActive && navInstruction != null) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(12.dp)
                                    .fillMaxWidth(0.9f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238).copy(0.9f))
                            ) {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Navigation, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text(navInstruction!!, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Timer visual (centro) — usa timerSeconds do VM (countdown)
                        if (timerSeconds > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(160.dp)
                                    .background(Color.Black.copy(0.7f), CircleShape)
                                    .border(4.dp, Color(0xFF00E5FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = {
                                        if (totalTime > 0) timerSeconds.toFloat() / totalTime.toFloat() else 0f
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 8.dp
                                )
                                Text(
                                    formatSeconds(timerSeconds),
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        FloatingActionButton(
                            onClick = { isFollowingUser = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            containerColor = Color.White
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = Color(0xFF00ACC1))
                        }
                    }
                }

                // ===== Botão inferior =====
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    val (label, icon, action) = when (trackingState) {
                        is RouteTrackingState.TrackingActive ->
                            Triple("Concluir", Icons.Default.Flag, {
                                mapViewModel.completeRoute()
                                navController.navigate(Routes.ROUTE_SUMMARY)
                            })

                        is RouteTrackingState.RouteGenerated ->
                            Triple("Iniciar", Icons.Default.PlayArrow, {
                                mapViewModel.startRouteTracking()
                                isFollowingUser = true
                            })

                        else ->
                            Triple("Analisar Humor", Icons.Default.Mood, {
                                navController.navigate(Routes.MOOD_ANALYSIS)
                            })
                    }

                    Button(
                        onClick = action,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                    ) {
                        Icon(icon, null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ===== POPUP =====
            shownChallenge?.let { c ->
                ChallengePopupOverlayV2(
                    desafio = c,
                    allowAccept = allowAccept,
                    phase = phase,
                    timerText = formatSeconds(timerSeconds),
                    isTimerRunning = timerSeconds > 0,

                    reflectionText = reflectionText,
                    feedbackText = feedbackText,
                    onReflectionChange = { reflectionText = it },
                    onFeedbackChange = { feedbackText = it },

                    isRecording = isRecording,
                    isStarting = isStarting,

                    onAccept = {
                        if (!allowAccept) return@ChallengePopupOverlayV2
                        Log.d("MainScreen", "✅ ACEITAR desafio ${c.id} tipo=${c.tipo}")

                        phase = ChallengePhase.RUNNING
                        mapViewModel.onChallengeAccepted(c)
                    },

                    onIgnore = {
                        Log.d("MainScreen", "➡️ IGNORAR desafio ${c.id}")
                        if (isRecording || isStarting) mapViewModel.cancelInternalRecording()
                        mapViewModel.dismissChallenge(ignore = true)
                        shownChallenge = null
                    },

                    // ✅ UM ÚNICO botão principal (guarda feedback/reflexão se houver + conclui por tipo)
                    onPrimaryFinish = {
                        Log.d("MainScreen", "➡️ PRIMARY finish desafio ${c.id} tipo=${c.tipo}")

                        val fb = feedbackText.trim()
                        val ref = reflectionText.trim()

                        when (c.tipo) {
                            TipoDesafio.FOTO -> {
                                Log.d("MainScreen", "➡️ Tirar foto desafio ${c.id}")
                                cameraLauncher.launch(null)
                            }

                            TipoDesafio.AUDIO -> {
                                Log.d("MainScreen", "➡️ Audio via PRIMARY (rec=$isRecording starting=$isStarting) desafio ${c.id}")

                                if (isStarting) return@ChallengePopupOverlayV2

                                if (!isRecording) {
                                    if (!hasAudioPermission()) {
                                        Log.e("MainScreen", "❌ Sem permissão RECORD_AUDIO. A pedir novamente...")
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                        return@ChallengePopupOverlayV2
                                    }
                                    mapViewModel.startInternalRecording()
                                } else {
                                    mapViewModel.stopInternalRecordingAndUpload()
                                    phase = ChallengePhase.COMPLETED
                                }
                            }

                            else -> {
                                // ✅ TEXTO / REFLEXAO / outros sem media: 1 chamada só
                                mapViewModel.finishNonMediaChallenge(
                                    desafioId = c.id,
                                    tipo = c.tipo,
                                    feedback = fb,
                                    reflection = ref
                                )
                                shownChallenge = null
                            }
                        }
                    }

                )
            }
        }
    }
}

@Composable
private fun ChallengePopupOverlayV2(
    desafio: DesafioBemEstar,
    allowAccept: Boolean,
    phase: ChallengePhase,
    timerText: String,
    isTimerRunning: Boolean,

    reflectionText: String,
    feedbackText: String,
    onReflectionChange: (String) -> Unit,
    onFeedbackChange: (String) -> Unit,

    isRecording: Boolean,
    isStarting: Boolean,

    onAccept: () -> Unit,
    onIgnore: () -> Unit,

    // ✅ único botão principal
    onPrimaryFinish: () -> Unit
) {
    val audioButtonText = when {
        isStarting -> "A iniciar..."
        isRecording -> "Parar e Enviar"
        else -> "Gravar Áudio"
    }

    AlertDialog(
        onDismissRequest = { /* bloqueia dismiss fora */ },
        title = { Text("🎯 Desafio: ${desafio.focoPsicologico}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(desafio.instrucao)

                Spacer(Modifier.height(12.dp))

                if (phase == ChallengePhase.PENDING) {
                    Text(
                        "Aceita o desafio para iniciar o temporizador.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (isTimerRunning) {
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                } else if (phase == ChallengePhase.RUNNING) {
                    Text(
                        "Tempo terminou. Agora podes concluir.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (desafio.tipo == TipoDesafio.TEXTO || desafio.tipo == TipoDesafio.REFLEXAO) {
                    OutlinedTextField(
                        value = reflectionText,
                        onValueChange = onReflectionChange,
                        label = { Text("Reflexão") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = phase != ChallengePhase.PENDING
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = onFeedbackChange,
                    label = { Text("Feedback (como te sentes agora?)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = phase != ChallengePhase.PENDING
                )
            }
        },

        confirmButton = {
            when {
                phase == ChallengePhase.PENDING -> {
                    Button(
                        onClick = onAccept,
                        enabled = allowAccept
                    ) { Text("Aceitar") }
                }

                else -> {
                    val label = when (desafio.tipo) {
                        TipoDesafio.FOTO -> "Tirar Foto"
                        TipoDesafio.AUDIO -> audioButtonText
                        else -> "Enviar & Concluir"
                    }

                    Button(
                        onClick = onPrimaryFinish,
                        enabled = when (desafio.tipo) {
                            TipoDesafio.AUDIO -> !isTimerRunning && !isStarting
                            else -> !isTimerRunning && !isStarting && !isRecording
                        }
                    ) { Text(label) }
                }
            }
        },

        dismissButton = {
            // ✅ sem botão separado de feedback
            TextButton(
                onClick = onIgnore,
                enabled = allowAccept && !isStarting
            ) { Text("Ignorar") }
        }
    )
}


private fun formatSeconds(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
