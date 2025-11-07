// app/src/main/java/com/example/flowpaths/data/repositories/UtilizadorRepository.kt
package com.example.flowpaths.data.repositories

import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.models.Utilizador
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest


class UtilizadorRepository {

    private val client = FlowPathsApplication.supabaseClient

    // Função para criar o perfil inicial (Após o Registo no Auth)
    suspend fun criarPerfil(utilizador: Utilizador) {
        try {
            client.postgrest
                .from("utilizador") // Nome da tabela
                .insert(utilizador)
        } catch (e: RestException) {
            throw Exception("Falha ao criar o perfil: ${e.message}")
        }
    }

    // Função para obter os dados do perfil após o login
    suspend fun obterPerfil(userId: String): Utilizador {
        return client.postgrest
            .from("utilizador")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<Utilizador>()
    }
}