package com.example.flowpaths

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.flowpaths.data.remote.SpotifyManager
import com.example.flowpaths.navigation.FlowPathsNavHost
import com.example.flowpaths.ui.theme.FlowPathsAppTheme
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.SessionViewModel
import io.github.jan.supabase.gotrue.handleDeeplinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModel()
    private val mapViewModel: MapViewModel by viewModel()

    private lateinit var spotifyManager: SpotifyManager

    // Estado de controlo OAuth
    private var hasAttemptedSpotifyAuth = false
    private var spotifyAuthToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa Spotify Manager
        spotifyManager = SpotifyManager(this)

        setContent {
            FlowPathsAppTheme {
                FlowPathsNavHost()
            }
        }

        // Processa Deep Links do Supabase
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        // ========================================
        // 🎵 FLUXO SPOTIFY COMPLETO
        // ========================================

        if (!hasAttemptedSpotifyAuth) {
            Log.d("MainActivity", "🔐 Iniciando OAuth do Spotify...")

            spotifyManager.startAuthIfNeeded(
                onAuthStarted = {
                    hasAttemptedSpotifyAuth = true
                    Log.d("MainActivity", "📱 Popup OAuth aberto")
                }
            )
        } else if (spotifyAuthToken != null && !spotifyManager.isConnected()) {
            // Já tem token, tenta conectar App Remote
            Log.d("MainActivity", "🔌 Token já obtido, conectando App Remote...")
            connectSpotifyAppRemote()
        }
    }

    override fun onStop() {
        super.onStop()
        spotifyManager.disconnect()
    }

    /**
     * ✅ RECEBE RESPOSTA DO OAUTH FLOW
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        spotifyManager.handleAuthResponse(
            requestCode,
            resultCode,
            data,
            onSuccess = { token ->
                spotifyAuthToken = token
                Log.d("MainActivity", "✅ OAuth bem-sucedido! A conectar App Remote...")

                // Agora que tem token, conecta ao player
                connectSpotifyAppRemote()
            },
            onError = { error ->
                Log.e("MainActivity", "❌ Erro OAuth: $error")
                // Mostra snackbar ou toast ao utilizador (opcional)
            }
        )
    }

    /**
     * Conecta ao Spotify App Remote
     */
    private fun connectSpotifyAppRemote() {
        spotifyManager.connect(
            onConnected = { remote ->
                Log.d("MainActivity", "✅ Spotify App Remote ligado!")
                mapViewModel.attachSpotify(remote)
            },
            onFailure = { error ->
                Log.e("MainActivity", "⚠️ Erro ao conectar App Remote: ${error.message}")

                // Diagnóstico comum
                if (error.message?.contains("Could not resolve service") == true) {
                    Log.e("MainActivity", "💡 Dica: App Spotify não está instalado!")
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Processa Deep Links do Supabase (mantido igual)
     */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return

        Log.d("DEBUG_AUTH", "🔗 Link recebido: $uri")

        if (uri.toString().startsWith("flowpaths://auth-callback")) {
            val fragment = uri.fragment ?: ""
            val query = uri.query ?: ""

            val isRecovery = fragment.contains("type=recovery") ||
                    query.contains("type=recovery")

            lifecycleScope.launch {
                try {
                    if (isRecovery) {
                        Log.d("MainActivity", "🛑 Deep link de RECUPERAÇÃO detetado!")
                        sessionViewModel.setRecoveryMode()
                        delay(800) // Race condition guard
                    } else {
                        Log.d("MainActivity", "✅ Deep link de Login/Confirmação normal.")
                    }

                    FlowPathsApplication.supabaseClient.handleDeeplinks(intent)
                    Log.d("MainActivity", "✅ Link processado pelo Supabase.")

                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Erro ao processar Deep Link: ${e.message}")
                }
            }
        }
    }
}