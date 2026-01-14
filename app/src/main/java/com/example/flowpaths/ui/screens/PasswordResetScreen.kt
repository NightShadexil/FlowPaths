package com.example.flowpaths.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flowpaths.ui.auth.AuthState
import com.example.flowpaths.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun PasswordResetScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    onBackToLogin: () -> Unit,
    onResetPassword: (String) -> Unit,
    authState: AuthState
) {
    var emailValue by remember { mutableStateOf(email) }
    val isLoading = authState is AuthState.Loading
    val isSuccess = authState is AuthState.PasswordResetSent
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (!isSuccess) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Recuperar Palavra-passe",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!isSuccess) {
            Text(
                text = "Digite o seu e-mail para receber as instruções de recuperação.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = emailValue,
                onValueChange = { emailValue = it },
                label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            keyboardController?.hide()
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    onResetPassword(emailValue)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && emailValue.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("ENVIAR EMAIL DE RECUPERAÇÃO")
                }
            }
        }

        // ✅ BLOCO DE FEEDBACK ATUALIZADO
        when (authState) {
            is AuthState.Loading -> {
                // O indicador de loading já está no botão, então pode não precisar de mais nada aqui.
            }
            is AuthState.PasswordResetSent -> {
                // Só mostra a mensagem de sucesso (o botão de voltar já está em baixo)
                Text(
                    text = "✅ Email de recuperação enviado!\n\nVerifique a sua caixa de entrada e a pasta de spam. Siga o link no email para redefinir a sua palavra-passe.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            is AuthState.Error -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            else -> { /* Não fazer nada para outros estados */ }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ✅ BOTÃO ÚNICO DE VOLTAR (Serve para ambos os estados)
        TextButton(onClick = onBackToLogin) {
            Text("Voltar ao Login")
        }
    }
}