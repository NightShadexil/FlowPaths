package com.example.flowpaths.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.screens.AuthScreen
import com.example.flowpaths.ui.screens.MainScreen
import com.example.flowpaths.ui.screens.ProfileScreen
import com.example.flowpaths.ui.screens.SplashScreen
import com.example.flowpaths.ui.screens.WelcomeScreen
import com.example.flowpaths.ui.screens.MoodAnalysisScreen
import com.example.flowpaths.ui.screens.RouteSummaryScreen
import com.example.flowpaths.viewmodel.SessionViewModel
import com.example.flowpaths.viewmodel.SessionState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel // 💡 Importar Koin

@Composable
fun FlowPathsNavHost() {
    val navController: NavHostController = rememberNavController()

    // ViewModel da sessão (Injetado pelo Koin)
    // Usamos koinViewModel() para obter a instância global
    val sessionViewModel: SessionViewModel = koinViewModel()

    val sessionState by sessionViewModel.sessionState.collectAsState()

    // 🔁 Redirecionar conforme o estado da sessão (Lógica de Arranque)
    LaunchedEffect(sessionState) {
        val destination = when (sessionState) {
            is SessionState.Authenticated -> Routes.PRIVATE_DASHBOARD // Vai para o Mapa/Main
            is SessionState.Unauthenticated -> Routes.PUBLIC_HOME // Vai para o Welcome
            is SessionState.Loading -> null // Fica no Splash
        }

        if (destination != null) {
            navController.navigate(destination) {
                // Limpa a pilha inteira
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    // 🚀 NavHost principal
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH // Começa sempre no Splash
    ) {
        // Ecrã de Splash (Verificação)
        composable(Routes.SPLASH) {
            SplashScreen()
        }

        // Ecrã de Boas-Vindas (Público)
        composable(Routes.PUBLIC_HOME) {
            WelcomeScreen(navController = navController)
        }

        // Ecrã de Autenticação (Login/Registo)
        composable(Routes.AUTH_SCREEN) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Routes.PRIVATE_DASHBOARD) {
                        popUpTo(Routes.AUTH_SCREEN) { inclusive = true }
                    }
                }
            )
        }

        // --- ROTAS PRIVADAS ---

        // Ecrã Principal (Dashboard com Mapa)
        composable(Routes.PRIVATE_DASHBOARD) {
            MainScreen(navController = navController)
        }

        // Ecrã de Perfil (Área Pessoal)
        composable(Routes.PROFILE) {
            ProfileScreen(navController = navController)
        }

        // Ecrã de Análise de Humor
        composable(Routes.MOOD_ANALYSIS) {
            MoodAnalysisScreen(navController = navController)
        }

        // Ecrã de Resumo da Rota
        composable(Routes.ROUTE_SUMMARY) {
            RouteSummaryScreen(navController)
        }

        // TODO: Adicionar PUBLIC_MAP
        composable(Routes.PUBLIC_MAP) {
            // Temporário
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Ecrã de Mapa Público (Convidado)")
            }
        }
    }
}