package com.example.flowpaths.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flowpaths.R
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.LightGrayBackground
import com.example.flowpaths.ui.theme.WhiteBackground
import com.example.flowpaths.viewmodel.MoodUiState
import com.example.flowpaths.viewmodel.MoodViewModel
import com.example.flowpaths.viewmodel.MapViewModel
import org.koin.androidx.compose.koinViewModel // Importar Koin

/**
 * Ecrã para analisar a "vibe" (humor), inspirado na tela_analise_humor.png.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAnalysisScreen(
    navController: NavController,
    viewModel: MoodViewModel = koinViewModel(), // Usar Koin para injetar o ViewModel
    mapViewModel: MapViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var moodText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("") } // Guarda o Emoji/Descrição

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("A sua Vibe Hoje") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        // Fundo gradiente suave
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // 1. Logotipo
                Image(
                    painter = painterResource(id = R.drawable.flowpaths_logo),
                    contentDescription = "Logotipo FlowPaths",
                    modifier = Modifier.height(100.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. Título
                Text(
                    text = "Qual é a sua vibe hoje?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Campo de Texto (como na imagem)
                OutlinedTextField(
                    value = moodText,
                    onValueChange = { moodText = it },
                    label = { Text("Descreva o seu humor...") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { /* TODO: Lógica de microfone */ }) {
                            Icon(Icons.Default.Mic, contentDescription = "Gravar voz")
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Ícones de Humor (como na imagem)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // (As tuas imagens de ícones - aqui como emojis)
                    MoodIcon(
                        label = "Feliz",
                        emoji = "😊",
                        isSelected = selectedMood == "Feliz"
                    ) { selectedMood = "Feliz" }
                    MoodIcon(
                        label = "Sol",
                        emoji = "☀️",
                        isSelected = selectedMood == "Sol"
                    ) { selectedMood = "Sol" }
                    MoodIcon(
                        label = "Nublado",
                        emoji = "☁️",
                        isSelected = selectedMood == "Nublado"
                    ) { selectedMood = "Nublado" }
                    MoodIcon(
                        label = "Energético",
                        emoji = "⚡️",
                        isSelected = selectedMood == "Energético"
                    ) { selectedMood = "Energético" }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 5. Botão de Análise
                Button(
                    onClick = {
                        // Envia o texto e o emoji selecionado para o ViewModel
                        viewModel.analyzeVibe(moodText, selectedMood)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = uiState !is MoodUiState.Loading // Desativa se estiver a carregar
                ) {
                    if (uiState is MoodUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            "Analisar e Recomendar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 6. 💡 Área de Resposta do Gemini (CORRIGIDA)
                when (val state = uiState) {
                    is MoodUiState.Success -> {
                        // 1. Cartão com a recomendação em TEXTO
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                // 🛑 CORREÇÃO: Aceder ao campo de texto 'recomendacao' do objeto.
                                text = state.recommendation.recomendacao,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Botão para usar as coordenadas no Mapa (AÇÃO FUTURA)
                        Button(
                            onClick = {
                                // 💡 1. Guardar a rota no ViewModel partilhado
                                mapViewModel.setRecommendedRoute(state.recommendation)

                                // 💡 2. Navegar de volta para o Dashboard
                                navController.navigate(Routes.PRIVATE_DASHBOARD) {
                                    popUpTo(Routes.MOOD_ANALYSIS) { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            // Exibir o tipo de percurso como título do botão
                            Text("Iniciar Percurso: ${state.recommendation.tipoPercurso}")
                        }
                    }

                    is MoodUiState.Error -> {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {
                        // Não mostra nada se for Idle ou Loading
                    }
                }
            }
        }
    }
}

// 💡💡💡 CÓDIGO EM FALTA (ADICIONADO) 💡💡💡
/**
 * Componente de UI reutilizável para os ícones de humor clicáveis.
 */
@Composable
private fun MoodIcon(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Define a cor de fundo com base na seleção
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    // Define a cor da borda com base na seleção
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.LightGray.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, borderColor, CircleShape)
            .clickable { onClick() }, // Define a ação de clique
        contentAlignment = Alignment.Center
    ) {
        // Usa Text para renderizar o Emoji
        Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
    }
}