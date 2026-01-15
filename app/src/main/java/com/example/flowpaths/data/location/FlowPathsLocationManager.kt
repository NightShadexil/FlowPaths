package com.example.flowpaths.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Modelo de dados para a localização atual (opcional, mas limpa)
data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val bearing: Float = 0f // 💡 NOVO: Campo para o rumo/direção de movimento (usado para rotação do mapa)
)

/**
 * Encapsula a lógica da Fused Location Provider.
 * NOTA: O MapViewModel VAI CONSUMIR o userLocation flow.
 */
class FlowPathsLocationManager(
    private val context: Context // SÓ precisa do Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Flow para emitir a localização em tempo real
    private val _userLocation = MutableStateFlow<CurrentLocation?>(null)
    val userLocation: StateFlow<CurrentLocation?> = _userLocation

    private lateinit var locationRequest: LocationRequest

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val newLocation = CurrentLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = location.speed,
                    bearing = location.bearing // 💡 POPULAR O RUMO DO GPS AQUI
                )
                _userLocation.value = newLocation

                Log.d("LOCATION", "Localização atualizada: Lat=${newLocation.latitude}, Rumo=${newLocation.bearing}")
            }
        }
    }

    private var isStarted = false

    @SuppressLint("MissingPermission") // A permissão é verificada no MainScreen
    fun startLocationUpdates() {
        if (isStarted) {
            Log.d("LOCATION", "startLocationUpdates ignorado (já ativo).")
            return
        }
        isStarted = true

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(5))
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(2))
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnSuccessListener {
            Log.d("LOCATION", "Subscrição de localização bem-sucedida.")
        }.addOnFailureListener { e ->
            Log.e("LOCATION", "Falha ao iniciar updates de localização: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        Log.d("LOCATION", "A parar FusedLocationProviderClient updates.")
        isStarted = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}