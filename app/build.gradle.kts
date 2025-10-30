plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") // Adicionar o plugin de serialização aqui
}

android {
    namespace = "com.example.flowpaths"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.flowpaths"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
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

    // KTOR Client e Kotlinx Serialization
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-android:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Supabase Kotlin Client (Inclui Auth e Postgrest)
    val supabase_version = "1.6.0"
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabase_version")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabase_version")

    // Koin para Injeção de Dependências (Melhora a Arquitetura)
    implementation("io.insert-koin:koin-android:3.4.3")
}