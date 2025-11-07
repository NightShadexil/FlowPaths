package com.example.flowpaths.ui.screens

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.navigation.NavController
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.LightGrayBackground
import com.example.flowpaths.ui.theme.WhiteBackground
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Ecrã de arranque (Splash) que verifica a autenticação e redireciona.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Carregando sessão...", style = MaterialTheme.typography.bodyLarge)
    }
}