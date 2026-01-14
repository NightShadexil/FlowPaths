package com.example.flowpaths.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.R
import com.example.flowpaths.ui.auth.AuthState
import com.example.flowpaths.ui.auth.AuthViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Facebook
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.OAuthProvider
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    val authState by viewModel.authState.collectAsState()

    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isRegisterMode by remember { mutableStateOf(true) }
    var isPasswordResetMode by remember { mutableStateOf(false) }

    // Estado para saber qual provider foi clicado (Google ou Facebook)
    var selectedProvider by remember { mutableStateOf<OAuthProvider?>(null) }

    val context = LocalContext.current

    fun launchOAuth(provider: OAuthProvider) {
        selectedProvider = provider
        // Muda estado IMEDIATAMENTE para evitar glitch visual
        viewModel.setProcessingOAuth()

        (context as? ComponentActivity)?.lifecycleScope?.launch {
            try {
                FlowPathsApplication.supabaseClient.auth.signInWith(provider, "flowpaths://auth-callback")
            } catch (e: Exception) {
                Log.e("AuthScreen", "Erro ao lançar OAuth: ${e.message}", e)
                viewModel.resetState()
                selectedProvider = null
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // -------------------------------------------------------------------------
            // ESTADO DE PROCESSAMENTO OU SUCESSO (Esconde formulário)
            // -------------------------------------------------------------------------
            if (authState is AuthState.ProcessingOAuth || authState is AuthState.Success) {

                if (authState is AuthState.Success) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF4CAF50) // Verde Sucesso
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Autenticação Confirmada!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A entrar...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    // Ícone dinâmico baseado no provider selecionado
                    val iconRes = if (selectedProvider == Facebook) R.drawable.ic_facebook else R.drawable.ic_google

                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "A verificar autenticação...",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Por favor, verifique o navegador para completar o login.\nNão feche a aplicação.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator()

                if (authState !is AuthState.Success) {
                    Spacer(modifier = Modifier.height(32.dp))
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("Cancelar")
                    }
                }
            }

            // ... (Reset Password mantém-se igual) ...
            else if (isPasswordResetMode) {
                PasswordResetScreen(
                    email = email,
                    onEmailChange = { email = it },
                    onBackToLogin = { isPasswordResetMode = false },
                    onResetPassword = { viewModel.recuperarPassword(it) },
                    authState = authState
                )
            } else {
                // --- FORMULÁRIO ---
                Text(
                    text = if (isRegisterMode) "Criar Conta FlowPaths" else "Bem-vindo de Volta",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome Completo") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Palavra-passe") },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = "Mostrar/Esconder Password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    singleLine = true
                )

                val isLoading = authState is AuthState.Loading

                Button(
                    onClick = {
                        if (isRegisterMode) viewModel.registarUtilizador(nome, email, password)
                        else viewModel.autenticarUtilizador(email, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (isRegisterMode) "REGISTAR CONTA" else "ENTRAR")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(" OU ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // ✅ TEXTOS GENÉRICOS ("Continuar com...")
                OutlinedButton(
                    onClick = { launchOAuth(Google) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 8.dp)
                    )
                    Text("Continuar com Google")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { launchOAuth(Facebook) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_facebook),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 8.dp)
                    )
                    Text("Continuar com Facebook")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { isPasswordResetMode = true }) {
                        Text("Esqueci-me da senha", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                        Text(if (isRegisterMode) "Já tenho conta? Entrar" else "Criar conta", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (authState is AuthState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}