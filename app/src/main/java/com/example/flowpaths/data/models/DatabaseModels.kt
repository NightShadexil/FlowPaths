package com.example.flowpaths.data.models

import com.example.flowpaths.utils.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// --- INSERTS ---

@Serializable
data class PercursoInsert(
    @SerialName("user_id")
    val userId: String,

    @SerialName("titulo_dinamico")
    val tituloDinamico: String,

    @SerialName("data_inicio")
    val dataInicio: String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        Locale.getDefault()
    ).format(Date()),

    @SerialName("dados_meteorologicos")
    val dadosMeteorologicos: String? = null,

    @SerialName("polyline_detalhada")
    val polylineDetalhada: String? = null,

    @SerialName("duracao_segundos")
    val duracaoSegundos: Int? = null,

    @SerialName("distancia_metros")
    val distanciaMetros: Int? = null,

    @SerialName("rota_pontos")
    val rotaPontos: RotaPontos? = null,

    @SerialName("sentimento_dominante")
    val sentimentoDominante: String? = null,

    @SerialName("status_processamento")
    val statusProcessamento: String? = "Pendente",

    // ✅ Spotify Data
    @SerialName("playlist_spotify_nome")
    val playlistSpotifyNome: String? = null,

    @SerialName("playlist_spotify_url")
    val playlistSpotifyUrl: String? = null
)

@Serializable
data class DesafioInsert(
    @Serializable(with = UUIDSerializer::class)
    @SerialName("percurso_id")
    val percursoId: UUID, // Keeps UUID type

    @SerialName("instrucao")
    val instrucao: String,

    @SerialName("tipo")
    val tipo: String,

    @SerialName("duracao_segundos")
    val duracaoSegundos: Int,

    @SerialName("foco_psicologico")
    val focoPsicologico: String,

    @SerialName("latitude")
    val latitude: Double?,

    @SerialName("longitude")
    val longitude: Double?
)

@Serializable
data class MultimediaInsert(
    @Serializable(with = UUIDSerializer::class)
    @SerialName("media_id")
    val mediaId: UUID = UUID.randomUUID(),

    @Serializable(with = UUIDSerializer::class)
    @SerialName("percurso_id")
    val percursoId: UUID,

    @SerialName("url_acesso")
    val urlAcesso: String,

    @SerialName("tipo_conteudo")
    val tipoConteudo: String,

    @SerialName("geoponto_captura")
    val geopontoCaptura: String
)

// --- RESPONSES ---

@Serializable
data class PercursoResponse(
    @Serializable(with = UUIDSerializer::class)
    @SerialName("percurso_id")
    val percursoId: UUID, // ✅ ALREADY A UUID due to Serializer

    @SerialName("titulo_dinamico")
    val tituloDinamico: String
)