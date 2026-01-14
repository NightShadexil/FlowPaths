package com.example.flowpaths.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

enum class TipoDesafio {
    FOTO,
    AUDIO,
    TEXTO,
    REFLEXAO
}

@Serializable
data class DesafioBemEstar(

    @SerialName("desafio_id")
    val id: String = UUID.randomUUID().toString(),

    val instrucao: String,

    val tipo: TipoDesafio,

    @SerialName("duracao_segundos")
    val duracaoSegundos: Int,

    @SerialName("foco_psicologico")
    val focoPsicologico: String,

    // ❗ NÃO vem da IA → estado interno
    @Transient
    val statusConclusao: String = "PENDENTE",

    // 🔴 ESTES são os que iam para NULL
    val latitude: Double? = null,
    val longitude: Double? = null,

    // ✔ Apenas para mapear o JSON da IA
    @Transient
    val pontoReferencia: PontoMapa? = null
)
