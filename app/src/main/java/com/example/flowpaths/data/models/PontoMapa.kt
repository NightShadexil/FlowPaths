package com.example.flowpaths.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PontoMapa(
    val latitude: Double,
    val longitude: Double,
    val nome: String? = null // O nome do ponto-chave é opcional
)