// ui/theme/Color.kt
package com.example.flowpaths.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Cores Primárias (Fluxo e Caminho)
val FlowPrimary = Color(0xFF00BCD4)      // Cyan Turquesa (Botões, Fluxo)
val FlowPrimaryDark = Color(0xFF007987) // Ciano Escuro (Contraste)

// Cores Secundárias (Estabilidade e Terreno)
val FlowSecondary = Color(0xFF8BC34A)    // Verde Lima
val FlowBackground = Color(0xFFFAFAFA)  // Fundo Limpo

val LightGrayBackground = Color(0xFFF0F2F5)
val WhiteBackground = Color(0xFFFFFFFF)

// Paleta Light do Material3
val LightColorScheme = lightColorScheme(
    primary = FlowPrimary,
    onPrimary = Color.White,
    primaryContainer = FlowPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = FlowSecondary,
    onSecondary = Color.Black,
    surface = FlowBackground,
    background = FlowBackground,
    error = Color(0xFFF44336) // Vermelho para Erros/Humor Negativo
)