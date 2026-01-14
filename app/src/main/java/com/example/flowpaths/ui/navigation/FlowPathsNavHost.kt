package com.example.flowpaths.navigation

import android.util.Log
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
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.screens.*
import com.example.flowpaths.ui.auth.AuthViewModel
import com.example.flowpaths.viewmodel.SessionViewModel
import com.example.flowpaths.data.states.SessionState
import org.koin.androidx.compose.koinViewModel

@Composable
fun FlowPathsNavHost() {
    val navController: NavHostController = rememberNavController()
    val sessionViewModel: SessionViewModel = koinViewModel()

    val sessionState by sessionViewModel.sessionState.collectAsState()
    val isRecoveryMode by sessionViewModel.isRecoveryMode.collectAsState()

    // 🔁 CÉREBRO DA NAVEGAÇÃO
    // É este bloco que decide para onde a app vai com base no estado da sessão.
    LaunchedEffect(sessionState, isRecoveryMode) {
        Log.d("NavHost", "Estado atual: $sessionState | Modo Recuperação: $isRecoveryMode")

        when (sessionState) {
            // ✅ CASO 1: Recuperação de Senha (O QUE FALTAVA PARA CORRIGIR O FREEZE)
            is SessionState.PasswordRecovery -> {
                if (navController.currentDestination?.route != Routes.NEW_PASSWORD_SCREEN) {
                    Log.d("NavHost", "🛟 Estado Recuperação detetado -> A navegar para NewPasswordScreen")
                    navController.navigate(Routes.NEW_PASSWORD_SCREEN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }

            // ✅ CASO 2: Autenticado (Login normal ou via link mágico)
            is SessionState.Authenticated -> {
                // Dupla segurança: se por acaso cair aqui mas a flag de recuperação estiver ativa
                if (isRecoveryMode) {
                    if (navController.currentDestination?.route != Routes.NEW_PASSWORD_SCREEN) {
                        navController.navigate(Routes.NEW_PASSWORD_SCREEN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                } else {
                    // Login normal -> Vai para a Dashboard
                    if (navController.currentDestination?.route != Routes.PRIVATE_DASHBOARD) {
                        navController.navigate(Routes.PRIVATE_DASHBOARD) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }

            // ✅ CASO 3: Não Autenticado (Logout ou arranque)
            is SessionState.Unauthenticated -> {
                // Só navega se não estivermos a meio de uma recuperação
                if (!isRecoveryMode &&
                    navController.currentDestination?.route != Routes.PUBLIC_HOME &&
                    navController.currentDestination?.route != Routes.AUTH_SCREEN
                ) {
                    navController.navigate(Routes.PUBLIC_HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }

            else -> { /* Loading... Fica quieto no ecrã atual */ }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.PUBLIC_HOME) { WelcomeScreen(navController) }

        composable(Routes.AUTH_SCREEN) {
            // O AuthScreen apenas mostra a UI. A navegação é gerida pelo LaunchedEffect acima.
            AuthScreen()
        }

        composable(Routes.PRIVATE_DASHBOARD) { MainScreen(navController) }
        composable(Routes.PROFILE) { ProfileScreen(navController) }
        composable(Routes.MOOD_ANALYSIS) { MoodAnalysisScreen(navController) }
        composable(Routes.ROUTE_SUMMARY) { RouteSummaryScreen(navController) }

        composable(Routes.NEW_PASSWORD_SCREEN) {
            val authViewModel: AuthViewModel = koinViewModel()
            val sessVM: SessionViewModel = koinViewModel()

            NewPasswordScreen(
                viewModel = authViewModel,
                onPasswordResetSuccess = {
                    // Quando a pass é alterada com sucesso:
                    // 1. Limpamos o form do AuthViewModel
                    authViewModel.resetState()
                    // 2. Fazemos Logout lógico no SessionViewModel
                    // Isto vai mudar o estado para Unauthenticated e o NavHost vai mandar para a Home
                    sessVM.logout()
                }
            )
        }

        composable(Routes.PUBLIC_MAP) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Mapa Público")
            }
        }
    }
}