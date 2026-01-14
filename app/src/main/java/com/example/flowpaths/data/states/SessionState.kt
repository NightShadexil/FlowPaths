package com.example.flowpaths.data.states

sealed class SessionState {
    object Loading : SessionState()
    object Unauthenticated : SessionState()
    data class Authenticated(val userId: String) : SessionState()
    object PasswordRecovery : SessionState()
}