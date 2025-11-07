package com.example.flowpaths.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.BuildConfig
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.models.Utilizador
// Imports v3
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.upload
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlin.time.Duration.Companion.days

// Estados da UI para o ProfileScreen
sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success(val utilizador: Utilizador) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {

    private val auth = FlowPathsApplication.supabaseClient.auth
    private val postgrest = FlowPathsApplication.supabaseClient.postgrest
    private val storage = FlowPathsApplication.supabaseClient.storage

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    /**
     * Carrega o perfil do utilizador (da tabela 'public.utilizador')
     */
    fun loadUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProfileUiState.Loading
            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.value = ProfileUiState.Error("Utilizador não autenticado.")
                return@launch
            }
            try {
                Log.d("ProfileVM", "A carregar perfil para $userId")
                val utilizador = postgrest.from("utilizador")
                    .select(Columns.ALL) {
                        filter { eq("id", userId) }
                    }
                    .decodeSingle<Utilizador>()
                Log.d("ProfileVM", "Perfil carregado. AvatarPath da BD: ${utilizador.avatarUrl}")
                var utilizadorComAvatar = utilizador
                val avatarPath = utilizador.avatarUrl

                if (!avatarPath.isNullOrEmpty()) {
                    try {
                        Log.d("ProfileVM", "A gerar URL ASSINADA (Bucket é Privado/RLS)...")

                        // 1. Gera o URL com o token de segurança.
                        val signedUrl = storage.from("avatars").createSignedUrl(
                            path = avatarPath,
                            expiresIn = 7.days
                        )

                        // O createSignedUrl devolve: 'object/sign/avatars/path/to/file?token=...'

                        // 2. CONSTRÓI A URL COMPLETA USANDO A SUPABASE URL DA BUILD CONFIG

                        // Remove a barra final se existir para evitar URL duplicada //
                        val baseStorageUrl = BuildConfig.SUPABASE_URL.removeSuffix("/") + "/storage/v1/"

                        val finalAbsoluteUrl = if (!signedUrl.isNullOrEmpty()) {
                            // Concatena a base e a parte assinada
                            "$baseStorageUrl$signedUrl&v=${System.currentTimeMillis()}"
                        } else {
                            // Se a assinatura falhar (improvável), usa a URL pública
                            storage.from("avatars").publicUrl(avatarPath)
                        }

                        Log.d("ProfileVM", "URL FINAL ABSOLUTA: $finalAbsoluteUrl")
                        utilizadorComAvatar = utilizador.copy(avatarUrl = finalAbsoluteUrl)

                    } catch (e: Exception) {
                        Log.e("ProfileVM", "Falha catastrófica ao obter URL: ${e.message}")
                        _uiState.value = ProfileUiState.Error("Falha ao carregar a imagem do avatar. ${e.message}")
                        return@launch
                    }
                }
                _uiState.value = ProfileUiState.Success(utilizadorComAvatar)
            } catch (e: Exception) {
                Log.e("ProfileVM", "Falha ao carregar o perfil: ${e.message}")
                _uiState.value = ProfileUiState.Error("Falha ao carregar o perfil: ${e.message}")
            }
        }
    }

    /**
     * Faz o upload de um novo avatar para o Supabase Storage e atualiza o URL na tabela 'utilizador'.
     */
    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProfileUiState.Loading // Mostrar loading durante o upload

            val userId = auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.value = ProfileUiState.Error("Utilizador não autenticado.")
                return@launch
            }

            try {
                // 1. Obter os bytes da imagem
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes == null) {
                    _uiState.value = ProfileUiState.Error("Falha ao ler o ficheiro da imagem.")
                    return@launch
                }

                // 2. Definir o caminho no Storage
                val fileExtension = contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "png"
                val uniqueFileName = "${UUID.randomUUID()}.$fileExtension"
                val filePath = "$userId/$uniqueFileName"
                Log.d("ProfileVM", "A fazer upload para o caminho: $filePath")

                // 3. Fazer o Upload para o Supabase Storage (Bucket "avatars")
                storage.from("avatars").upload(
                    path = filePath,
                    data = fileBytes,
                    upsert = false // Não sobrescrever, criar sempre novo
                )
                Log.d("ProfileVM", "Upload concluído.")

                // 4. Atualizar a tabela 'utilizador' (Postgrest) com o novo caminho
                Log.d("ProfileVM", "A atualizar 'avatar_url' na BD com: $filePath")
                postgrest.from("utilizador")
                    .update(mapOf("avatar_url" to filePath)) {
                        filter {
                            eq("id", userId)
                        }
                    }
                Log.d("ProfileVM", "BD atualizada.")

                // 5. Recarregar o perfil para obter o novo URL
                loadUserProfile()

            } catch (e: Exception) {
                Log.e("ProfileVM", "Falha no upload do avatar: ${e.message}")
                _uiState.value = ProfileUiState.Error("Falha no upload do avatar: ${e.message}")
            }
        }
    }

}