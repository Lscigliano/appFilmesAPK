pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "appFilmesAPK"
include(":app")

// O OneDrive mantém arquivos abertos enquanto sincroniza, e o build quebra com
// "AccessDeniedException" / "Unable to delete directory" no meio das tarefas de
// merge de recursos. Não é erro de código — já aconteceu três vezes neste
// projeto (ver docs/DIARIO.md).
//
// Quando o projeto está dentro de uma pasta do OneDrive, a saída do build vai
// para fora dela. No CI (Linux, sem OneDrive) nada muda, e o APK continua saindo
// no caminho padrão que o workflow espera.
if (rootDir.path.contains("OneDrive", ignoreCase = true)) {
    val destino = File(System.getProperty("java.io.tmpdir"), "appFilmesAPK-build")
    gradle.beforeProject {
        val nome = project.path.replace(':', '-').trim('-').ifEmpty { "raiz" }
        project.layout.buildDirectory.set(File(destino, nome))
    }
    logger.lifecycle("OneDrive detectado: saída do build em $destino")
}
