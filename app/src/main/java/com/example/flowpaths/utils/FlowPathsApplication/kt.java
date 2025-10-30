// FlowPathsApplication.kt
package com.example.flowpaths.utils.FlowPathsApplication;

import android.app.Application;
import com.example.flowpaths.utils.SupabaseConstants;
import io.github.jan.supabase.SupabaseClient;
import io.github.jan.supabase.createSupabaseClient;
import io.github.jan.supabase.gotrue.Auth;
import io.github.jan.supabase.postgrest.Postgrest;



class FlowPathsApplication : Application() {

    companion object {
        // Cliente acessível globalmente
        lateinit var supabaseClient: SupabaseClient
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializa o cliente Supabase
        supabaseClient = createSupabaseClient(
                supabaseUrl = SupabaseConstants.SUPABASE_URL,
                supabaseKey = SupabaseConstants.SUPABASE_ANON_KEY
        ) {
            // 2. Instala os módulos necessários
            install(Auth) // Módulo de Autenticação
            install(Postgrest) // Módulo para API RESTful (Base de Dados)
        }
    }
}
