package com.example.flowpaths.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.FlowPathsApplication
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(private val client: SupabaseClient) : ViewModel() {

    private val supabase = FlowPathsApplication.supabaseClient
    private val auth = supabase.auth

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState = _sessionState.asStateFlow()

    init {
        observeSession()
    }

    fun observeSession() {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                _sessionState.value = when (status) {
                    is io.github.jan.supabase.gotrue.SessionStatus.Authenticated -> {
                        SessionState.Authenticated(status.session)
                    }
                    is io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated -> {
                        SessionState.Unauthenticated
                    }
                    is io.github.jan.supabase.gotrue.SessionStatus.LoadingFromStorage,
                    is io.github.jan.supabase.gotrue.SessionStatus.NetworkError -> {
                        SessionState.Loading
                    }
                }
            }
        }
    }
}
