package com.example.flowpaths.ui.navigation

/**
 * Objeto centralizado para gerir as constantes de rota (Strings) da aplicação.
 */
object Routes {

    // 💡 ROTA DE ARRANQUE (Nova)
    const val SPLASH = "splash" // Ecrã que verifica a sessão

    // Rotas Públicas
    const val PUBLIC_HOME = "welcome" // Ecrã de Boas-Vindas (com botões Login/Convidado)
    const val PUBLIC_MAP = "public_map"
    const val AUTH_SCREEN = "auth"

    // Rotas Privadas (Após Login)

    // 💡 ROTA PRINCIPAL PRIVADA (Nova)
    const val PRIVATE_DASHBOARD = "main_dashboard" // O MainScreen com o mapa

    // 💡 ROTA DE PERFIL (Nova)
    const val PROFILE = "profile" // O ecrã de perfil/área pessoal

    // 💡 ROTA DE ANÁLISE (Nova)
    const val MOOD_ANALYSIS = "mood_analysis" // O ecrã de análise de vibe

    const val ROUTE_SUMMARY = "route_summary"
}
