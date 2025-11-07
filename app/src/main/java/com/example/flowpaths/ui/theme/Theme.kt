// ui/theme/Theme.kt
package com.example.flowpaths.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography

// (Definir LightColorScheme usando as cores de Color.kt, conforme acima)

val Typography = Typography()
@Composable
fun FlowPathsAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // Assume a Tipografia padrão (ou a sua customizada)
        content = content
    )
}