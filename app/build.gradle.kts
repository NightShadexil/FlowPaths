import org.gradle.kotlin.dsl.implementation
import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" // NOTA: Considera atualizar isto se o Kotlin/Compose der erros
}

android {
    namespace = "com.example.flowpaths"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.example.flowpaths"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        } else {
            println("AVISO: Ficheiro local.properties não encontrado. As chaves SUPABASE serão vazias.")
        }

        buildConfigField("String", "SUPABASE_URL",
            "\"${properties.getProperty("supabase.url") ?: ""}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY",
            "\"${properties.getProperty("supabase.publishable_key") ?: ""}\"")

        buildConfigField("String", "CLOUD_API_KEY",
            "\"${properties.getProperty("google.cloud.key") ?: ""}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${properties.getProperty("spotify.client.id")}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${properties.getProperty("spotify.client.secret")}\"")

        buildConfigField("String", "OPENWEATHER_API_KEY",
            "\"${properties.getProperty("openweather.api.key") ?: ""}\"")

        manifestPlaceholders["GOOGLE_MAPS_KEY"] = properties.getProperty("google.cloud.key") ?: ""
        manifestPlaceholders["redirectSchemeName"] = "com.example.flowpaths"
        manifestPlaceholders["redirectHostName"] = "callback"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(platform("io.ktor:ktor-bom:2.3.12"))
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-encoding")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") // Ou 1.8.1

    // -----------------------------------------------------------------
    // ‼️ INÍCIO DA CORREÇÃO SUPABASE ‼️
    // -----------------------------------------------------------------

    // Define uma versão única para todas as bibliotecas Supabase
    // (Esta é a versão que aparecia no teu erro, por isso é a mais recente que tens)
    implementation(platform(libs.supabase.bom))

    // ‼️ IMPORTANTE: Importa as bibliotecas com o sufixo '-android'
    // Isto resolve o teu problema de 'handleDeepLink' e 'signInWith(context)'
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // -----------------------------------------------------------------
    // ‼️ FIM DA CORREÇÃO SUPABASE ‼️
    // -----------------------------------------------------------------


    // Dependência Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    implementation("io.insert-koin:koin-androidx-compose:3.4.6")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.maps.android:android-maps-utils:3.9.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation( files("libs/spotify-auth-release-2.1.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
}