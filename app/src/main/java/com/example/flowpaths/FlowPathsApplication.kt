package com.example.flowpaths

import android.app.Application
import com.example.flowpaths.di.appModule
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
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.okhttp.OkHttp

// ✅ IMPORTS CORRETOS DO KOIN
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FlowPathsApplication : Application() {

    companion object {
        lateinit var supabaseClient: SupabaseClient
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar Supabase
        initializeSupabase()

        // 2. Inicializar Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FlowPathsApplication)
            modules(appModule)
        }
    }

    @OptIn(SupabaseInternal::class)
    private fun initializeSupabase() {
        supabaseClient = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            // 1. Define explicitamente o serializer para ser "tolerante"
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true // <--- ESTA LINHA É A CHAVE MESTRA
                isLenient = true
                encodeDefaults = true
                prettyPrint = true
                coerceInputValues = true // Ajuda a converter nulos para valores default se necessário
            })

            // 2. Configura o motor Ktor explicitamente (Ktor 3 style)
            httpEngine = OkHttp.create()

            // 3. Configurações de Timeout (para evitar o erro que tinhas antes)
            httpConfig {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 60000 // 60 segundos
                    connectTimeoutMillis = 60000
                    socketTimeoutMillis = 60000
                }
                // Log para veres o erro real se crashar outra vez
                install(io.ktor.client.plugins.logging.Logging) {
                    level = io.ktor.client.plugins.logging.LogLevel.ALL
                }
            }

            install(Auth) {
                // Configurar o esquema de URL para redirecionamento após login
                scheme = "flowpaths" // Deve corresponder ao AndroidManifest.xml
                host = "auth-callback" // Parte final do URL de redirecionamento
            }
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