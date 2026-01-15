package com.example.flowpaths.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ViewModel que atua como um barramento de eventos para navegação por deep link.
 * A MainActivity emite eventos e o NavHost escuta.
 */
class DeepLinkViewModel : ViewModel() {
    // ✅ SharedFlow para emitir eventos de navegação de forma segura para múltiplos coletores.
    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    /**
     * Função para a MainActivity emitir um evento de navegação.
     */
    fun navigateTo(destination: String) {
        _navigationEvent.tryEmit(destination) // tryEmit é mais seguro
    }
}