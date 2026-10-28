// Build raiz. Nenhum plugin é aplicado aqui — só declarados para os módulos.
// NOTA (AGP 9): o plugin `org.jetbrains.kotlin.android` NÃO deve aparecer neste
// projeto. O AGP 9 traz Kotlin embutido e aplicá-lo quebra o build com
// "Cannot add extension with name 'kotlin'". Ver docs/DIARIO.md.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
