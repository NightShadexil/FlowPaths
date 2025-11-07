package com.example.flowpaths.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import com.example.flowpaths.R
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.LightGrayBackground
import com.example.flowpaths.ui.theme.WhiteBackground
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.RouteTrackingState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

import org.koin.androidx.compose.koinViewModel
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapProperties


/**
 * O Ecrã Principal (Dashboard) que mostra o mapa e os botões de ação.
 */
@Composable
fun MainScreen(
    navController: NavController,
    mapViewModel: MapViewModel = koinViewModel()
) {
    // 1. Observar Estados
    val currentRoute by mapViewModel.currentRoute.collectAsState()
    val trackingState by mapViewModel.trackingState.collectAsState()
    val userLocation by mapViewModel.userCurrentLocation.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Log.d("MAP_VM_CHECK", "MainScreen iniciado. Estado inicial: $trackingState")
    }

    // DEBUG: Confirma a leitura do estado na recomposição
    LaunchedEffect(currentRoute) {
        Log.d(
            "ROUTE_STATE_CHECK",
            "Recomposição de currentRoute: ${if (currentRoute != null) "OK: ${currentRoute?.pontosChave?.size} pontos" else "NULL"}"
        )
    }

    // Coordenadas de exemplo (Lisboa, Portugal) e localização fictícia (Mountain View)
    val defaultLocation = LatLng(38.7223, -9.1393)
    val mountainViewLocation = LatLng(37.4219983, -122.0840000)

    // Estado da câmara do mapa
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 12f)
    }

    // --- Lógica de Permissões ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

        if (granted) mapViewModel.startRouteTracking()
        else Log.d("LOCATION", "Permissão de localização negada.")
    }

    fun checkAndRequestLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) mapViewModel.startRouteTracking()
        else locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    // --- Fim Lógica de Permissões ---


    // 2. Efeito para redirecionar após terminar o percurso
    LaunchedEffect(trackingState) {
        if (trackingState is RouteTrackingState.TrackingCompleted) {
            // Isto assume que o RouteSummaryScreen foi adicionado ao NavHost
            navController.navigate(Routes.ROUTE_SUMMARY) {
                popUpTo(Routes.PRIVATE_DASHBOARD) { inclusive = false }
            }
        }
    }


    // Configurações da UI do Mapa
    val uiSettings = MapUiSettings(
        zoomControlsEnabled = true,
        compassEnabled = false,
        mapToolbarEnabled = false,
        myLocationButtonEnabled = false,
        scrollGesturesEnabled = true,
        zoomGesturesEnabled = true,
        tiltGesturesEnabled = false,
        rotationGesturesEnabled = false
    )

    // 3. Lógica Condicional para o Botão Principal
    val (buttonText, buttonAction) = when (trackingState) {
        is RouteTrackingState.TrackingActive -> {
            Pair("Terminar Percurso", { mapViewModel.completeRoute() })
        }

        is RouteTrackingState.RouteGenerated -> {
            Pair("Iniciar Acompanhamento", { checkAndRequestLocationPermissions() })
        }

        else -> {
            Pair("Analisar Vibe", { navController.navigate(Routes.MOOD_ANALYSIS) })
        }
    }


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.MOOD_ANALYSIS) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Mood, contentDescription = "Analisar Vibe")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(LightGrayBackground, WhiteBackground),
                        startY = 0.0f,
                        endY = 1000.0f
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Top Bar Personalizada ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(40.dp))
                    Image(
                        painter = painterResource(id = R.drawable.flowpaths_logo),
                        contentDescription = "Logotipo FlowPaths",
                        modifier = Modifier.size(100.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Aceder à Área Pessoal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { navController.navigate(Routes.PROFILE) }
                            .padding(8.dp)
                    )
                }

                // --- Cartão do Mapa (ÁREA CRÍTICA) ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    // SOLUÇÃO AGRESSIVA: Usa `key` para forçar a recriação do GoogleMap quando a rota muda de NULL para OK.
                    key(currentRoute != null) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = uiSettings,
                            // ✅ Camada Híbrida: Satélite + Nomes de Ruas
                            properties = MapProperties(mapType = MapType.HYBRID)
                        ) {

                            // 1. 💡 LÓGICA DE MOVIMENTO DA CÂMARA, ZOOM E RASTREAMENTO (BÚSSOLA)
                            LaunchedEffect(currentRoute, trackingState, userLocation) {
                                val route = currentRoute

                                // Variaveis para a nova posição (usamos o estado atual como base)
                                var target = cameraPositionState.position.target
                                var zoom = cameraPositionState.position.zoom
                                var tilt = cameraPositionState.position.tilt
                                var bearing = cameraPositionState.position.bearing // Rumo atual

                                // Priority 1: Tracking Ativo (Zoom apertado, rotação, inclinação - Modo Navegação)
                                if (trackingState is RouteTrackingState.TrackingActive && userLocation != null) {

                                    // ⚠️ CORREÇÃO: Ignorar a localização fictícia (Mountain View)
                                    if (userLocation!!.latitude != mountainViewLocation.latitude || userLocation!!.longitude != mountainViewLocation.longitude) {
                                        target = LatLng(
                                            userLocation!!.latitude,
                                            userLocation!!.longitude
                                        )
                                    } else if (route != null) {
                                        // Se ainda estiver a receber MV, e tiver uma rota válida, usa a rota como foco.
                                        target = LatLng(
                                            route.pontosChave.first().latitude,
                                            route.pontosChave.first().longitude
                                        )
                                    }

                                    zoom = 18f // Zoom de navegação apertado (como no Google Maps)
                                    tilt = 60f  // Inclinação para a vista 3D
                                    // ✅ USAR O BEARING REAL DO GPS PARA ROTAÇÃO DA CÂMARA
                                    bearing = userLocation!!.bearing

                                    Log.d(
                                        "MAP_ROUTE_CHECK",
                                        "CÂMARA: Rastreamento ativo, zoom/rotação no utilizador. Bearing: ${userLocation!!.bearing}"
                                    )

                                    // Priority 2: Rota Gerada (Zoom para mostrar todo o percurso - Visualização 2D)
                                } else if (route != null) {
                                    val firstPoint = route.pontosChave.firstOrNull()
                                    if (firstPoint != null) {
                                        target = LatLng(firstPoint.latitude, firstPoint.longitude)
                                        zoom = 14f // Zoom para ver o percurso
                                        tilt = 0f   // Volta à vista de cima (2D)
                                        bearing = 0f // Volta ao Norte
                                        Log.d(
                                            "MAP_ROUTE_CHECK",
                                            "CÂMARA: Rota gerada, movendo para o ponto de partida (Vista Geral)."
                                        )
                                    }
                                } else {
                                    Log.d(
                                        "MAP_ROUTE_CHECK",
                                        "CÂMARA: Estado inicial/limpo, sem movimento forçado."
                                    )
                                    return@LaunchedEffect // Sair se não houver rota/rastreamento
                                }

                                cameraPositionState.animate(
                                    update = CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder()
                                            .target(target)
                                            .zoom(zoom)
                                            .tilt(tilt)
                                            .bearing(bearing)
                                            .build()
                                    ),
                                    durationMs = 800 // Animação suave
                                )
                            }

                            // 2. DESENHO DOS MARCADORES E DA ROTA (Polyline)
                            currentRoute?.pontosChave?.let { pontos ->
                                Log.d(
                                    "MAP_ROUTE_CHECK",
                                    "✅ DESENHO INICIADO: ${pontos.size} marcadores e Polyline."
                                )

                                val pathPoints = pontos.map { LatLng(it.latitude, it.longitude) }

                                pathPoints.forEachIndexed { index, latLng ->
                                    Marker(
                                        state = MarkerState(position = latLng),
                                        title = pontos[index].nome ?: "Ponto ${index + 1}",
                                        snippet = currentRoute?.tipoPercurso ?: ""
                                    )
                                }

                                if (pathPoints.size >= 2) {
                                    Polyline(
                                        points = pathPoints,
                                        color = MaterialTheme.colorScheme.primary,
                                        width = 10f
                                    )
                                    Log.d("MAP_ROUTE_CHECK", "Polyline desenhada com sucesso.")
                                }
                            }

                            // 3. MARCADOR DE LOCALIZAÇÃO DO UTILIZADOR (Ícone de navegação)
                            userLocation?.let { location ->
                                // O ícone de navegação só precisa de ser um marcador simples no Compose Maps.
                                Marker(
                                    state = MarkerState(
                                        position = LatLng(
                                            location.latitude,
                                            location.longitude
                                        )
                                    ),
                                    title = "A sua localização",
                                    snippet = if (trackingState is RouteTrackingState.TrackingActive) "Rastreamento Ativo" else "GPS Ativo",
                                )
                                LaunchedEffect(location) {
                                    Log.d(
                                        "LOCATION_UI",
                                        "A localização do utilizador atualizou a UI: Lat=${location.latitude}"
                                    )
                                }
                            }
                        } // Fim GoogleMap
                    } // Fim key()
                }

                // --- Botão Principal de Ação (não alterado) ---
                Button(
                    onClick = buttonAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFF8A65), Color(0xFFFF5722))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Hiking,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = buttonText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}