package com.example.flowpaths.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.BuildConfig
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.models.Utilizador
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration.Companion.days
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.serialization.json.JsonPrimitive

// Estados da UI (sem alterações)
sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success(val utilizador: Utilizador) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel : ViewModel() {

    private val auth = FlowPathsApplication.supabaseClient.auth
    private val postgrest = FlowPathsApplication.supabaseClient.postgrest
    private val storage = FlowPathsApplication.supabaseClient.storage

    private val okHttpClient = OkHttpClient()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProfileUiState.Loading

            // 1. Obter utilizador atual da sessão (cache local)
            val currentUser = auth.currentUserOrNull()
            val userId = currentUser?.id

            if (userId == null) {
                _uiState.value = ProfileUiState.Error("Utilizador não autenticado.")
                return@launch
            }

            // Obter metadados do provedor (Google/Facebook)
            val metadata = currentUser.userMetadata
            val providerName = (metadata?.get("name") as? JsonPrimitive)?.content
                ?: (metadata?.get("full_name") as? JsonPrimitive)?.content

            try {
                Log.d("ProfileVM", "A procurar perfil para $userId na tabela 'utilizador'.")

                // 2. Tentar buscar perfil na base de dados
                val utilizadores = postgrest.from("utilizador")
                    .select(Columns.ALL) {
                        filter { eq("id", userId) }
                    }
                    .decodeList<Utilizador>()

                if (utilizadores.isNotEmpty()) {
                    // Cenário A: Utilizador JÁ EXISTE
                    val utilizadorBD = utilizadores.first()
                    var utilizadorAtualizado = utilizadorBD

                    // Atualiza o nome se tiver mudado no provedor (opcional, mas útil)
                    if (providerName != null && providerName != utilizadorBD.nome) {
                        Log.d("ProfileVM", "Nome mudou no provedor. Atualizando na BD.")
                        postgrest.from("utilizador")
                            .update(mapOf("nome" to providerName)) {
                                filter { eq("id", userId) }
                            }
                        utilizadorAtualizado = utilizadorBD.copy(nome = providerName)
                    }

                    // Processa a URL do avatar (assina se for privada)
                    val utilizadorComAvatar = processarAvatarUrl(utilizadorAtualizado)
                    _uiState.value = ProfileUiState.Success(utilizadorComAvatar)

                } else {
                    // Cenário B: Utilizador NÃO EXISTE (Primeiro login)
                    Log.d("ProfileVM", "Utilizador $userId não encontrado. A criar novo perfil.")
                    // Passamos o currentUser para evitar nova chamada à Auth
                    criarPerfilAPartirDaAuth(currentUser, providerName)
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Falha geral ao carregar o perfil: ${e.message}")
                _uiState.value = ProfileUiState.Error("Falha ao carregar o perfil: ${e.message}")
            }
        }
    }

    private suspend fun criarPerfilAPartirDaAuth(currentUser: io.github.jan.supabase.gotrue.user.UserInfo, providerName: String?) {
        try {
            val userId = currentUser.id
            val email = currentUser.email ?: ""
            val nome = providerName ?: "Novo Utilizador"

            val providerAvatarUrl = (currentUser.userMetadata?.get("avatar_url") as? JsonPrimitive)?.content
            var avatarPath: String? = null

            // 1. Tentar migrar o avatar do Google/Facebook para o nosso Storage
            if (!providerAvatarUrl.isNullOrEmpty()) {
                try {
                    Log.d("ProfileVM", "A fazer download do avatar do provedor: $providerAvatarUrl")
                    val imageBytes = downloadImage(providerAvatarUrl)

                    if (imageBytes != null) {
                        val fileExtension = providerAvatarUrl.substringAfterLast(".", "png").take(3) // Limita extensão
                        val uniqueFileName = "provider_avatar.$fileExtension"
                        val filePath = "$userId/$uniqueFileName"

                        storage.from("avatars").upload(filePath, imageBytes, upsert = true)
                        Log.d("ProfileVM", "Avatar guardado no Storage: $filePath")
                        avatarPath = filePath
                    }
                } catch (e: Exception) {
                    Log.w("ProfileVM", "Falha não-crítica no upload do avatar: ${e.message}")
                }
            }

            // 2. Criar objeto Utilizador
            val novoUtilizador = Utilizador(
                id = userId,
                nome = nome,
                email = email,
                avatarUrl = avatarPath
            )

            // 3. Inserir na BD
            postgrest.from("utilizador").insert(novoUtilizador)
            Log.d("ProfileVM", "Perfil criado na BD com sucesso.")

            // ✅ CORREÇÃO CRÍTICA: Não chamar loadUserProfile() aqui.
            // Atualizar o estado diretamente com os dados que já temos.

            // Processa o URL (gera link assinado se necessário)
            val utilizadorFinal = processarAvatarUrl(novoUtilizador)

            // Atualiza a UI
            _uiState.value = ProfileUiState.Success(utilizadorFinal)

        } catch (e: Exception) {
            Log.e("ProfileVM", "Falha crítica ao criar perfil: ${e.message}")
            _uiState.value = ProfileUiState.Error("Erro ao criar perfil. Tente novamente.")
        }
    }

    private suspend fun processarAvatarUrl(utilizador: Utilizador): Utilizador {
        val avatarPath = utilizador.avatarUrl
        if (!avatarPath.isNullOrEmpty()) {
            try {
                // Se for URL externa (ex: google), devolve direto (mas aqui já devemos ter migrado para path interno)
                if (avatarPath.startsWith("http")) return utilizador

                Log.d("ProfileVM", "A gerar URL ASSINADA para: $avatarPath")

                // Gera URL assinada válida por 7 dias
                val signedUrl = storage.from("avatars").createSignedUrl(
                    path = avatarPath,
                    expiresIn = 7.days
                )

                // Constrói a URL completa correta com o domínio da tua instância Supabase
                val baseStorageUrl = BuildConfig.SUPABASE_URL.removeSuffix("/") + "/storage/v1"
                // O signedUrl retornado pela biblioteca já costuma ser relativo ou completo dependendo da versão.
                // Na v3, normalmente devolve o path relativo com o token. Vamos garantir.

                val finalUrl = if (signedUrl.startsWith("http")) {
                    signedUrl
                } else {
                    "$baseStorageUrl/object/sign/avatars/$signedUrl" // Ajuste conforme comportamento da lib v3
                }

                // Adiciona timestamp para invalidar cache do Coil
                val urlWithCacheBuster = "$finalUrl&v=${System.currentTimeMillis()}"

                Log.d("ProfileVM", "URL Final: $urlWithCacheBuster")
                return utilizador.copy(avatarUrl = urlWithCacheBuster)

            } catch (e: Exception) {
                Log.e("ProfileVM", "Erro ao assinar URL: ${e.message}")
            }
        }
        return utilizador
    }

    private suspend fun downloadImage(url: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProfileUiState.Loading
            val userId = auth.currentUserOrNull()?.id ?: return@launch

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes != null) {
                    val uniqueFileName = "avatar_${System.currentTimeMillis()}.jpg"
                    val filePath = "$userId/$uniqueFileName"

                    storage.from("avatars").upload(filePath, fileBytes, upsert = true)

                    postgrest.from("utilizador")
                        .update(mapOf("avatar_url" to filePath)) {
                            filter { eq("id", userId) }
                        }

                    // Aqui podemos recarregar o perfil porque é uma ação iniciada pelo utilizador, não automática
                    loadUserProfile()
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Erro upload: ${e.message}")
                _uiState.value = ProfileUiState.Error("Falha no upload.")
            }
        }
    }
}