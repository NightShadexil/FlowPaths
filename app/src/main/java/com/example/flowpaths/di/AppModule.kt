package com.example.flowpaths.di

import com.example.flowpaths.BuildConfig
import com.example.flowpaths.FlowPathsApplication
import com.example.flowpaths.data.location.FlowPathsLocationManager
import com.example.flowpaths.data.remote.DirectionsService
import com.example.flowpaths.data.remote.GeminiMoodAnalyzer
import com.example.flowpaths.data.remote.GoogleDirectionsService
import com.example.flowpaths.data.remote.SpotifyService
import com.example.flowpaths.data.remote.WeatherService
import com.example.flowpaths.data.repository.RouteRepository
import com.example.flowpaths.ui.auth.AuthViewModel
import com.example.flowpaths.ui.viewmodel.DeepLinkViewModel
import com.example.flowpaths.viewmodel.MapViewModel
import com.example.flowpaths.viewmodel.MoodViewModel
import com.example.flowpaths.viewmodel.ProfileViewModel
import com.example.flowpaths.viewmodel.SessionViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { FlowPathsApplication.supabaseClient }

    single<HttpClient> {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    encodeDefaults = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
    }

    single { FlowPathsLocationManager(context = androidContext()) }

    single<DirectionsService> {
        GoogleDirectionsService(apiKey = BuildConfig.CLOUD_API_KEY, httpClient = get())
    }

    single { WeatherService( get()) }
    single { GeminiMoodAnalyzer() }
    single { RouteRepository(supabaseClient = get()) }
    single { SpotifyService() }

    viewModel { AuthViewModel() }
    viewModel { ProfileViewModel() }
    viewModel { DeepLinkViewModel() }
    viewModel { SessionViewModel() }

    viewModel {
        MoodViewModel(
            geminiAnalyzer = get(),
            directionsService = get(),
            weatherService = get(),
            spotifyService = get(),
            routeRepository = get(),
            locationManager = get()
        )
    }

    // ✅ MapViewModel injetado corretamente com HttpClient
    single {
        MapViewModel(
            application = androidApplication(),
            routeRepository = get(),
            locationManager = get(),
            weatherService = get(),
            httpClient = get()
        )
    }
}