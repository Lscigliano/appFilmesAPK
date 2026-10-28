# O release sai com minify desligado (ver app/build.gradle.kts): o ganho de
# tamanho não compensa o risco de quebrar o libVLC, que carrega classes por JNI.
# As regras abaixo ficam prontas para quando valer a pena ligar o minify.

# libVLC chama estas classes a partir do código nativo (JNI). Ofuscar quebra tudo.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn io.ktor.**
