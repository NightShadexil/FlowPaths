package com.example.flowpaths.data.states

import com.example.flowpaths.data.models.PercursoRecomendado

sealed class MoodUiState {
    data object Idle : MoodUiState()
    data object Loading : MoodUiState()
    data class Success(val recommendation: PercursoRecomendado) : MoodUiState()
    data class Error(val message: String) : MoodUiState()
}