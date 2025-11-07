package com.example.flowpaths

import android.app.Application
import com.example.flowpaths.data.remote.GeminiMoodAnalyzer
import com.example.flowpaths.ui.auth.AuthViewModel
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.MoodViewModel
import com.example.flowpaths.viewmodel.ProfileViewModel
import com.example.flowpaths.viewmodel.SessionViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.functions.Functions
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.github.jan.supabase.annotations.SupabaseInternal

// Imports do Koin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module

import android.content.Context
import com.example.flowpaths.data.location.FlowPathsLocationManager
import org.koin.android.ext.koin.androidApplication

class FlowPathsApplication : Application() {

    companion object {
        lateinit var supabaseClient: SupabaseClient
            private set
    }

    // 💡 MÓDULO DO KOIN (CORRIGIDO: MapViewModel como 'single' para persistência)
    val appModule = module {
        // Serviços e Singletons
        single { supabaseClient }
        single { GeminiMoodAnalyzer() }
        single<Context> { androidApplication() }

        // Location Manager como Singleton (necessário para o MapViewModel)
        single { FlowPathsLocationManager(get()) }

        // ViewModels
        viewModel { AuthViewModel() }
        viewModel { ProfileViewModel() }
        viewModel { MoodViewModel(get()) }
        viewModel { SessionViewModel(get()) }

        // 🚨 CORREÇÃO CRÍTICA: O MapViewModel DEVE ser um 'single' (singleton)
        // se o seu estado (currentRoute) precisar de persistir e ser partilhado
        // entre diferentes ecrãs Compose (MainScreen e MoodAnalysisScreen).
        single { MapViewModel(get()) } // <-- Agora um SINGLETON
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar o Supabase (TEM de vir antes do Koin)
        initializeSupabase()

        // 2. INICIALIZAR O KOIN
        startKoin {
            androidLogger()
            androidContext(this@FlowPathsApplication)
            modules(appModule) // Carrega as definições
        }
    }

    @OptIn(SupabaseInternal::class)
    private fun initializeSupabase() {
        supabaseClient = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)

            httpConfig {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30000
                    connectTimeoutMillis = 30000
                    socketTimeoutMillis = 30000
                }
            }
        }
    }
}