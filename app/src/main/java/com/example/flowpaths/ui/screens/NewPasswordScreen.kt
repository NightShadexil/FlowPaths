package com.example.flowpaths.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.flowpaths.ui.auth.AuthState
import com.example.flowpaths.ui.auth.AuthViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun NewPasswordScreen(
    viewModel: AuthViewModel = koinViewModel(),
    onPasswordResetSuccess: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ✅ CORREÇÃO VITAL: Limpa qualquer estado de sucesso anterior
    // assim que o ecrã abre.
    LaunchedEffect(Unit) {
        viewModel.resetState()
    }

    val authState by viewModel.authState.collectAsState()
    val isLoading = authState is AuthState.Loading
    val isSuccess = authState is AuthState.Success

    // Lógica de Sucesso e Navegação
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            delay(3000) // Espera 3 segundos
            onPasswordResetSuccess()
        } else if (authState is AuthState.Error) {
            errorMessage = (authState as AuthState.Error).message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSuccess) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Sucesso",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Palavra-passe alterada!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A redirecionar para o login...",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            Text(
                text = "Definir Nova Senha",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Escolha uma senha segura para a sua conta.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; errorMessage = null },
                label = { Text("Nova Palavra-passe") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = !isLoading,
                singleLine = true
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorMessage = null },
                label = { Text("Confirmar Nova Palavra-passe") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                enabled = !isLoading,
                singleLine = true,
                isError = errorMessage != null
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (newPassword.length < 6) {
                        errorMessage = "Mínimo de 6 caracteres."
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        errorMessage = "As senhas não coincidem."
                        return@Button
                    }
                    viewModel.updateNewPassword(newPassword)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Redefinir Palavra-passe")
                }
            }
        }
    }
}