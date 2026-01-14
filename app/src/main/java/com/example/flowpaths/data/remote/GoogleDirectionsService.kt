package com.example.flowpaths.data.remote

import android.util.Log
import com.example.flowpaths.data.models.PontoMapa
import com.google.android.gms.maps.model.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

class GoogleDirectionsService(
    private val apiKey: String,
    private val httpClient: HttpClient
) : DirectionsService {

    override suspend fun getDirections(
        origin: String,
        destination: String,
        waypoints: String
    ): DirectionsResult? {
        return try {
            val response: GoogleDirectionsResponse = httpClient.get(
                "https://maps.googleapis.com/maps/api/directions/json"
            ) {
                parameter("origin", origin)
                parameter("destination", destination)
                if (waypoints.isNotEmpty()) {
                    parameter("waypoints", waypoints)
                }
                parameter("key", apiKey)
            }.body()

            val polyline = response.routes.firstOrNull()
                ?.overviewPolyline?.points

            polyline?.let { DirectionsResult(it) }
        } catch (e: Exception) {
            Log.e("GoogleDirectionsService", "Erro ao obter direções: ${e.message}")
            null
        }
    }

    // ✅ IMPLEMENTAR o novo método
    override suspend fun getDetailedPolyline(points: List<PontoMapa>): List<LatLng> {
        if (points.isEmpty()) return emptyList()

        return try {
            val origin = points.first().let { "${it.latitude},${it.longitude}" }
            val destination = points.last().let { "${it.latitude},${it.longitude}" }
            val waypoints = points.drop(1).dropLast(1)
                .joinToString("|") { "${it.latitude},${it.longitude}" }

            val directionsResult = getDirections(origin, destination, waypoints)
            directionsResult?.decodePolyline() ?: emptyList()
        } catch (e: Exception) {
            Log.e("GoogleDirectionsService", "Erro ao obter polyline detalhada: ${e.message}")
            emptyList()
        }
    }
}

@Serializable
data class GoogleDirectionsResponse(
    val routes: List<Route>
)

@Serializable
data class Route(
    @SerialName("overview_polyline")
    val overviewPolyline: OverviewPolyline
)

@Serializable
data class OverviewPolyline(
    val points: String
)