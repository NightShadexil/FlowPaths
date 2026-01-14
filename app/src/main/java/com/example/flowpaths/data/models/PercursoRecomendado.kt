package com.example.flowpaths.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PercursoRecomendado(
    val recomendacao: String,

    @SerialName("titulo_dinamico")
    val tituloDinamico: String,

    @SerialName("pontos_paragem")
    val pontosParagem: List<PontoMapa>,

    @SerialName("polyline_detalhada")
    val polylineDetalhada: String? = null,

    @SerialName("dados_meteorologicos")
    val dadosMeteorologicos: String? = null,

    @SerialName("sentimento_dominante")
    val sentimentoDominante: String? = null,

    val desafios: List<DesafioBemEstar> = emptyList(),

    @SerialName("duracao_estimada")
    val duracaoEstimada: Int? = null,

    @SerialName("distancia_estimada")
    val distanciaEstimada: Int? = null,

    @SerialName("termo_pesquisa_spotify")
    val termoPesquisaSpotify: String? = null,

    @SerialName("playlist_spotify_nome")
    val playlistSpotifyNome: String? = null,

    @SerialName("playlist_spotify_url")
    val playlistSpotifyUrl: String? = null
)
