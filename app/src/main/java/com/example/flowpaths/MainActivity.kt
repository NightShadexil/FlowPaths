package com.example.flowpaths

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Opcional: Se for usar um splash screen nativo
import com.example.flowpaths.navigation.FlowPathsNavHost
import com.example.flowpaths.ui.theme.FlowPathsAppTheme

/**
 * MainActivity: A única Activity na aplicação, servindo como o Host para toda a UI em Jetpack Compose.
 *
 * Não utiliza ficheiros XML de layout, pois a UI é definida inteiramente em @Composable functions.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se estiver a usar um Splash Screen nativo (recomendado):
        // installSplashScreen()

        // setContent é o método que liga o Compose à Activity
        setContent {
            // 1. Aplica o Tema FlowPaths (Cores, Tipografia)
            FlowPathsAppTheme {
                // 2. Inicia o sistema de navegação
                FlowPathsNavHost()
            }
        }
    }
}