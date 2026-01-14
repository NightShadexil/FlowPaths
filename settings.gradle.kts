pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        flatDir {
            dirs("app/libs")
        }

        // Repositório especial para Gemini
        //maven { setUrl("https://maven.google.com/ai") }

        // Para libs que vêm do GitHub (ex: Supabase, Koin)
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "FlowPaths"
include(":app")
//include(":gemini-ktor3-compat")