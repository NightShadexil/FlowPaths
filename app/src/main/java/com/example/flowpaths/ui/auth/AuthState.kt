// No seu ficheiro AuthState.kt
package com.example.flowpaths.ui.auth

import io.github.jan.supabase.gotrue.user.UserInfo

/**
 * Representa os possíveis estados do processo de autenticação (Login ou Registo).
 */
sealed class AuthState {
    /** Estado inicial, ou quando não há uma operação ativa. */
    data object Idle : AuthState()

    /** Estado durante a execução da chamada de API (Registo ou Login). */
    data object Loading : AuthState()

    /** Estado durante o processamento do callback OAuth. */
    data object ProcessingOAuth : AuthState()

    /** Estado que indica que a operação foi concluída com sucesso. */
    data class Success(val user: UserInfo) : AuthState()

    /**
     * Estado que indica que ocorreu um erro.
     * @param message A mensagem de erro a ser exibida ao utilizador.
     */
    data class Error(val message: String) : AuthState()

    /** Estado que indica que o utilizador fez logout. */
    data object LoggedOut : AuthState()

    // ✅ NOVO ESTADO: Indica que o email de recuperação foi enviado com sucesso.
    data object PasswordResetSent : AuthState()
}