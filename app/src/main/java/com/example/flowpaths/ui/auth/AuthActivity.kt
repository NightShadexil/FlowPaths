package com.example.flowpaths.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.MainActivity
import com.example.flowpaths.ui.screens.AuthScreen
import com.example.flowpaths.ui.theme.FlowPathsAppTheme
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AuthActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private var sessionStatusJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FlowPathsAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthScreen()
                }
            }
        }

        startObservingSessionStatus()

        lifecycleScope.launch {
            intent?.let {
                FlowPathsApplication.supabaseClient.handleDeeplinks(it)
            }
        }
    }

    private fun startObservingSessionStatus() {
        sessionStatusJob = lifecycleScope.launch {
            FlowPathsApplication.supabaseClient.auth.sessionStatus.collect { status ->
                Log.d("AuthActivity", "🔎 Status da sessão mudou para: $status")
                when (status) {
                    is SessionStatus.Authenticated -> {
                        Log.d("AuthActivity", "🎉 Utilizador autenticado via OAuth! Navegando para MainActivity.")
                        navigateToMain()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        Log.d("AuthActivity", "ℹ️ Utilizador não autenticado.")
                    }
                    is SessionStatus.LoadingFromStorage -> {
                        Log.d("AuthActivity", "⏳ Carregando estado da autenticação a partir do armazenamento...")
                    }
                    is SessionStatus.NetworkError -> {
                        Log.e("AuthActivity", "❌ Erro de rede ao verificar autenticação: ${status.toString()}")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("AuthActivity", "🔁 onNewIntent chamado com data: ${intent?.data}")

        if (intent.data != null) {
            authViewModel.processOAuthCallback(intent)
        }
    }

    private fun navigateToMain() {
        sessionStatusJob?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionStatusJob?.cancel()
    }
}