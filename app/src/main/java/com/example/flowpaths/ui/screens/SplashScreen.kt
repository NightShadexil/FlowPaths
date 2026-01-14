package com.example.flowpaths.ui.screens

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.ui.navigation.Routes
import com.example.flowpaths.R
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    // Cor de fundo off-white do logotipo (aproximadamente #F8F8F8)
    val offWhiteBackgroundColor = Color(0xFFF8F8F8)
    val logoDarkColor = Color(0xFF2E3D48)
    val sloganColor = Color(0xFF5D707D)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(offWhiteBackgroundColor), // Fundo definido para off-white
        contentAlignment = Alignment.Center
    ) {
        // --- Elementos de aquarela nos cantos ---

        // Top-left splash (azul/ciano)
        Image(
            painter = painterResource(id = R.drawable.watercolor_splash_blue),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .size(200.dp)
        )

        // Top-right splash (verde)
        Image(
            painter = painterResource(id = R.drawable.watercolor_splash_green),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-50).dp)
                .size(200.dp)
        )

        // Bottom-right splash (laranja/pêssego)
        Image(
            painter = painterResource(id = R.drawable.watercolor_splash_orange),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .size(200.dp)
        )

        // Bottom-left splash (ROSA/VERMELHO)
        Image(
            painter = painterResource(id = R.drawable.watercolor_splash_pink),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-50).dp, y = 50.dp)
                .size(200.dp)
        )
        // --- Fim dos elementos de aquarela ---

        // Conteúdo central (logo e texto)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logotipo
            Image(
                painter = painterResource(id = R.drawable.flowpaths_logo),
                contentDescription = "FlowPaths Logo",
                modifier = Modifier.size(200.dp)
            )

//            Text(
//                text = "FlowPaths",
//                fontSize = 48.sp,
//                fontWeight = FontWeight.Bold,
//                color = logoDarkColor,
//                modifier = Modifier.padding(top = 16.dp)
//            )

            // Slogan com os traços (hífens)
            Text(
                text = buildAnnotatedString {
                    append("Onde as suas emoções encontram\n")
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )) {
                        append("–")
                    }
                    append(" o seu caminho. ")
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )) {
                        append("–")
                    }
                },
                fontSize = 20.sp,
                color = sloganColor,
                modifier = Modifier.padding(top = 8.dp),
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )

            //CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp), color = sloganColor)
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        // Correção na chamada do Supabase: usar currentUserOrNull() diretamente
        val currentUser = FlowPathsApplication.supabaseClient.auth.currentUserOrNull()
        if (currentUser != null) {
            navController.navigate(Routes.PRIVATE_DASHBOARD) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        } else {
            navController.navigate(Routes.AUTH_SCREEN) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }
    }
}