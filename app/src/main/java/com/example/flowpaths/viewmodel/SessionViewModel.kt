package com.example.flowpaths.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.states.SessionState
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SessionViewModel : ViewModel() {

    // Acede à instância Auth do cliente Supabase global
    private val auth = FlowPathsApplication.supabaseClient.auth

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // Controla se estamos no fluxo de recuperação de password (para não redirecionar automaticamente)
    private val _isRecoveryMode = MutableStateFlow(false)
    val isRecoveryMode = _isRecoveryMode.asStateFlow()

    init {
        // Verificação inicial rápida: se não houver sessão em cache, marca logo como Unauthenticated
        // para a UI não ficar presa no "Loading"
        if (auth.currentSessionOrNull() == null) {
            _sessionState.value = SessionState.Unauthenticated
        }
        observeSessionStatus()
    }

    fun setRecoveryMode() {
        Log.d("SessionViewModel", "Modo de recuperação ATIVADO")
        _isRecoveryMode.value = true
    }

    fun exitRecoveryMode() {
        Log.d("SessionViewModel", "Modo de recuperação DESATIVADO")
        _isRecoveryMode.value = false
        // Ao sair do modo de recuperação, verificamos o estado atual
        val user = auth.currentUserOrNull()
        if (user != null) {
            _sessionState.value = SessionState.Authenticated(user.id)
        } else {
            _sessionState.value = SessionState.Unauthenticated
        }
    }

    private fun observeSessionStatus() {
        viewModelScope.launch {
            auth.sessionStatus
                // 🔥 AQUI ESTÁ A CORREÇÃO CRÍTICA 🔥
                // O operador .catch intercepta qualquer erro que ocorra DENTRO do fluxo
                // (como o erro 'missing destination name refresh_token_hmac_key')
                .catch { e ->
                    Log.e("SessionViewModel", "🔥 CRASH EVITADO: Erro crítico na sessão: ${e.message}")

                    // Força um logout limpo para apagar o token corrompido do armazenamento local
                    forceCleanLogout()

                    // Redireciona o utilizador para o Login
                    _sessionState.value = SessionState.Unauthenticated
                }
                .collect { status ->
                    // Se estivermos em recuperação de password, ignoramos atualizações automáticas
                    // para evitar que o user seja "expulso" do ecrã de nova password
                    if (_isRecoveryMode.value && status is SessionStatus.Authenticated) {
                        Log.d("SessionViewModel", "🔒 Recuperação ativa. Bloqueando navegação automática.")
                        _sessionState.value = SessionState.PasswordRecovery
                        return@collect
                    }

                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val userId = status.session.user?.id.orEmpty()
                            Log.d("SessionViewModel", "✅ Sessão válida: $userId")
                            _sessionState.value = SessionState.Authenticated(userId)
                        }
                        is SessionStatus.NotAuthenticated -> {
                            Log.w("SessionViewModel", "⛔ Não autenticado.")
                            _sessionState.value = SessionState.Unauthenticated
                        }
                        is SessionStatus.LoadingFromStorage -> {
                            // Só mostramos loading se ainda não tivermos certeza do estado
                            if (_sessionState.value !is SessionState.Unauthenticated) {
                                _sessionState.value = SessionState.Loading
                            }
                        }
                        is SessionStatus.NetworkError -> {
                            Log.e("SessionViewModel", "⚠️ Erro de rede na verificação de sessão.")
                            // Em caso de erro de rede, mantemos o estado anterior ou forçamos logout?
                            // Geralmente, forçar logout aqui é agressivo. Vamos assumir Unauthenticated por segurança.
                            _sessionState.value = SessionState.Unauthenticated
                        }
                    }
                }
        }
    }

    /**
     * Função auxiliar para limpar dados locais sem causar novos erros.
     * Usada quando o token está corrompido.
     */
    private suspend fun forceCleanLogout() {
        try {
            Log.w("SessionViewModel", "🧹 A limpar dados de sessão corrompidos...")
            auth.signOut() // Isto limpa o SharedPreferences/DataStore
        } catch (e: Exception) {
            // Se falhar o signOut (ex: sem rede), não faz mal,
            // o importante é que tentámos limpar e vamos mudar o estado da UI a seguir.
            Log.e("SessionViewModel", "Erro ao forçar limpeza: ${e.message}")
        }
    }

    suspend fun handleDeepLinkAndSetState(intent: Intent?) {
        if (intent == null) return
        _isRecoveryMode.value = false
        try {
            FlowPathsApplication.supabaseClient.handleDeeplinks(intent)
        } catch (e: Exception) {
            Log.e("SessionViewModel", "Erro ao processar Deep Link: ${e.message}")
            _sessionState.value = SessionState.Unauthenticated
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isRecoveryMode.value = false
            try {
                auth.signOut()
                Log.d("SessionViewModel", "Logout efetuado com sucesso.")
            } catch (e: Exception) {
                Log.w("SessionViewModel", "Erro ao tentar logout: ${e.message}")
            } finally {
                // Garante SEMPRE que a UI vai para o ecrã de login
                _sessionState.value = SessionState.Unauthenticated
            }
        }
    }
}