package com.example.flowpaths.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flowpaths.R
import com.example.flowpaths.data.states.MoodUiState
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.LightGrayBackground
import com.example.flowpaths.ui.theme.WhiteBackground
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.MoodViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAnalysisScreen(
    navController: NavController,
    viewModel: MoodViewModel = koinViewModel(),
    mapViewModel: MapViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var moodText by remember { mutableStateOf("") }

    // O estado inicial pode ser vazio ou "Zen" (Neutro)
    var selectedMood by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Definição da nova escala de ícones
    val moodOptions = listOf(
        "Esgotado" to "😫",
        "Indeciso" to "😕",
        "Zen" to "😌",
        "Feliz" to "😃",
        "Radiante" to "🤩"
    )

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(LightGrayBackground, WhiteBackground),
                        startY = 0.0f,
                        endY = 1500.0f
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Image(
                    painter = painterResource(id = R.drawable.flowpaths_logo),
                    contentDescription = "Logotipo FlowPaths",
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(32.dp))

                // -----------------------------------------------------------
                // VERIFICAÇÃO DE ESTADO: SUCESSO vs INPUT
                // -----------------------------------------------------------
                if (uiState is MoodUiState.Success) {
                    // === MODO RESULTADO (Esconde inputs, mostra apenas a rota) ===
                    val recommendation = (uiState as MoodUiState.Success).recommendation

                    Text(
                        text = "A sua Jornada está Pronta!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Removemos o height fixo para o cartão crescer conforme necessário
                            .wrapContentHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Título e Categoria
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✨ ${recommendation.tituloDinamico}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = recommendation.recomendacao,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Exibe o primeiro desafio como "teaser"
                            recommendation.desafios.firstOrNull()?.let { desafio ->
                                Text(
                                    text = "Desafio Sugerido:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${desafio.instrucao} (Modo: ${desafio.tipo})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Botão Principal: Iniciar
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val saveResult = viewModel.routeRepository.savePercurso(recommendation)
                                saveResult.onSuccess { percursoId ->
                                    Log.d("MoodAnalysis", "Percurso ID: $percursoId")
                                    mapViewModel.setRecommendedRoute(recommendation, percursoId.toString())
                                    navController.navigate(Routes.PRIVATE_DASHBOARD) {
                                        popUpTo(Routes.MOOD_ANALYSIS) { inclusive = true }
                                    }
                                }.onFailure { e ->
                                    viewModel.setUiState(MoodUiState.Error("Erro ao guardar rota: ${e.message}"))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Iniciar Jornada Agora")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botão Secundário: Tentar de Novo (Reseta o estado)
                    TextButton(
                        onClick = { viewModel.setUiState(MoodUiState.Idle) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Não é isto? Tentar outra vez")
                    }

                } else {
                    // === MODO INPUT (Formulário Normal) ===

                    Text(
                        text = "Qual é a sua vibe hoje?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = moodText,
                        onValueChange = { moodText = it },
                        label = { Text("Descreva o que sente...") },
                        placeholder = { Text("Ex: Cansado do trabalho, preciso de ar puro.") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        minLines = 2,
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(onClick = { /* TODO: Lógica de microfone */ }) {
                                Icon(Icons.Default.Mic, contentDescription = "Gravar voz")
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Linha de Emojis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        moodOptions.forEach { (label, emoji) ->
                            MoodIcon(
                                label = label,
                                emoji = emoji,
                                isSelected = selectedMood == label,
                                onClick = { selectedMood = label }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.analyzeVibe(moodText, selectedMood)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uiState !is MoodUiState.Loading
                    ) {
                        if (uiState is MoodUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "Gerar Rota Personalizada",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mensagens de Erro
                    if (uiState is MoodUiState.Error) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = (uiState as MoodUiState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MoodIcon(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}