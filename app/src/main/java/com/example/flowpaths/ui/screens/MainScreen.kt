package com.example.flowpaths.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val mapViewModel: MapViewModel = koinViewModel(
        viewModelStoreOwner = activity ?: checkNotNull(context as? ComponentActivity) {
            "MainScreen precisa de um contexto ComponentActivity"
        }
    )

    // Estados
    val currentRoute by mapViewModel.currentRoute.collectAsState()
    val trackingState by mapViewModel.trackingState.collectAsState()
    val userLocation by mapViewModel.userCurrentLocation.collectAsState()
    val directionsPolyline by mapViewModel.directionsPolyline.collectAsState()
    val approachPolyline by mapViewModel.approachPolyline.collectAsState()
    val activeChallenge by mapViewModel.activeChallenge.collectAsState()
    val navInstruction by mapViewModel.navigationInstruction.collectAsState()
    val timerSeconds by mapViewModel.timerSeconds.collectAsState()
    val totalTime by mapViewModel.totalChallengeSeconds.collectAsState()
    val nextWaypointIndex by mapViewModel.nextWaypointIndex.collectAsState()
    val weatherInfo by mapViewModel.weatherInfo.collectAsState()
    val spotifyState by mapViewModel.spotifyState.collectAsState()

    var isRecording by remember { mutableStateOf(false) }

    val isRouteActive = trackingState is RouteTrackingState.RouteGenerated ||
            trackingState is RouteTrackingState.TrackingActive

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(41.26, -8.62), 15f)
    }

    var isFollowingUser by remember { mutableStateOf(false) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Launcher Foto
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null && activeChallenge != null) {
            mapViewModel.completeChallengeWithPhoto(activeChallenge!!, bitmap)
        }
    }

    // Permissões
    fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    var hasLocationPermission by remember { mutableStateOf(checkPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = isGranted
        if (isGranted) mapViewModel.startLocationUpdates()
    }

    LaunchedEffect(Unit) {
        if (!checkPermissions()) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO // Para o áudio interno
            ))
        } else {
            mapViewModel.startLocationUpdates()
        }
    }

    // Zoom Automático
    LaunchedEffect(directionsPolyline, isMapLoaded) {
        if (isMapLoaded && !mapViewModel.hasCenteredRoute && !directionsPolyline.isNullOrEmpty()) {
            try {
                isFollowingUser = false
                delay(100)
                val builder = LatLngBounds.builder()
                directionsPolyline!!.forEach { builder.include(it) }
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 200), 1500)
                mapViewModel.hasCenteredRoute = true
                hasCenteredOnUser = true
            } catch (e: Exception) { Log.e("MainScreen", "Erro zoom: ${e.message}") }
        }
    }

    // Centrar User
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            mapViewModel.onLocationUpdate(loc)
            if (isMapLoaded && !hasCenteredOnUser && directionsPolyline.isNullOrEmpty()) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f))
                hasCenteredOnUser = true
            }
            if (isFollowingUser) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 18f), 500)
            }
        }
    }

    BackHandler(enabled = isRouteActive) {
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
                horizontalAlignment = Alignment.CenterHorizontally // CORRIGIDO: Alignment
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, // CORRIGIDO: Arrangement
                    verticalAlignment = Alignment.CenterVertically // CORRIGIDO: Alignment
                ) {
                    if (isRouteActive) {
                        IconButton(
                            onClick = { mapViewModel.clearRoute(); navController.popBackStack() },
                            modifier = Modifier.background(Color.White, CircleShape).shadow(2.dp, CircleShape)
                        ) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                    } else Spacer(Modifier.size(48.dp))

                    Image(painter = painterResource(R.drawable.flowpaths_logo), null, Modifier.size(50.dp))

                    IconButton(
                        onClick = { navController.navigate(Routes.PROFILE) },
                        modifier = Modifier.background(Color.White, CircleShape).shadow(2.dp, CircleShape)
                    ) { Icon(Icons.Default.Person, null, tint = Color(0xFF00ACC1)) }
                }

                // Dashboard
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(75.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weatherInfo?.let {
                        Card(modifier = Modifier.weight(0.35f).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                                AsyncImage(it.iconUrl, null, Modifier.size(28.dp))
                                Text("${it.temp}°C", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (isRouteActive) {
                        Card(modifier = Modifier.weight(0.65f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1DB954))) {
                            // CORRIGIDO: Argumentos Row
                            Row(
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spotifyState.trackName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                    Text(if (spotifyState.isPlaying) "A tocar" else "Pausado", color = Color.White.copy(0.8f), fontSize = 10.sp)
                                }
                                IconButton(onClick = { mapViewModel.togglePlayPause() }) {
                                    Icon(if(spotifyState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Mapa
                Surface(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp
                ) {
                    Box(Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(isMyLocationEnabled = hasLocationPermission, mapType = MapType.HYBRID),
                            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                            onMapLoaded = { isMapLoaded = true }
                        ) {
                            approachPolyline?.let { Polyline(points = it, color = Color.Gray, width = 15f, pattern = listOf(Dash(20f), Gap(10f))) }
                            directionsPolyline?.let { Polyline(points = it, color = Color(0xFF00E5FF), width = 20f) }

                            currentRoute?.pontosParagem?.forEachIndexed { index, ponto ->
                                val icon = if (index == nextWaypointIndex) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN) else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)

                                // CORRIGIDO: MarkerState tipado
                                Marker(
                                    state = MarkerState(position = LatLng(ponto.latitude, ponto.longitude)),
                                    title = ponto.nome,
                                    icon = icon
                                )
                            }
                        }

                        // Navegação
                        if (trackingState is RouteTrackingState.TrackingActive && navInstruction != null) {
                            Card(
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(0.9f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238).copy(0.9f))
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Navigation, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text(navInstruction!!, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Timer
                        if (timerSeconds > 0) {
                            Box(modifier = Modifier.align(Alignment.Center).size(160.dp).background(Color.Black.copy(0.7f), CircleShape).border(4.dp, Color(0xFF00E5FF), CircleShape), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { if (totalTime > 0) timerSeconds.toFloat() / totalTime.toFloat() else 0f }, modifier = Modifier.fillMaxSize(), color = Color(0xFF00E5FF), strokeWidth = 8.dp)
                                Text(formatSeconds(timerSeconds), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        FloatingActionButton(onClick = { isFollowingUser = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Color.White) {
                            Icon(Icons.Default.MyLocation, null, tint = Color(0xFF00ACC1))
                        }
                    }
                }

                // Botão Inferior
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    val (label, icon, action) = when (trackingState) {
                        is RouteTrackingState.TrackingActive -> Triple("Concluir", Icons.Default.Flag, { mapViewModel.completeRoute(); navController.navigate(Routes.ROUTE_SUMMARY) })
                        // CORRIGIDO: startRouteTracking
                        is RouteTrackingState.RouteGenerated -> Triple("Iniciar", Icons.Default.PlayArrow, { mapViewModel.startRouteTracking(); isFollowingUser = true })
                        else -> Triple("Analisar Humor", Icons.Default.Mood, { navController.navigate(Routes.MOOD_ANALYSIS) })
                    }
                    Button(onClick = action, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))) {
                        Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Popup de Desafio
        if (activeChallenge != null && timerSeconds == 0) {
            ChallengePopupOverlay(
                desafio = activeChallenge!!,
                isRecording = isRecording,
                onDismiss = { if (!isRecording) mapViewModel.dismissChallenge() },
                onAccept = {
                    when (activeChallenge!!.tipo) {
                        TipoDesafio.FOTO -> cameraLauncher.launch(null)
                        TipoDesafio.AUDIO -> {
                            if (!isRecording) {
                                mapViewModel.startInternalRecording()
                                isRecording = true
                            } else {
                                mapViewModel.stopInternalRecordingAndUpload()
                                isRecording = false
                            }
                        }
                        else -> {
                            if (activeChallenge!!.duracaoSegundos > 0) mapViewModel.startChallengeTimer(activeChallenge!!)
                            else mapViewModel.completeActiveChallengeNoMedia()
                        }
                    }
                }
            )
        }
    }
}

fun formatSeconds(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun ChallengePopupOverlay(
    desafio: DesafioBemEstar,
    isRecording: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val icon = when (desafio.tipo) {
        TipoDesafio.FOTO -> Icons.Default.CameraAlt
        TipoDesafio.AUDIO -> Icons.Default.Mic
        else -> Icons.Default.Star
    }

    val buttonText = when (desafio.tipo) {
        TipoDesafio.FOTO -> "Tirar Foto"
        TipoDesafio.AUDIO -> if (isRecording) "Parar e Enviar" else "Gravar Áudio"
        else -> if (desafio.duracaoSegundos > 0) "Começar (${desafio.duracaoSegundos}s)" else "Concluir"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 Desafio: ${desafio.focoPsicologico}") },
        text = {
            Column {
                Text(desafio.instrucao)
                if (isRecording) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val infiniteTransition = rememberInfiniteTransition(label = "")
                        val alpha by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 0.2f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "")
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Red.copy(alpha)))
                        Spacer(Modifier.width(8.dp))
                        Text("A GRAVAR...", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary)
            ) { Text(buttonText) }
        },
        dismissButton = {
            if (!isRecording) {
                TextButton(onClick = onDismiss) { Text("Ignorar") }
            }
        }
    )
}