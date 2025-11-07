package com.example.flowpaths.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.models.Utilizador
import com.example.flowpaths.data.repositories.UtilizadorRepository
import io.github.jan.supabase.exceptions.RestException // 💡 Atualizado para v3
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.auth // 💡 Atualizado para v3
import io.github.jan.supabase.gotrue.providers.AuthProvider
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
// Importar os módulos necessários
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.functions.functions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException
import android.util.Log

// (AuthState assumido como estando no AuthState.kt)


class AuthViewModel : ViewModel() {

    // 💡 Acesso aos módulos v3
    private val auth = FlowPathsApplication.supabaseClient.auth
    private val postgrest = FlowPathsApplication.supabaseClient.postgrest

    // 💡 Corrigido: Inicializar o repositório
    // (Assumindo que o Repositório usa o cliente Postgrest singleton)
    private val utilizadorRepository = UtilizadorRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // HANDLER GLOBAL: Garante que o estado Loading é limpo em caso de crash
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AUTH_FLOW", "EXCEÇÃO FATAL CAPTURADA: ${throwable.message}")
        if (_authState.value is AuthState.Loading) {
            _authState.value = AuthState.Error("Ocorreu um erro interno. Verifique a ligação e tente novamente.")
        }
    }

    // 💡 FUNÇÃO DE LOGOUT (Movida do ProfileViewModel para aqui, pois gere Auth)
    fun logout() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _authState.value = AuthState.Loading
            try {
                auth.signOut()
                // Depois de sair, passa para LoggedOut
                _authState.value = AuthState.LoggedOut
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro no logout: ${e.message}")
                _authState.value = AuthState.Error("Erro ao terminar sessão.")
            }
        }
    }


    // Função Principal de Registo
    fun registarUtilizador(nome: String, email: String, password: String) {

        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _authState.value = AuthState.Loading

            try {
                // 1. Chamada de Registo Supabase (Usamos AuthProvider Email)
                Log.d("AUTH_FLOW", "A iniciar chamada auth.signUpWith (Email)...")
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                Log.d("AUTH_FLOW", "Chamada signUpWith concluída.")

                val user = auth.currentUserOrNull()

                if (user != null) {
                    // 2. Criação do perfil na tabela 'public.utilizador'
                    utilizadorRepository.criarPerfil(
                        Utilizador(id = user.id, nome = nome, email = email)
                    )
                    _authState.value = AuthState.Success(user)
                } else {
                    // 3. Caso o "Confirm email" esteja ativo no Supabase
                    _authState.value = AuthState.Error("Registo bem-sucedido. Por favor, verifique o seu email para confirmar a conta antes de iniciar sessão.")
                }

                // 💡 TRATAMENTO DE ERROS DE REDE E TIMEOUT (Agora 30s)
            } catch (e: HttpRequestTimeoutException) {
                Log.e("AUTH_FLOW", "TIMEOUT HTTP (30s): ${e.message}")
                _authState.value = AuthState.Error("A ligação expirou (30s). Verifique o seu email.")
            } catch (e: SocketTimeoutException) {
                Log.e("AUTH_FLOW", "TIMEOUT DE SOCKET: ${e.message}")
                _authState.value = AuthState.Error("A ligação de rede falhou. Tente novamente.")

                // TRATAMENTO DE ERROS DO SERVIDOR (Supabase)
            } catch (e: RestException) { // 💡 ATUALIZADO para v3 (em vez de AuthRestException)
                Log.e("AUTH_FLOW", "Erro Supabase (RestException): ${e.message}")
                _authState.value = AuthState.Error("Erro no registo: Por favor, verifique o email e palavra-passe.")
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro inesperado: ${e.message}")
                _authState.value = AuthState.Error("Erro inesperado: ${e.message}")
            } finally {
                Log.d("AUTH_FLOW", "Bloco FINALLY atingido. Estado atual: ${_authState.value}")
                if (_authState.value is AuthState.Loading) {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    // Função para autenticar com provedores externos (Google/Facebook)
    fun autenticarComProvedor(provider: AuthProvider<*, *>) { // 💡 v3 usa AuthProvider
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _authState.value = AuthState.Loading
            try {
                auth.signInWith(provider)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Erro ao iniciar login com provedor: ${e.message}")
            } finally {
                Log.d("AUTH_FLOW", "Bloco FINALLY (Provedor) atingido. Estado atual: ${_authState.value}")
                if (_authState.value is AuthState.Loading) {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }

    // Função Principal de Login
    fun autenticarUtilizador(email: String, password: String) {

        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _authState.value = AuthState.Loading

            try {
                // 1. Invocação do Serviço de Auth para Login
                Log.d("AUTH_FLOW", "A iniciar chamada auth.signInWith (Email Login)...")
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                Log.d("AUTH_FLOW", "Chamada signInWith concluída.")

                val user = auth.currentUserOrNull()

                if (user != null) {
                    val perfil = utilizadorRepository.obterPerfil(user.id)
                    _authState.value = AuthState.Success(user)
                } else {
                    _authState.value = AuthState.Error("Falha ao iniciar sessão. Verifique as credenciais.")
                }

            } catch (e: HttpRequestTimeoutException) {
                Log.e("AUTH_FLOW", "TIMEOUT HTTP (Login): ${e.message}")
                _authState.value = AuthState.Error("A ligação expirou (30s). Tente novamente.")
            } catch (e: SocketTimeoutException) {
                Log.e("AUTH_FLOW", "TIMEOUT DE SOCKET (Login): ${e.message}")
                _authState.value = AuthState.Error("A ligação de rede falhou. Tente novamente.")

            } catch (e: RestException) { // 💡 ATUALIZADO para v3
                _authState.value = AuthState.Error("E-mail ou palavra-passe inválidos.")
            } catch (e: Exception) {
                Log.e("AUTH_FLOW", "Erro inesperado durante o login: ${e.message}")
                _authState.value = AuthState.Error("Erro inesperado durante o login: ${e.message}")
            } finally {
                Log.d("AUTH_FLOW", "Bloco FINALLY (Login) atingido. Estado atual: ${_authState.value}")
                if (_authState.value is AuthState.Loading) {
                    _authState.value = AuthState.Idle
                }
            }
        }
    }
}