package com.example.flowpaths.supabase

import android.content.Context
import com.example.flowpaths.FlowPathsApplication
import io.github.jan.supabase.SupabaseClient

/**
 * Um helper para aceder ao SupabaseClient global inicializado em FlowPathsApplication.
 */
object SupabaseClientHolder {

    val client: SupabaseClient
        get() = FlowPathsApplication.supabaseClient
}
