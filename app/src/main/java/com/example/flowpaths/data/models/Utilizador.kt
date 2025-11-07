package com.example.flowpaths.data.models

import kotlinx.serialization.SerialName // 💡 PASSO 1: Importar o SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Utilizador(
    val id: String,
    val nome: String,
    val email: String,

    @SerialName("perfil_humor_medio") // 💡 BÓNUS: Adicionei isto também
    val perfilHumorMedio: String = "Neutro",

    @SerialName("avatar_url") // 💡 PASSO 2: Mapear a coluna "avatar_url"
    var avatarUrl: String? = null
    // Outros campos devem ser adicionados aqui
)