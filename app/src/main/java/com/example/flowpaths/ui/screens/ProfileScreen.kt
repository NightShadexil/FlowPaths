package com.example.flowpaths.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flowpaths.R // Importar R para o logotipo
import com.example.flowpaths.ui.auth.AuthViewModel
import com.example.flowpaths.ui.auth.AuthState
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.ui.theme.*
import com.example.flowpaths.viewmodel.ProfileUiState
import com.example.flowpaths.viewmodel.ProfileViewModel
import org.koin.androidx.compose.koinViewModel
// Imports do Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = koinViewModel(), // Injetado
    profileViewModel: ProfileViewModel = koinViewModel() // Injetado
) {
    val authState by authViewModel.authState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Launcher para selecionar a imagem da galeria
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileViewModel.uploadAvatar(context, it)
        }
    }

    // Observar o efeito de logout
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedOut) {
            // Navega para auth e limpa toda a pilha
            navController.navigate(Routes.AUTH_SCREEN) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                }
            }
        }
    }

    Scaffold { padding ->
        // Fundo gradiente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(LightGrayBackground, WhiteBackground),
                        startY = 0.0f,
                        endY = 1000.0f
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
                Spacer(modifier = Modifier.height(64.dp))

                // 1. Logotipo
                Image(
                    painter = painterResource(id = R.drawable.flowpaths_logo),
                    contentDescription = "Logotipo FlowPaths",
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 2. Cartão de Perfil
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    when (val state = profileState) {
                        is ProfileUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is ProfileUiState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        is ProfileUiState.Success -> {
                            // --- Conteúdo do Perfil ---
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                // --- Foto de Perfil (com Coil) ---
                                Box(contentAlignment = Alignment.BottomEnd) {

                                    val avatarUrlWithCacheBuster = remember(state.utilizador.avatarUrl) {
                                        "${state.utilizador.avatarUrl}&t=${System.currentTimeMillis()}"
                                    }

                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(avatarUrlWithCacheBuster)
                                            .crossfade(true)
                                            .memoryCachePolicy(CachePolicy.DISABLED) // Desativa o cache em memória
                                            .diskCachePolicy(CachePolicy.DISABLED)  // Desativa o cache em disco
                                            .build(),
                                        placeholder = painterResource(id = R.drawable.ic_launcher_background),
                                        error = painterResource(id = R.drawable.ic_launcher_background),
                                        contentDescription = "Foto de Perfil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                    )

                                    Log.d("ProfileUI", "URL final do avatar: $avatarUrlWithCacheBuster")

                                    Log.d("ProfileUI", "URL final do avatar: ${state.utilizador.avatarUrl}")

                                    // Botão de Editar
                                    IconButton(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Mudar Avatar",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Nome do Utilizador
                                Text(
                                    text = state.utilizador.nome ?: "Utilizador",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Email
                                Text(
                                    text = state.utilizador.email ?: "Email desconhecido",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Botão de Logout
                                OutlinedButton(
                                    onClick = { authViewModel.logout() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Logout",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Terminar Sessão")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}