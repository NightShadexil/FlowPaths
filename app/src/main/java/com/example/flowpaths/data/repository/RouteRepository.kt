package com.example.flowpaths.data.repository

import android.location.Location
import android.util.Log
import com.example.flowpaths.data.models.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class RouteRepository(
    private val supabaseClient: SupabaseClient
) {
    suspend fun savePercurso(percurso: PercursoRecomendado): Result<UUID> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = supabaseClient.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Utilizador não autenticado.")

                // 1. Prepare Points
                val coordinates = percurso.pontosParagem.map { ponto ->
                    listOf(ponto.longitude, ponto.latitude)
                }
                val rotaPontos = RotaPontos(coordinates = coordinates)

                // 2. Prepare Insert Object
                val percursoToInsert = PercursoInsert(
                    userId = userId,
                    tituloDinamico = percurso.tituloDinamico,
                    dadosMeteorologicos = percurso.dadosMeteorologicos ?: "Desconhecido",
                    polylineDetalhada = percurso.polylineDetalhada,
                    rotaPontos = rotaPontos,
                    sentimentoDominante = percurso.sentimentoDominante ?: "Neutro",
                    statusProcessamento = "Pendente",
                    duracaoSegundos = percurso.duracaoEstimada ?: 0,
                    distanciaMetros = percurso.distanciaEstimada ?: 0,
                    // ✅ Spotify Fields
                    playlistSpotifyNome = percurso.playlistSpotifyNome,
                    playlistSpotifyUrl = percurso.playlistSpotifyUrl
                )

                // 3. Insert and Retrieve generated UUID
                val result = supabaseClient.from("percurso")
                    .insert(percursoToInsert) {
                        select(Columns.list("percurso_id, titulo_dinamico"))
                    }

                // decodeSingle automatically uses the UUIDSerializer
                val insertedPercurso = result.decodeSingle<PercursoResponse>()

                // 🔴 FIX: This is ALREADY a UUID, no need for UUID.fromString()
                val percursoIdUUID = insertedPercurso.percursoId

                Log.d("RouteRepository", "✅ Percurso salvo: $percursoIdUUID")

                // 4. Prepare Challenges
                val desafiosToInsert = percurso.desafios.map { d ->
                    DesafioInsert(
                        percursoId = percursoIdUUID, // Direct UUID passing
                        instrucao = d.instrucao,
                        tipo = d.tipo.name,
                        duracaoSegundos = d.duracaoSegundos,
                        focoPsicologico = d.focoPsicologico,
                        latitude = d.latitude,
                        longitude = d.longitude
                    )
                }

                // 5. Insert Challenges
                if (desafiosToInsert.isNotEmpty()) {
                    supabaseClient.from("desafio_bem_estar")
                        .insert(desafiosToInsert)
                    Log.d("RouteRepository", "✅ Salvos ${desafiosToInsert.size} desafios.")
                }

                // Return the UUID directly
                Result.success(percursoIdUUID)

            } catch (e: Exception) {
                Log.e("RouteRepository", "❌ Erro ao salvar rota: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun uploadChallengeMedia(
        bytes: ByteArray,
        percursoId: String, // Kept as String to avoid breaking ViewModel caller, converted below
        desafioId: String,
        tipo: TipoDesafio,
        location: Location
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val extension = if (tipo == TipoDesafio.AUDIO) "m4a" else "jpg"
                val contentType = if (tipo == TipoDesafio.AUDIO) "audio/mp4" else "image/jpeg"
                val fileName = "$percursoId/$desafioId.$extension"

                val bucket = supabaseClient.storage.from("multimedia")
                bucket.upload(fileName, bytes, upsert = true)
                val publicUrl = bucket.publicUrl(fileName)

                // Create Media Entry
                val mediaEntry = MultimediaInsert(
                    mediaId = UUID.randomUUID(),
                    percursoId = UUID.fromString(percursoId), // String -> UUID conversion here
                    urlAcesso = publicUrl,
                    tipoConteudo = if(tipo == TipoDesafio.AUDIO) "AUDIO" else "FOTO",
                    geopontoCaptura = "POINT(${location.longitude} ${location.latitude})"
                )

                supabaseClient.from("multimedia").insert(mediaEntry)

                // Update Challenge Status
                supabaseClient.from("desafio_bem_estar").update(
                    {
                        set("media_id", mediaEntry.mediaId) // UUID
                        set("status_conclusao", "CONCLUIDO")
                    }
                ) {
                    filter {
                        eq("desafio_id", desafioId)
                    }
                }

                Result.success(publicUrl)
            } catch (e: Exception) {
                Log.e("RouteRepository", "Erro upload media: ${e.message}")
                Result.failure(e)
            }
        }
    }
}