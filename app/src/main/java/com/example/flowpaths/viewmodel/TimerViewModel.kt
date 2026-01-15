package com.example.flowpaths.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class TimerViewModel : ViewModel() {

    // segundos RESTANTES (não "elapsed")
    private val _seconds = MutableStateFlow(0)
    val seconds = _seconds.asStateFlow()

    private var durationSeconds: Int = 0
    private var startTimeMs: Long = 0L
    private var job: Job? = null

    /**
     * Inicia (ou reinicia) um countdown preciso.
     * @param durationSeconds duração total em segundos
     */
    fun start(durationSeconds: Int) {
        if (durationSeconds <= 0) {
            reset()
            return
        }

        this.durationSeconds = durationSeconds
        startTimeMs = System.currentTimeMillis()
        _seconds.value = durationSeconds

        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
                val remaining = max(0, this@TimerViewModel.durationSeconds - elapsed)

                _seconds.value = remaining
                if (remaining <= 0) break

                delay(250) // suave e resistente a bloqueios de UI
            }
        }
    }

    /**
     * Para o timer e coloca 0.
     */
    fun reset() {
        job?.cancel()
        job = null
        durationSeconds = 0
        startTimeMs = 0L
        _seconds.value = 0
    }
}
