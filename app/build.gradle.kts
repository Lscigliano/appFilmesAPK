import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Assinatura opcional: se existir keystore.properties na raiz (IGNORADO pelo git),
// o release é assinado com a chave de verdade. Se não existir, cai na chave de debug,
// que também gera um APK instalável por sideload. Ver docs/DISTRIBUICAO.md.
val arquivoChave = rootProject.file("keystore.properties")
val temChavePropria = arquivoChave.exists()
val propsChave = Properties().apply { if (temChavePropria) arquivoChave.inputStream().use { load(it) } }

android {
    namespace = "io.zaffa.filmes"

    // Compose 1.11+ EXIGE compileSdk 37 (o build falha dizendo isso em claro).
    // targetSdk fica em 36 de propósito: subir compileSdk só dá acesso às APIs
    // novas; subir targetSdk mudaria o comportamento do app em tempo de execução.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.zaffa.filmes"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    if (temChavePropria) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(propsChave.getProperty("storeFile"))
                storePassword = propsChave.getProperty("storePassword")
                keyAlias = propsChave.getProperty("keyAlias")
                keyPassword = propsChave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (temChavePropria) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // O libVLC traz bibliotecas nativas para 4 arquiteturas; juntas passam de
    // 220 MB — ninguém baixa isso no celular. Separando por arquitetura, cada
    // APK fica em ~60 MB. `arm64-v8a` cobre praticamente todo celular moderno;
    // `armeabi-v7a` cobre aparelhos antigos e TV Box baratas.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Motor de vídeo 1 — ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Login nativo e leitura do HTML do painel
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Capas do catálogo
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Motor de vídeo 2 — libVLC (tolera servidores que ignoram Range)
    implementation(libs.libvlc)


}
