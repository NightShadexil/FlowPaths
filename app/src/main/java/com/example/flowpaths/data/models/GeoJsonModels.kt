package com.example.flowpaths.data.models

import com.example.flowpaths.utils.RotaPontosSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = RotaPontosSerializer::class)
data class RotaPontos(
    val type: String = "LineString",
    @SerialName("coordinates")
    val coordinates: List<List<Double>>
)