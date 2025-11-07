package com.example.flowpaths.viewmodel

import io.github.jan.supabase.gotrue.user.UserSession

sealed class SessionState {
    object Loading : SessionState()
    object Unauthenticated : SessionState()
    data class Authenticated(val session: UserSession) : SessionState()
}