package com.example.flowpaths.data.remote

import android.util.Base64
import android.util.Log
import com.example.flowpaths.BuildConfig
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Interfaces Retrofit
interface SpotifyAuthApi {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun getAccessToken(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyTokenResponse
}

interface SpotifySearchApi {
    @GET("v1/search")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("q") query: String,
        @Query("type") type: String = "playlist",
        @Query("limit") limit: Int = 1
    ): SpotifySearchResponse
}

// Data Classes
data class SpotifyTokenResponse(@SerializedName("access_token") val accessToken: String)
data class SpotifySearchResponse(val playlists: SpotifyPlaylists)
data class SpotifyPlaylists(val items: List<SpotifyPlaylist>)
data class SpotifyPlaylist(
    val name: String,
    @SerializedName("external_urls") val urls: Map<String, String>,
    val images: List<SpotifyImage>
)
data class SpotifyImage(val url: String)

class SpotifyService {

    // AGORA SEGURO: Lê do BuildConfig
    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET

    private val authApi = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/") // URL Correto para Auth
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyAuthApi::class.java)

    private val searchApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/") // URL Correto para API
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifySearchApi::class.java)

    suspend fun getPlaylistForVibe(vibe: String): SpotifyPlaylist? {
        return withContext(Dispatchers.IO) {
            try {
                // Passo 1: Obter Token
                val authString = "Basic " + Base64.encodeToString(
                    "$clientId:$clientSecret".toByteArray(),
                    Base64.NO_WRAP
                )
                val tokenResponse = authApi.getAccessToken(authString)
                val token = "Bearer ${tokenResponse.accessToken}"

                // Passo 2: Pesquisar Playlist baseada na Vibe
                // Ex: "Happy mood music", "Stressed calm music"
                val query = "$vibe mood music"
                val searchResponse = searchApi.search(token, query)

                searchResponse.playlists.items.firstOrNull()
            } catch (e: Exception) {
                Log.e("SpotifyService", "Erro ao buscar playlist: ${e.message}")
                null
            }
        }
    }
}