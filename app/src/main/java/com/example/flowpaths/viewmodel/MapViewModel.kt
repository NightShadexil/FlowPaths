package com.example.flowpaths.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.flowpaths.data.remote.PercursoRecomendado
import com.example.flowpaths.data.location.FlowPathsLocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel para gerir o estado do mapa, incluindo percursos gerados por IA.
 * É um singleton Koin para partilhar o estado entre MoodAnalysisScreen e MainScreen.
 */
// 💡 ESTADO DO CICLO DE VIDA DO PERCURSO
sealed class RouteTrackingState {
    data object Idle : RouteTrackingState() // Nada a acontecer
    data object AwaitingRoute : RouteTrackingState() // Navegação para MoodAnalysis / PublicMap
    data object RouteGenerated : RouteTrackingState() // Rota da IA no mapa
    data object TrackingActive : RouteTrackingState() // Percurso em andamento (GPS ativo)
    data object TrackingCompleted : RouteTrackingState() // Percurso concluído
}

class MapViewModel(
    private val locationManager: FlowPathsLocationManager // INJETADO (definido como single no Koin)
) : ViewModel() {

    // Estado que contém a última recomendação de IA ativa
    private val _currentRoute = MutableStateFlow<PercursoRecomendado?>(null)
    val currentRoute = _currentRoute.asStateFlow()

    // LÊ o Flow público do LocationManager (incluindo o novo campo 'bearing')
    val userCurrentLocation = locationManager.userLocation

    // 💡 ESTADO DE ACOMPANHAMENTO GPS
    private val _trackingState = MutableStateFlow<RouteTrackingState>(RouteTrackingState.Idle)
    val trackingState = _trackingState.asStateFlow()

    /** * Define a rota e é chamado pela MoodAnalysisScreen.
     * Altera o estado para RouteGenerated.
     */

    override fun onCleared() {
        super.onCleared()
        // Stop location updates se o ViewModel for destruído (se fosse um ViewModel normal)
        // Como é um 'single' no Koin, isto só acontece se o Koin for parado.
        locationManager.stopLocationUpdates()
    }

    fun setRecommendedRoute(route: PercursoRecomendado) {
        _currentRoute.value = route
        // Forçar o estado para Rota Gerada (RouteGenerated)
        _trackingState.value = RouteTrackingState.RouteGenerated
        Log.d("MAP_VM_CHECK", "Rota definida: ${route.tipoPercurso}, Pontos: ${route.pontosChave.size}")
    }

    /** * 💡 NOVA FUNÇÃO: Inicia o acompanhamento GPS.
     * Altera o estado para TrackingActive.
     */
    fun startRouteTracking() {
        if (currentRoute.value != null && trackingState.value is RouteTrackingState.RouteGenerated) {
            // 1. Iniciar o Fused Location Provider
            locationManager.startLocationUpdates()

            // 2. Mudar o estado
            _trackingState.value = RouteTrackingState.TrackingActive
            Log.d("TRACKING", "A iniciar o acompanhamento do percurso!")
        }
    }

    /** * 💡 NOVA FUNÇÃO: Termina o percurso e prepara a navegação para o resumo.
     * Altera o estado para TrackingCompleted.
     */
    fun completeRoute() {
        if (trackingState.value is RouteTrackingState.TrackingActive) {
            // 1. Parar o Fused Location Provider
            locationManager.stopLocationUpdates()

            // 2. Mudar o estado
            _trackingState.value = RouteTrackingState.TrackingCompleted
            Log.d("TRACKING", "Percurso concluído. Redirecionar para o Resumo.")
        }
    }

    /** * Limpa a rota e reseta o estado para Idle.
     */
    fun clearRoute() {
        _currentRoute.value = null
        _trackingState.value = RouteTrackingState.Idle // Limpar a rota reseta o estado
        Log.d("TRACKING", "Rota e estado limpos para Idle.")
    }
}