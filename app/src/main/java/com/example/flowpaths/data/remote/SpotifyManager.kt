package com.example.flowpaths.data.remote

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.flowpaths.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class SpotifyManager(private val activity: Activity) {

    private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
    private val REDIRECT_URI = "com.example.flowpaths://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    companion object {
        const val AUTH_REQUEST_CODE = 1337
    }

    /**
     * ✅ PASSO 1: Inicia OAuth Flow
     * Chama isto no onStart() da MainActivity
     */
    fun startAuthIfNeeded(onAuthStarted: () -> Unit) {
        // Validação de segurança
        if (CLIENT_ID.isEmpty()) {
            Log.e("SpotifyManager", "❌ SPOTIFY_CLIENT_ID está vazio!")
            return
        }

        // Verifica se já tem token salvo (opcional - adiciona depois)
        // Por agora, sempre força nova autenticação

        val request = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )
            .setScopes(arrayOf(
                "user-read-playback-state",
                "user-modify-playback-state",
                "user-read-currently-playing",
                "streaming",
                "app-remote-control"
            ))
            .build()

        Log.d("SpotifyManager", "🚀 A iniciar OAuth...")
        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
        onAuthStarted()
    }

    /**
     * ✅ PASSO 2: Processa resposta do OAuth
     * Chama isto no onActivityResult() da MainActivity
     */
    fun handleAuthResponse(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (requestCode != AUTH_REQUEST_CODE) return

        val response = AuthorizationClient.getResponse(resultCode, data)

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                Log.d("SpotifyManager", "✅ Token OAuth obtido: ${token.take(20)}...")

                // Guarda token (opcional - adiciona SharedPreferences depois)
                // Por agora, apenas passa para frente
                onSuccess(token)
            }

            AuthorizationResponse.Type.ERROR -> {
                val error = "Erro OAuth: ${response.error}"
                Log.e("SpotifyManager", "❌ $error")
                onError(error)
            }

            else -> {
                Log.w("SpotifyManager", "⚠️ OAuth cancelado pelo utilizador")
                onError("Autenticação cancelada")
            }
        }
    }

    /**
     * ✅ PASSO 3: Conecta ao App Remote (DEPOIS de ter token)
     */
    fun connect(
        onConnected: (SpotifyAppRemote) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        if (CLIENT_ID.isEmpty()) {
            Log.e("SpotifyManager", "❌ CLIENT_ID vazio!")
            onFailure(IllegalStateException("Client ID não configurado"))
            return
        }

        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true) // Mostra popup se necessário
            .build()

        Log.d("SpotifyManager", "🔌 A conectar ao App Remote...")

        SpotifyAppRemote.connect(
            activity.applicationContext,
            connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    spotifyAppRemote = appRemote
                    Log.d("SpotifyManager", "✅ App Remote conectado!")
                    onConnected(appRemote)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e("SpotifyManager", "❌ Falha ao conectar: ${throwable.message}")
                    throwable.printStackTrace()
                    onFailure(throwable)
                }
            }
        )
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
            Log.d("SpotifyManager", "🔌 Desconectado do Spotify")
        }
        spotifyAppRemote = null
    }

    fun isConnected(): Boolean = spotifyAppRemote?.isConnected == true
}