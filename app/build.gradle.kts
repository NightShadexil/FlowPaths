import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
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
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${properties.getProperty("google.gemini.key") ?: ""}\"")
        manifestPlaceholders["GOOGLE_MAPS_KEY"] = properties.getProperty("google.maps.key") ?: ""
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

    implementation("io.ktor:ktor-client-okhttp")      // Para o Gemini
    implementation("io.ktor:ktor-client-logging")
    //implementation("io.ktor:ktor-client-cio")    // Para corrigir o crash
    implementation("io.ktor:ktor-serialization-kotlinx-json") // Se ainda for necessário

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)

    // Dependência Gemini/API de IA — ajuste para artefacto válido ou remover até encontrar a versão correta
    // implementation("com.google.ai.client.kotlin:google-ai-client-kotlin:1.1.1")
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0") // Exemplo de versão válida :contentReference[oaicite:1]{index=1}

    implementation("io.insert-koin:koin-androidx-compose:3.4.6")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

//    implementation("io.ktor:ktor-client-core:3.0.0")
//    implementation("io.ktor:ktor-client-okhttp:3.0.0")
//    implementation("io.ktor:ktor-client-logging:3.0.0")
//    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
//    //implementation("io.ktor:ktor-client-timeout:3.0.0") // ✅ nome novo
//    implementation(project(":gemini-ktor3-compat"))
//    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")
}

configurations.all {
    resolutionStrategy {
        force(
            "io.ktor:ktor-client-core:2.3.7",
            "io.ktor:ktor-client-okhttp:2.3.7",
            "io.ktor:ktor-client-logging:2.3.7",
            "io.ktor:ktor-serialization-kotlinx-json:2.3.7"
        )
    }
}