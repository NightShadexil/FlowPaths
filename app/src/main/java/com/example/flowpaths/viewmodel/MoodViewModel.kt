package com.example.flowpaths.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowpaths.data.remote.GeminiMoodAnalyzer
import com.example.flowpaths.data.remote.PercursoRecomendado // 💡 IMPORTAR PERCURSO RECOMENDADO (agora no GeminiMoodAnalyzer.kt)
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

// Estados da UI para o MoodAnalysisScreen
sealed class MoodUiState {
    data object Idle : MoodUiState() // Estado inicial
    data object Loading : MoodUiState()
    // O tipo de recomendação muda de String para o objeto estruturado
    data class Success(val recommendation: PercursoRecomendado) : MoodUiState()
    data class Error(val message: String) : MoodUiState()
}

class MoodViewModel(
    private val geminiAnalyzer: GeminiMoodAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow<MoodUiState>(MoodUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun analyzeVibe(moodText: String, moodIcon: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MoodUiState.Loading

            // Validar se algo foi selecionado
            if (moodText.isBlank() && moodIcon.isBlank()) {
                _uiState.value = MoodUiState.Error("Por favor, descreva o seu humor ou selecione um ícone.")
                return@launch
            }

            // O retorno da função getVibeRecommendation agora é Result<PercursoRecomendado>
            val result = geminiAnalyzer.getVibeRecommendation(moodText, moodIcon)

            result.onSuccess { percursoRecomendado ->
                _uiState.value = MoodUiState.Success(percursoRecomendado)
            }.onFailure {
                _uiState.value = MoodUiState.Error("Falha ao contactar a IA: ${it.message}")
            }
        }
    }
}