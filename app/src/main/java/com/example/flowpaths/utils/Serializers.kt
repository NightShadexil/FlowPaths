package com.example.flowpaths.utils

import com.example.flowpaths.data.models.RotaPontos
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

@Serializer(forClass = RotaPontos::class)
object RotaPontosSerializer : KSerializer<RotaPontos> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RotaPontos") {
        element<String>("type")
        element<List<List<Double>>>("coordinates")
    }

    override fun serialize(encoder: Encoder, value: RotaPontos) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Expected JsonEncoder")
        val jsonObject = buildJsonObject {
            put("type", value.type)
            put("coordinates",
                JsonArray(value.coordinates.map { JsonArray(it.map { JsonPrimitive(it) }) })
            )
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): RotaPontos {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Expected JsonDecoder")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content ?: error("Missing type")
        val coordinates = jsonObject["coordinates"]?.jsonArray?.map { coordArray ->
            coordArray.jsonArray.map { it.jsonPrimitive.double }
        } ?: error("Missing coordinates")
        return RotaPontos(type, coordinates)
    }
}