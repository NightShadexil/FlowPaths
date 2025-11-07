// ui/screens/AuthScreen.kt
package com.example.flowpaths.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flowpaths.ui.auth.AuthState
import com.example.flowpaths.ui.auth.AuthViewModel
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.Facebook

// A callback onAuthSuccess é crucial para navegação pós-login
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthSuccess: () -> Unit // Função chamada após login/registo bem-sucedido
) {
    // Observa o estado do ViewModel
    val authState by viewModel.authState.collectAsState()

    // Estados da UI
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(true) } // Iniciar no modo Registo

    // Efeito para Navegação
    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onAuthSuccess() // Chama a função que fará a navegação no NavHost
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

            Text(
                text = if (isRegisterMode) "Criar Conta FlowPaths" else "Bem-vindo de Volta",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // 1. Campo Nome (Apenas para Registo)
            if (isRegisterMode) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            // 2. Campo E-mail
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // 3. Campo Palavra-passe
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Palavra-passe") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                // Lógica para mostrar/esconder password
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(imageVector = image, contentDescription = "Mostrar/Esconder Password")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // 4. Botão Principal de Ação
            val isLoading = authState is AuthState.Loading
            Button(
                onClick = {
                    if (isRegisterMode) {
                        viewModel.registarUtilizador(nome, email, password) // RF1.1
                    } else {
                        viewModel.autenticarUtilizador(email, password) // RF1.2
                    }
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

            Text(
                text = "OU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Botão Google
            OutlinedButton(
                onClick = { viewModel.autenticarComProvedor(Google) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                // Para simplificar, usamos um ícone de cadeado se o ícone do Google não estiver disponível
                Icon(Icons.Filled.Lock, contentDescription = "Google Login", modifier = Modifier.size(20.dp).padding(end = 8.dp))
                Text("Continuar com Google")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botão Facebook
            OutlinedButton(
                onClick = { viewModel.autenticarComProvedor(Facebook) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Facebook Login", modifier = Modifier.size(20.dp).padding(end = 8.dp))
                Text("Continuar com Facebook")
            }

            // ---

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Alternar Modo
            TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                Text(if (isRegisterMode) "Já tenho conta? Entrar" else "Não tem conta? Registar")
            }

            // 6. Feedback de Erro
            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}