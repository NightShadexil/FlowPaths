package com.example.flowpaths.ui.auth

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.FlowPathsApplication.Companion.supabaseClient
import com.example.flowpaths.data.models.Utilizador
import com.example.flowpaths.data.repositories.UtilizadorRepository
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth = FlowPathsApplication.supabaseClient.auth
    private val utilizadorRepository = UtilizadorRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    init {
        observeSessionStatus()
    }

    // --- MÉTODOS DE CONTROLO DE UI ---

    fun setProcessingOAuth() {
        _authState.value = AuthState.ProcessingOAuth
    }

    // ✅ NOVO: Limpa o estado (Crítico para a Recuperação de Senha)
    fun resetState() { _authState.value = AuthState.Idle }

    // -----------------------------------------------------

    fun processOAuthCallback(intent: Intent) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.ProcessingOAuth
            try {
                supabaseClient.handleDeeplinks(intent)
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro ao processar callback OAuth: ${e.message}")
                _authState.value = AuthState.Error("Erro ao processar login. Tente novamente.")
            }
        }
    }

    private fun observeSessionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            auth.sessionStatus.collect { status ->
                when (status) {
                    // 🔥 ALTERAÇÃO CRÍTICA:
                    // Removemos a atualização automática para 'Success' aqui.
                    // Isso evita que o ecrã de Auth "pisque" sucesso antes de navegar.
                    is SessionStatus.Authenticated -> {
                        Log.d("AuthVM", "Sessão detetada. O NavHost deve navegar agora.")
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (_authState.value !is AuthState.Idle) {
                            _authState.value = AuthState.Idle // Volta ao formulário limpo
                        }
                    }
                    is SessionStatus.NetworkError -> {
                        _authState.value = AuthState.Error("Erro de rede.")
                    }
                    else -> {}
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                auth.signOut()
                _authState.value = AuthState.LoggedOut
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro no logout: ${e.message}")
                _authState.value = AuthState.Error("Erro ao terminar sessão.")
            }
        }
    }

    fun registarUtilizador(nome: String, email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                val user = auth.currentUserOrNull()
                if (user != null) {
                    utilizadorRepository.criarPerfil(Utilizador(id = user.id, nome = nome, email = email))
                    _authState.value = AuthState.Success(user)
                } else {
                    _authState.value = AuthState.Error("Registo bem-sucedido. Verifique o email.")
                }
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro no registo: ${e.message}")
                _authState.value = AuthState.Error("Erro no registo: ${e.message}")
            }
        }
    }

    fun autenticarUtilizador(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                // 🔥 ALTERAÇÃO: Não definimos 'Success' aqui manualmente.
                // Deixamos o SessionViewModel detetar a sessão válida e navegar.
                // O AuthScreen fica em 'Loading' até ser destruído pela navegação.
                Log.d("AuthViewModel", "Login enviado. Aguardando sessão...")
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Dados inválidos.")
            }
        }
    }

    fun recuperarPassword(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                auth.resetPasswordForEmail(email)
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro na recuperação: ${e.message}", e)
                _authState.value = AuthState.Error("Erro ao enviar email de recuperação.")
            }
        }
    }

    fun updateNewPassword(newPassword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _authState.value = AuthState.Loading
            try {
                auth.modifyUser {
                    password = newPassword
                }
                auth.currentUserOrNull()?.let { user ->
                    _authState.value = AuthState.Success(user)
                }
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro ao atualizar password: ${e.message}")
                _authState.value = AuthState.Error("Erro ao redefinir password.")
            }
        }
    }
}