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
    private val TAG = "RouteRepository"

    fun getCurrentUserIdOrNull(): String? = supabaseClient.auth.currentUserOrNull()?.id

    suspend fun savePercurso(percurso: PercursoRecomendado): Result<UUID> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = supabaseClient.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Utilizador não autenticado.")

                val coordinates = percurso.pontosParagem.map { ponto ->
                    listOf(ponto.longitude, ponto.latitude)
                }
                val rotaPontos = RotaPontos(coordinates = coordinates)

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
                    playlistSpotifyNome = percurso.playlistSpotifyNome,
                    playlistSpotifyUrl = percurso.playlistSpotifyUrl
                )

                val result = supabaseClient.from("percurso")
                    .insert(percursoToInsert) {
                        select(Columns.list("percurso_id, titulo_dinamico"))
                    }

                val insertedPercurso = result.decodeSingle<PercursoResponse>()
                val percursoIdUUID = insertedPercurso.percursoId

                Log.d(TAG, "✅ Percurso salvo: $percursoIdUUID (user=$userId)")

                // 🔴 CORREÇÃO: Garantir que desafios têm IDs UUID válidos
                val desafiosToInsert = percurso.desafios.mapIndexed { index, d ->
                    // Gerar UUID se o ID vier como String inválida
                    val validDesafioId = try {
                        UUID.fromString(d.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Desafio ${d.id} tem ID inválido. A gerar novo UUID.")
                        UUID.randomUUID()
                    }

                    DesafioInsert(
                        percursoId = percursoIdUUID,
                        instrucao = d.instrucao,
                        tipo = d.tipo.name,
                        duracaoSegundos = d.duracaoSegundos,
                        focoPsicologico = d.focoPsicologico,
                        latitude = d.latitude,
                        longitude = d.longitude
                    )
                }

                if (desafiosToInsert.isNotEmpty()) {
                    supabaseClient.from("desafio_bem_estar").insert(desafiosToInsert)
                    Log.d(TAG, "✅ Salvos ${desafiosToInsert.size} desafios.")
                } else {
                    Log.w(TAG, "⚠️ Nenhum desafio para inserir.")
                }

                Result.success(percursoIdUUID)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao salvar rota: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun uploadChallengeMedia(
        bytes: ByteArray,
        percursoId: String,
        desafioId: String,
        tipo: TipoDesafio,
        location: Location
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (bytes.isEmpty()) throw IllegalArgumentException("Ficheiro de áudio/foto está vazio.")

                val extension = if (tipo == TipoDesafio.AUDIO) "m4a" else "jpg"
                val fileName = "$percursoId/$desafioId.$extension"

                val bucket = supabaseClient.storage.from("multimedia")

                Log.d(TAG, "🚀 Upload media bucket=multimedia file=$fileName tipo=$tipo")
                bucket.upload(fileName, bytes, upsert = true)

                val publicUrl = bucket.publicUrl(fileName)
                Log.d(TAG, "🔗 Public URL gerada: $publicUrl")

                val mediaIdUUID = UUID.randomUUID()
                val mediaEntry = MultimediaInsert(
                    mediaId = mediaIdUUID,
                    percursoId = UUID.fromString(percursoId),
                    urlAcesso = publicUrl,
                    tipoConteudo = if (tipo == TipoDesafio.AUDIO) "AUDIO" else "FOTO",
                    geopontoCaptura = "POINT(${location.longitude} ${location.latitude})"
                )

                supabaseClient.from("multimedia").insert(mediaEntry)
                Log.d(TAG, "✅ Inserido em multimedia: mediaId=$mediaIdUUID")

                // 🔴 CORREÇÃO: Validar UUID antes de UPDATE
                val percursoUUID = try {
                    UUID.fromString(percursoId)
                } catch (e: Exception) {
                    throw IllegalArgumentException("percursoId inválido: $percursoId")
                }

                val desafioUUID = try {
                    UUID.fromString(desafioId)
                } catch (e: Exception) {
                    throw IllegalArgumentException("desafioId inválido: $desafioId")
                }

                supabaseClient.from("desafio_bem_estar").update(
                    {
                        set("media_id", mediaIdUUID)
                        set("status_conclusao", "CONCLUIDO")
                    }
                ) {
                    filter {
                        eq("desafio_id", desafioUUID)
                        eq("percurso_id", percursoUUID)
                    }
                }

                Log.d(TAG, "✅ Desafio atualizado (CONCLUIDO + media_id).")
                Result.success(publicUrl)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro upload media: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun markChallengeCompleted(
        percursoId: String,
        desafioId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // 🔴 VALIDAÇÃO CRÍTICA
                val percursoUUID = try {
                    UUID.fromString(percursoId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ percursoId inválido: $percursoId")
                    throw IllegalArgumentException("percursoId inválido: $percursoId")
                }

                val desafioUUID = try {
                    UUID.fromString(desafioId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ desafioId inválido: $desafioId")
                    throw IllegalArgumentException("desafioId inválido: $desafioId")
                }

                Log.d(TAG, "🔄 markChallengeCompleted: percurso=$percursoUUID desafio=$desafioUUID")

                val result = supabaseClient.from("desafio_bem_estar")
                    .update({ set("status_conclusao", "CONCLUIDO") }) {
                        filter {
                            eq("desafio_id", desafioUUID)
                            eq("percurso_id", percursoUUID)
                        }
                    }

                Log.d(TAG, "✅ markChallengeCompleted OK. Response: ${result.data}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "❌ markChallengeCompleted falhou: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // 🔴 CORREÇÃO CRÍTICA: Validação rigorosa de UUIDs
    suspend fun insertChallengeFeedback(
        percursoId: String,
        desafioId: String,
        feedbackTexto: String?
    ): Result<Unit> {
        Log.e("🔥🔥🔥 REPO", "ENTROU NO REPOSITORY insertChallengeFeedback")
        return withContext(Dispatchers.IO) {
            try {
                if (feedbackTexto.isNullOrBlank()) {
                    Log.d(TAG, "ℹ️ insertChallengeFeedback ignorado (texto vazio)")
                    return@withContext Result.success(Unit)
                }

                val userId = supabaseClient.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Utilizador não autenticado.")

                // 🔴 VALIDAÇÃO RIGOROSA DE UUIDs
                val percursoUUID = try {
                    UUID.fromString(percursoId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ percursoId inválido: '$percursoId' (${e.message})")
                    throw IllegalArgumentException("percursoId inválido: $percursoId", e)
                }

                val desafioUUID = try {
                    UUID.fromString(desafioId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ desafioId inválido: '$desafioId' (${e.message})")
                    throw IllegalArgumentException("desafioId inválido: $desafioId", e)
                }

                val userUUID = try {
                    UUID.fromString(userId)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ userId inválido: '$userId' (${e.message})")
                    throw IllegalArgumentException("userId inválido: $userId", e)
                }

                val payload = mapOf(
                    "percurso_id" to percursoUUID,
                    "desafio_id" to desafioUUID,
                    "user_id" to userUUID,
                    "feedback_texto" to feedbackTexto.trim()
                )

                Log.d(TAG, "🚀 insertChallengeFeedback:")
                Log.d(TAG, "   percurso_id: $percursoUUID")
                Log.d(TAG, "   desafio_id: $desafioUUID")
                Log.d(TAG, "   user_id: $userUUID")
                Log.d(TAG, "   feedback_texto: '${feedbackTexto.take(50)}...' (${feedbackTexto.length} chars)")

                // 🔴 CRÍTICO: Usar .select() para forçar resposta e detectar erros
                val resp = supabaseClient
                    .from("interacao_desafio")
                    .insert(payload) {
                        select() // Força resposta do servidor
                    }

                // 🔴 VALIDAÇÃO DA RESPOSTA
                val responseData = resp.data
                if (responseData.isNullOrEmpty()) {
                    Log.e(TAG, "⚠️ INSERT retornou resposta vazia! Possível erro de RLS ou constraint.")
                    throw Exception("INSERT em interacao_desafio retornou vazio")
                }

                Log.d(TAG, "✅ interacao_desafio INSERT OK")
                Log.d(TAG, "   Response: $responseData")

                Result.success(Unit)
            } catch (e: Exception) {
                // 🔴 LOG DETALHADO DO ERRO
                Log.e(TAG, "❌ insertChallengeFeedback FALHOU:")
                Log.e(TAG, "   Exception Type: ${e::class.java.simpleName}")
                Log.e(TAG, "   Message: ${e.message}")
                Log.e(TAG, "   Stack Trace:", e)

                Result.failure(e)
            }
        }
    }
}